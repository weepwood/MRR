<script setup lang="ts">
/* eslint-disable vue/first-attribute-linebreak, vue/html-closing-bracket-newline, vue/html-indent */
import type { StatisticsRecord, StatisticsSummary, TypeStatistics } from '@/api/types'
import type { MetricTone } from '@/components/MrrMetricCard/types'
import type { MrrTableAction } from '@/components/MrrTableActions/types'
import { ElMessage } from 'element-plus'
import { LayoutDashboard as DataBoard, Download, RefreshCw as Refresh } from 'lucide-vue-next'

import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { exportStatisticsCsv, getStatisticsList, getStatisticsSummary } from '@/api/modules/statistics'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import MrrFilterActionButton from '@/components/MrrFilterActionButton/index.vue'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import MrrTableActions from '@/components/MrrTableActions/index.vue'
import { useTableActionLayout } from '@/composables/useTableActionLayout'
import { rangePickerPopperOptions } from '@/ui/provider/picker-options'
import { copyText as copyToClipboard } from '@/utils/copy-text'
import { downloadBlob } from '@/utils/file-download'
import { formatMedicalRecordCode } from '@/utils/medical-record-code'
import { getRequestErrorMessage } from '@/utils/request-error-message'
import { ARCHIVE_DEFAULT_PAGE_SIZE, getArchivePageSize } from './archive-layout'

defineOptions({ name: 'StatisticsDetailPage' })

interface ArchiveItem extends StatisticsRecord {
  patientName?: string
  inpatientDepartment?: string
  patientId?: string
  dischargeDate?: string
}

type ArchiveDisplayMode = 'folder' | 'list'

interface ListData {
  total: number
  size: number
  totalPages: number
  page: number
  list: ArchiveItem[]
}

interface DetailQueryParams {
  page: number
  size: number
  sortBy: string
  sortOrder: string
  keyword?: string
  bah?: string
  sjh?: string
  type?: string
  startDate?: string
  endDate?: string
}

const router = useRouter()
const loading = ref(false)
const error = ref('')
const summaryData = ref<StatisticsSummary>({ byType: [], total: {} })
const listData = ref<ListData>({
  total: 0,
  size: ARCHIVE_DEFAULT_PAGE_SIZE,
  totalPages: 0,
  page: 1,
  list: [],
})

const currentPage = ref(1)
const pageSize = ref(ARCHIVE_DEFAULT_PAGE_SIZE)
const archiveShelfRef = ref<HTMLElement | null>(null)
const archiveDisplayMode = ref<ArchiveDisplayMode>('folder')
const selectedArchive = ref<ArchiveItem | null>(null)
const selectedArchiveKey = ref('')

const archiveActions: MrrTableAction[] = [{
  key: 'view',
  label: '查看影像',
  icon: 'i-lucide:eye',
  tone: 'primary',
  placement: 'inline',
}]
const {
  maxInlineActions: archiveMaxInlineActions,
  actionColumnWidth: archiveActionColumnWidth,
} = useTableActionLayout(archiveActions.length, 1)

let resizeObserver: ResizeObserver | null = null
let archiveListRequestId = 0
let isPageActive = false

const filters = reactive({
  keyword: '',
  bah: '',
  sjh: '',
  type: '',
  dateRange: [] as string[],
})

const sortKey = ref('date-desc')
const sortOptions = [
  { key: 'date-desc', label: '按归档日期倒序', prop: 'date', order: 'desc' },
  { key: 'date-asc', label: '按归档日期升序', prop: 'date', order: 'asc' },
  { key: 'bah-asc', label: '按病案号升序', prop: 'bah', order: 'asc' },
  { key: 'pages-desc', label: '按页数倒序', prop: 'pages', order: 'desc' },
]

const typeOptions = computed(() => {
  const source: TypeStatistics[] = summaryData.value?.byType ?? []
  return source
    .map(item => String(item?.type ?? '').trim())
    .filter(item => item && item.toUpperCase() !== 'NULL')
})

const currentSort = computed(() => sortOptions.find(item => item.key === sortKey.value) || sortOptions[0])

interface SummaryCard {
  label: string
  value: string
  note: string
  tone: MetricTone
  icon: string
}

/* 指标卡只展示文本，数字在这里一次性格式化为千分位，避免模板里重复判断类型。 */
function formatSummaryValue(value: unknown) {
  return Number.isFinite(Number(value)) ? Number(value).toLocaleString('zh-CN') : String(value ?? '-')
}

const summaryCards = computed<SummaryCard[]>(() => [
  { label: '档案袋总数', value: formatSummaryValue(listData.value.total || 0), note: '符合当前筛选条件的统计记录', tone: 'blue', icon: 'i-lucide:folder-open' },
  { label: '病案数量', value: formatSummaryValue(summaryData.value?.uniqueBAHCount ?? 0), note: '系统内已归档病案号数量', tone: 'green', icon: 'i-lucide:contact-round' },
  { label: '总页数', value: formatSummaryValue(summaryData.value?.total?.totalPages ?? 0), note: '统计表累计扫描页数', tone: 'violet', icon: 'i-lucide:file-text' },
  { label: '当前患者', value: selectedArchive.value?.patientName ? normalizeText(selectedArchive.value.patientName) : '未选择', note: '当前选中档案袋对应患者', tone: 'amber', icon: 'i-lucide:user-round' },
])

function normalizeText(value: unknown) {
  const text = String(value ?? '').trim()
  return text && text.toUpperCase() !== 'NULL' ? text : '-'
}

function padTo8Digits(value: unknown) {
  return formatMedicalRecordCode(value)
}

function formatDate(value: unknown) {
  const text = normalizeText(value)
  if (text === '-') {
    return '-'
  }
  return text.replace(/\//g, '-').split(/[ T]/)[0]
}

async function copyCode(text: string, label: string, originalText?: string) {
  await copyToClipboard(text, { success: `${label} ${originalText || text} 已复制` })
}

function archiveKey(item: ArchiveItem, index = 0) {
  return [
    item.bah,
    item.patientId,
    item.cid,
    item.date,
    item.type,
    item.pages,
    item.openerNo,
    item.sjh,
    index,
  ].join('|')
}

function tableIndex(index: number) {
  return (currentPage.value - 1) * pageSize.value + index + 1
}

function typeTone(type: string | undefined) {
  const value = String(type || '')
  if (value.includes('首页')) {
    return 'success'
  }
  if (value.includes('手术')) {
    return 'warning'
  }
  if (value.includes('护理')) {
    return 'primary'
  }
  return 'info'
}

async function loadSummary() {
  try {
    const res = await getStatisticsSummary()
    summaryData.value = res.data ?? { byType: [], total: {} }
  }
  catch (err) {
    console.error('加载统计摘要失败:', err)
  }
}

function buildQueryParams(): DetailQueryParams {
  const params: DetailQueryParams = {
    page: currentPage.value,
    size: pageSize.value,
    sortBy: currentSort.value.prop,
    sortOrder: currentSort.value.order,
  }
  if (filters.keyword.trim()) {
    params.keyword = filters.keyword.trim()
  }
  if (filters.bah.trim()) {
    params.bah = filters.bah.trim()
  }
  if (filters.sjh.trim()) {
    params.sjh = filters.sjh.trim()
  }
  if (filters.type) {
    params.type = filters.type
  }
  if (filters.dateRange.length === 2) {
    params.startDate = filters.dateRange[0]
    params.endDate = filters.dateRange[1]
  }
  return params
}

async function loadArchiveList() {
  const requestId = ++archiveListRequestId
  loading.value = true
  error.value = ''
  try {
    const res = await getStatisticsList(buildQueryParams())
    if (requestId !== archiveListRequestId) {
      return
    }
    const payload = res.data ?? {
      list: [],
      total: 0,
      size: pageSize.value,
      totalPages: 0,
      page: currentPage.value,
    }
    const list = Array.isArray(payload.list) ? payload.list.filter(Boolean) as ArchiveItem[] : []
    listData.value = {
      total: Number(payload.total || 0),
      size: Number(payload.size || pageSize.value),
      totalPages: Number(payload.totalPages || 0),
      page: Number(payload.page || currentPage.value),
      list,
    }
    selectedArchive.value = list[0] || null
    selectedArchiveKey.value = selectedArchive.value ? archiveKey(selectedArchive.value, 0) : ''
  }
  catch (err: unknown) {
    if (requestId !== archiveListRequestId) {
      return
    }
    const msg = err instanceof Error ? err.message : '病案明细加载失败'
    error.value = msg
    ElMessage.error(error.value)
    listData.value = { total: 0, size: pageSize.value, totalPages: 0, page: 1, list: [] }
  }
  finally {
    if (requestId === archiveListRequestId) {
      loading.value = false
    }
  }
}

async function refreshAll() {
  await Promise.all([loadSummary(), loadArchiveList()])
}

function handleSearch() {
  currentPage.value = 1
  loadArchiveList()
}

function resetSearch() {
  filters.keyword = ''
  filters.bah = ''
  filters.sjh = ''
  filters.type = ''
  filters.dateRange = []
  sortKey.value = 'date-desc'
  currentPage.value = 1
  loadArchiveList()
}

function updateArchivePageSize(containerWidth: number, reload = true) {
  const nextPageSize = getArchivePageSize(containerWidth)
  if (nextPageSize === pageSize.value) {
    return
  }

  pageSize.value = nextPageSize
  currentPage.value = 1
  if (reload) {
    loadArchiveList()
  }
}

function handleArchiveShelfResize() {
  updateArchivePageSize(archiveShelfRef.value?.clientWidth ?? 0)
}

function startArchiveResizeObserver() {
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(handleArchiveShelfResize)
    if (archiveShelfRef.value) {
      resizeObserver.observe(archiveShelfRef.value)
    }
  }
  else {
    window.addEventListener('resize', handleArchiveShelfResize)
  }

  handleArchiveShelfResize()
}

function selectArchive(item: ArchiveItem, index = 0) {
  selectedArchive.value = item
  selectedArchiveKey.value = archiveKey(item, index)
}

function selectArchiveFromList(item: ArchiveItem) {
  selectArchive(item, listData.value.list.indexOf(item))
}

function archiveRowClassName({ row, rowIndex }: { row: ArchiveItem, rowIndex: number }) {
  return selectedArchiveKey.value === archiveKey(row, rowIndex) ? 'archive-list-row-selected' : ''
}

function openArchive(item = selectedArchive.value) {
  if (!item?.bah) {
    ElMessage.warning('当前档案袋缺少病案号，无法打开影像')
    return
  }
  router.push({
    path: '/archive/embed',
    query: {
      from: 'statistics-detail',
      bah: item.bah,
      cid: item.cid || '',
      type: item.type || '',
      date: item.date || '',
      pages: String(item.pages ?? ''),
      openerNo: item.openerNo || '',
      sjh: item.sjh || '',
    },
  })
}

function handleArchiveAction(action: string, item: ArchiveItem) {
  if (action === 'view') {
    openArchive(item)
  }
}

function goBackToStatistics() {
  router.push('/statistics')
}

async function handleExportCsv() {
  const params: Record<string, string> = {}
  if (filters.keyword.trim()) {
    params.keyword = filters.keyword.trim()
  }
  if (filters.bah.trim()) {
    params.bah = filters.bah.trim()
  }
  if (filters.sjh.trim()) {
    params.sjh = filters.sjh.trim()
  }
  if (filters.type) {
    params.type = filters.type
  }
  if (filters.dateRange.length === 2) {
    params.startDate = filters.dateRange[0]
    params.endDate = filters.dateRange[1]
  }
  try {
    const response = await exportStatisticsCsv(params)
    const blob = response instanceof Blob ? response : response.data
    if (!(blob instanceof Blob)) {
      throw new TypeError('导出接口未返回有效文件')
    }
    downloadBlob(blob, `statistics-${Date.now()}.csv`)
    ElMessage.success('导出成功')
  }
  catch (error: unknown) {
    ElMessage.error(`导出失败：${getRequestErrorMessage(error)}`)
  }
}

onMounted(async () => {
  isPageActive = true
  updateArchivePageSize(archiveShelfRef.value?.clientWidth ?? 0, false)
  await refreshAll()
  if (isPageActive) {
    startArchiveResizeObserver()
  }
})

onBeforeUnmount(() => {
  isPageActive = false
  archiveListRequestId += 1
  resizeObserver?.disconnect()
  resizeObserver = null
  window.removeEventListener('resize', handleArchiveShelfResize)
})
</script>

<template>
  <MrrPageShell class="statistics-detail-page">
    <MrrPageHeader
      eyebrow="Archive Detail"
      title="病案明细档案袋"
      description="统一查看患者、住院、归档与扫描信息，支持筛选、排序、影像查看和整袋下载。"
    >
      <template #actions>
        <el-button :icon="Download" @click="handleExportCsv">
          导出 CSV
        </el-button>
        <el-button :icon="Refresh" :loading="loading" @click="refreshAll">
          刷新
        </el-button>
        <el-button :icon="DataBoard" @click="goBackToStatistics">
          返回统计
        </el-button>
      </template>
    </MrrPageHeader>

    <section class="mrr-metric-grid">
      <MrrMetricCard v-for="item in summaryCards" :key="item.label" v-bind="item" />
    </section>

    <MrrSectionCard title="档案筛选" description="可按患者姓名、病人 ID、住院科室、设备、负责人或日期定位档案袋">
      <template #actions>
        <el-tag type="info">
          {{ listData.total || 0 }} 条
        </el-tag>
      </template>

      <div class="filter-grid">
        <el-input v-model="filters.bah" class="filter-bah" clearable aria-label="病案号" placeholder="病案号" @keyup.enter="handleSearch" />
        <el-input v-model="filters.sjh" class="filter-sjh" clearable aria-label="上架号" placeholder="上架号" @keyup.enter="handleSearch" />
        <el-input v-model="filters.keyword" class="filter-keyword" clearable aria-label="综合筛选" placeholder="患者姓名、病人 ID、科室、设备或负责人"
          @keyup.enter="handleSearch" />
        <el-select v-model="filters.type" class="filter-type" clearable aria-label="档案类型" placeholder="全部类型" @change="handleSearch">
          <el-option v-for="item in typeOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-date-picker v-model="filters.dateRange" class="filter-date" type="daterange" :popper-options="rangePickerPopperOptions" range-separator="至"
          start-placeholder="归档开始日期" end-placeholder="归档结束日期" value-format="YYYY-MM-DD" />
        <el-select v-model="sortKey" class="filter-sort" aria-label="排序方式" @change="handleSearch">
          <el-option v-for="item in sortOptions" :key="item.key" :label="item.label" :value="item.key" />
        </el-select>
        <div class="filter-actions">
          <MrrFilterActionButton action="search" :pending="loading" :busy="loading" @click="handleSearch">
            查询
          </MrrFilterActionButton>
          <MrrFilterActionButton action="reset" :pending="loading" :busy="loading" @click="resetSearch">
            重置
          </MrrFilterActionButton>
        </div>
      </div>
    </MrrSectionCard>

    <section class="content-layout">
      <div ref="archiveShelfRef" class="archive-shelf">
        <div class="archive-toolbar">
          <h2 class="archive-toolbar-title">
档案列表
</h2>
          <el-radio-group v-model="archiveDisplayMode" aria-label="档案展示方式">
            <el-radio-button value="folder">
              档案袋
            </el-radio-button>
            <el-radio-button value="list">
              列表
            </el-radio-button>
          </el-radio-group>
        </div>

        <AppLoading v-if="loading" type="table" :rows="8" />
        <AppError v-else-if="error" :message="error" @retry="loadArchiveList" />
        <AppEmpty v-else-if="!listData.list.length" description="暂无统计明细" />
        <div v-else-if="archiveDisplayMode === 'folder'" class="archive-grid">
          <article v-for="(item, index) in listData.list" :key="archiveKey(item, index)" class="archive-folder-card"
            :class="{ 'is-selected': selectedArchiveKey === archiveKey(item, index) }"
            :data-department-name="normalizeText(item.inpatientDepartment)">
            <div class="folder-layer folder-layer-back" />
            <div class="folder-layer folder-layer-middle" />
            <div class="folder-card-body">
              <header class="folder-top">
                <span class="folder-index">NO. A{{ tableIndex(index) }}</span>
                <span v-if="selectedArchiveKey === archiveKey(item, index)" class="folder-selected-label">
                  <i />已选中
                </span>
                <el-tag class="folder-type" size="small" :type="typeTone(item.type)" effect="plain">
                  {{ normalizeText(item.type) }}
                </el-tag>
              </header>

              <!--
                选择档案用一个真实按钮承担，按钮的 ::after 铺满卡片，整卡仍可点击。
                原先整张卡是 role="button"，里面又有「查看影像」与复制两类按钮，交互元素互相嵌套；
                现在复制、查看影像与选择是并列的三个按钮，键盘依次可达，读屏各自读出名称。
              -->
              <h3 class="folder-name">
                <button type="button" class="folder-select" :aria-pressed="selectedArchiveKey === archiveKey(item, index)"
                  :aria-label="`选择患者 ${normalizeText(item.patientName)}，病案号 ${padTo8Digits(item.bah)}，上架号 ${padTo8Digits(item.sjh)}，${Number(item.pages || 0)} 页`"
                  @click="selectArchive(item, index)">
                  {{ normalizeText(item.patientName) }}
                </button>
              </h3>

              <p class="folder-meta">
                <span class="folder-department">{{ normalizeText(item.inpatientDepartment) }}</span>
                <span class="folder-meta-dot" aria-hidden="true" />
                <span>出院 <time class="folder-date">{{ formatDate(item.dischargeDate) }}</time></span>
              </p>

              <dl class="folder-codes">
                <div class="folder-code">
                  <dt>病案号</dt>
                  <dd>
                    <span class="folder-code-value">{{ padTo8Digits(item.bah) }}</span>
                    <button type="button" class="folder-copy" :aria-label="`复制病案号 ${padTo8Digits(item.bah)}`" title="复制病案号"
                      @click.stop="copyCode(padTo8Digits(item.bah), '病案号', item.bah)">
                      <FaIcon name="i-lucide:copy" />
                    </button>
                  </dd>
                </div>
                <div class="folder-code">
                  <dt>上架号</dt>
                  <dd>
                    <span class="folder-code-value">{{ padTo8Digits(item.sjh) }}</span>
                    <button type="button" class="folder-copy" :aria-label="`复制上架号 ${padTo8Digits(item.sjh)}`" title="复制上架号"
                      @click.stop="copyCode(padTo8Digits(item.sjh), '上架号', item.sjh)">
                      <FaIcon name="i-lucide:copy" />
                    </button>
                  </dd>
                </div>
              </dl>

              <footer class="folder-footer">
                <p class="folder-page-count">
                  <strong>{{ Number(item.pages || 0).toLocaleString('zh-CN') }}</strong>
                  <span>页扫描影像</span>
                </p>
                <el-button class="folder-action" text type="primary" @click.stop="openArchive(item)">
                  查看影像
                  <span aria-hidden="true">→</span>
                </el-button>
              </footer>
            </div>
          </article>
        </div>
        <div v-else class="archive-list-wrap">
          <el-table :data="listData.list" :row-class-name="archiveRowClassName" class="archive-list" row-key="id"
            @row-click="selectArchiveFromList">
            <el-table-column label="#" width="72" align="center">
              <template #default="{ $index }">
                {{ tableIndex($index) }}
              </template>
            </el-table-column>
            <el-table-column label="患者" min-width="150">
              <template #default="{ row }">
                <div class="archive-patient-cell">
                  <strong>{{ normalizeText(row.patientName) }}</strong>
                  <span>ID {{ normalizeText(row.patientId) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="病案号" min-width="120">
              <template #default="{ row }">
                <strong class="archive-bah">{{ padTo8Digits(row.bah) }}</strong>
              </template>
            </el-table-column>
            <el-table-column label="住院科室" min-width="110">
              <template #default="{ row }">
                {{ normalizeText(row.inpatientDepartment) }}
              </template>
            </el-table-column>
            <el-table-column label="出院日期" min-width="110">
              <template #default="{ row }">
                {{ formatDate(row.dischargeDate) }}
              </template>
            </el-table-column>
            <el-table-column label="档案类型" min-width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="typeTone(row.type)">
                  {{ normalizeText(row.type) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="归档日期" min-width="110">
              <template #default="{ row }">
                {{ formatDate(row.date) }}
              </template>
            </el-table-column>
            <el-table-column label="扫描设备" min-width="100">
              <template #default="{ row }">
                {{ normalizeText(row.cid) }}
              </template>
            </el-table-column>
            <el-table-column label="上架号" min-width="110">
              <template #default="{ row }">
                {{ padTo8Digits(row.sjh) }}
              </template>
            </el-table-column>
            <el-table-column label="页数" width="84" align="right">
              <template #default="{ row }">
                {{ Number(row.pages || 0).toLocaleString('zh-CN') }} 页
              </template>
            </el-table-column>
            <el-table-column label="负责人" min-width="100">
              <template #default="{ row }">
                {{ normalizeText(row.openerNo) }}
              </template>
            </el-table-column>
            <el-table-column
              label="操作"
              :width="archiveActionColumnWidth"
              fixed="right"
              align="center"
            >
              <template #default="{ row }">
                <MrrTableActions
                  :actions="archiveActions"
                  :max-inline="archiveMaxInlineActions"
                  @select="handleArchiveAction($event, row)"
                />
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="pagination-wrapper">
          <el-pagination v-model:current-page="currentPage" :page-size="pageSize" :total="listData.total"
            layout="total, prev, pager, next, jumper" @current-change="loadArchiveList" />
        </div>
      </div>
    </section>
  </MrrPageShell>
</template>

<style scoped>
.filter-grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-3);
  align-items: center;
}

.filter-bah,
.filter-sjh {
  flex: 0 0 140px;
}

.filter-keyword {
  flex: 1 1 300px;
  min-width: 240px;
}

.filter-type,
.filter-sort {
  flex: 0 0 190px;
}

.filter-date {
  flex: 1 1 290px;
  min-width: 260px;
}

.filter-actions {
  display: flex;
  flex: 0 0 auto;
  gap: var(--mrr-space-2);
  margin-left: auto;
}

.content-layout,
.archive-shelf {
  display: grid;
  gap: var(--mrr-space-4);
}

.archive-toolbar {
  display: flex;
  gap: var(--mrr-space-3);
  align-items: center;
  justify-content: space-between;
}

/* 与分区卡标题同档（14px / 650）：它是「档案筛选」卡下方的同级分区，原 16px / 700 比卡标题还重。 */
.archive-toolbar-title {
  margin: 0;
  font-size: var(--mrr-font-size-md);
  font-weight: 650;
  color: var(--text-primary);
}

.archive-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: var(--mrr-space-4);
}

.archive-list-wrap {
  overflow-x: auto;
  border: 1px solid var(--divider);
  border-radius: var(--mrr-radius-md);
}

.archive-list {
  min-width: 1320px;
}

.archive-bah {
  color: var(--text-primary);
}

.archive-patient-cell {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.archive-patient-cell strong,
.archive-patient-cell span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.archive-patient-cell strong {
  color: var(--text-primary);
}

.archive-patient-cell span {
  font-size: var(--mrr-font-size-xs);
  color: var(--text-secondary);
}

:deep(.el-table__row) {
  cursor: pointer;
}

:deep(.el-table__row.archive-list-row-selected > td.el-table__cell) {
  background: hsl(var(--primary) / 8%);
}

.archive-folder-card {
  --folder-accent: var(--color-primary);
  --folder-tint: var(--mrr-tone-primary-soft);

  position: relative;
  padding: var(--mrr-space-3) var(--mrr-space-2) var(--mrr-space-2);
  isolation: isolate;
}

/*
 * 叠层纸是档案袋隐喻（DESIGN.md §2.9 保留项），只留静态错位：悬停时只有卡面抬起，
 * 背后两张纸不再各自旋转位移，避免一屏 18 张卡同时“翻动”。
 */
.folder-layer {
  position: absolute;
  pointer-events: none;
  border: 1px solid color-mix(in srgb, var(--folder-accent) 18%, var(--divider));
  border-radius: var(--mrr-radius-xl);
}

.folder-layer-back {
  /* Lightning CSS can lower literal inset values for Chrome 86. */
  inset: 4px 24px 16px 20px;
  z-index: -2;
  background: color-mix(in srgb, var(--folder-tint) 60%, var(--surface));
  transform: rotate(-1.2deg);
}

.folder-layer-middle {
  inset: 8px 12px 12px;
  z-index: -1;
  background: color-mix(in srgb, var(--folder-tint) 36%, var(--surface));
  transform: rotate(0.7deg);
}

.folder-card-body {
  position: relative;
  display: grid;
  gap: var(--mrr-space-3);
  padding: var(--mrr-space-5) var(--mrr-card-inset) var(--mrr-space-4);
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: var(--mrr-radius-xl);
  box-shadow: var(--mrr-shadow-xs);
  transition: transform var(--mrr-motion-move) var(--mrr-ease-out), border-color var(--mrr-motion-feedback) var(--mrr-ease-out), box-shadow var(--mrr-motion-feedback) var(--mrr-ease-out);
}

/* 科室色条：静态身份信号，同一科室同一颜色；颜色由科室档案袋配色设置决定。 */
.folder-card-body::before {
  position: absolute;
  inset: 0 0 auto;
  height: 3px;
  content: "";
  background: var(--folder-strip, var(--folder-accent));
}

.archive-folder-card:hover .folder-card-body {
  border-color: color-mix(in srgb, var(--folder-accent) 40%, var(--divider));
  box-shadow: var(--mrr-shadow-sm);
  transform: var(--mrr-motion-decor, translateY(-2px));
}

/* 选中：主色描边 + 3px 外环（§5.2，外环只属于选中态）。 */
.archive-folder-card.is-selected .folder-card-body {
  border-color: var(--folder-accent);
  box-shadow: var(--mrr-shadow-sm), 0 0 0 3px color-mix(in srgb, var(--folder-accent) 18%, transparent);
}

.folder-top,
.folder-footer {
  display: flex;
  gap: var(--mrr-space-2);
  align-items: center;
}

.folder-index {
  font-family: var(--mrr-font-mono);
  font-size: var(--mrr-font-size-xs);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--text-secondary);
  letter-spacing: 0.04em;
}

.folder-selected-label {
  display: inline-flex;
  gap: var(--mrr-space-1);
  align-items: center;
  padding: 2px var(--mrr-space-2);
  font-size: var(--mrr-font-size-xs);
  font-weight: 600;
  color: var(--folder-accent);
  background: color-mix(in srgb, var(--folder-accent) 10%, transparent);
  border-radius: var(--mrr-radius-pill);
}

.folder-selected-label i {
  width: 6px;
  height: 6px;
  background: var(--folder-accent);
  border-radius: 50%;
}

.folder-type {
  position: relative;
  z-index: 1;
  margin-left: auto;
}

.folder-name {
  min-width: 0;
  margin: 0;
  font-size: var(--mrr-font-size-lg);
  font-weight: 650;
  line-height: 1.35;
}

/* 选择按钮外观与标题一致；::after 铺满卡面，承担整卡点击。 */
.folder-select {
  display: block;
  max-width: 100%;
  padding: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font: inherit;
  color: var(--text-primary);
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
  outline: none;
  background: none;
  border: 0;
}

.folder-select::after {
  position: absolute;
  inset: 0;
  content: "";
  border-radius: var(--mrr-radius-xl);
}

/* 卡面 overflow: hidden，焦点轮廓画在内侧，避免被裁切。 */
.folder-select:focus-visible::after {
  outline: 2px solid var(--mrr-ring);
  outline-offset: -2px;
}

.folder-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-1) var(--mrr-space-2);
  align-items: center;
  min-width: 0;
  margin: 0;
  font-size: var(--mrr-font-size-sm);
  color: var(--text-secondary);
}

.folder-department {
  font-weight: 600;
  color: var(--text-primary);
}

.folder-meta-dot {
  width: 3px;
  height: 3px;
  background: currentcolor;
  border-radius: 50%;
  opacity: 0.6;
}

.folder-date {
  font-variant-numeric: tabular-nums;
}

.folder-codes {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--mrr-space-3);
  padding: var(--mrr-space-3) 0;
  margin: 0;
  border-block: 1px solid var(--divider);
}

.folder-code {
  display: grid;
  gap: var(--mrr-space-1);
  min-width: 0;
}

.folder-code dt {
  font-size: var(--mrr-font-size-xs);
  color: var(--text-secondary);
}

.folder-code dd {
  display: flex;
  gap: var(--mrr-space-1);
  align-items: center;
  min-width: 0;
  margin: 0;
}

.folder-code-value {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: var(--mrr-font-mono);
  font-size: var(--mrr-font-size-md);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--text-primary);
  white-space: nowrap;
}

.folder-copy {
  position: relative;
  z-index: 1;
  display: inline-grid;
  flex: none;
  place-items: center;
  width: 24px;
  height: 24px;
  padding: 0;
  font-size: var(--mrr-font-size-sm);
  color: var(--text-secondary);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--mrr-radius-sm);
  transition: color var(--mrr-motion-feedback) var(--mrr-ease-out), background-color var(--mrr-motion-feedback) var(--mrr-ease-out);
}

.folder-copy:hover {
  color: var(--folder-accent);
  background: var(--mrr-muted);
}

.folder-copy:focus-visible {
  outline: 2px solid var(--mrr-ring);
  outline-offset: 1px;
}

.folder-page-count {
  display: flex;
  gap: var(--mrr-space-1);
  align-items: baseline;
  margin: 0;
}

.folder-page-count strong {
  font-family: var(--mrr-font-mono);
  font-size: var(--mrr-font-size-lg);
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  color: var(--text-primary);
}

.folder-page-count span {
  font-size: var(--mrr-font-size-xs);
  color: var(--text-secondary);
}

.folder-action {
  position: relative;
  z-index: 1;
  margin-left: auto;
  font-weight: 600;
}

.folder-action span {
  margin-left: var(--mrr-space-1);
  transition: transform var(--mrr-motion-move) var(--mrr-ease-out);
}

.archive-folder-card:hover .folder-action span {
  transform: var(--mrr-motion-decor, translateX(3px));
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 720px) {
  .filter-bah,
  .filter-sjh,
  .filter-keyword,
  .filter-type,
  .filter-date,
  .filter-sort,
  .filter-actions {
    flex: 1 1 100%;
    min-width: 0;
    margin-left: 0;
  }

  .archive-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 480px) {
  .archive-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .folder-codes {
    grid-template-columns: 1fr;
  }
}

/*
 * Chrome 86 回退：档案袋是本站唯一用「表面」承载业务隐喻的地方，不能接受降级。
 *
 * 这里没有一处是「先静态值再 color-mix」的双声明——含 var() 的 color-mix 在 86 上
 * 通过解析、赢得级联，再到计算值阶段把属性打成 unset，前一条静态声明不会回来。
 * 因此回退只能写成独立的 @supports not 块：现代浏览器完全不读这里，86 读到的就是
 * 下面这些实色，三张纸重新分成「主题浅底 / 中性灰底 / 卡面白底」三个可辨层级。
 */
@supports not (color: color-mix(in srgb, #000 50%, #fff)) {
  .folder-layer {
    border: 1px solid var(--divider);
  }

  .folder-layer-back {
    background: var(--folder-tint);
  }

  .folder-layer-middle {
    background: var(--mrr-muted);
  }

  .folder-card-body {
    background: var(--mrr-card);
  }

  .archive-folder-card:hover .folder-card-body {
    border-color: var(--folder-accent);
  }

  /* 选中态原本靠 3px 半透明环加重；86 上换成 outline，1px 边色差不再独自承担选中识别。 */
  .archive-folder-card.is-selected .folder-card-body {
    outline: 2px solid var(--folder-accent);
    outline-offset: 3px;
    box-shadow: var(--mrr-shadow-sm);
  }

  .folder-selected-label {
    background: var(--folder-tint);
  }
}
</style>
