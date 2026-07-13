<script setup lang="ts">
import type { StatisticsRecord, StatisticsSummary, TypeStatistics } from '@/api/types'
import { DataBoard, Download, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { exportStatisticsCsv, getStatisticsList, getStatisticsSummary } from '@/api/modules/statistics'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
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

const summaryCards = computed(() => [
  { label: '档案袋总数', value: listData.value.total || 0, note: '符合当前筛选条件的统计记录', tone: 'blue', icon: 'i-ant-design:folder-open-twotone' },
  { label: '病案数量', value: summaryData.value?.uniqueBAHCount ?? 0, note: '系统内已归档病案号数量', tone: 'green', icon: 'i-ant-design:profile-twotone' },
  { label: '总页数', value: summaryData.value?.total?.totalPages ?? 0, note: '统计表累计扫描页数', tone: 'violet', icon: 'i-ant-design:file-text-twotone' },
  { label: '当前患者', value: selectedArchive.value?.patientName ? normalizeText(selectedArchive.value.patientName) : '未选择', note: '当前选中档案袋对应患者', tone: 'amber', icon: 'i-ant-design:user-outlined' },
])

function normalizeText(value: unknown) {
  const text = String(value ?? '').trim()
  return text && text.toUpperCase() !== 'NULL' ? text : '-'
}

function padTo8Digits(value: unknown) {
  const text = normalizeText(value)
  if (text === '-') {
    return '-'
  }
  const num = Number.parseInt(text, 10)
  if (Number.isNaN(num)) {
    return text
  }
  return String(num).padStart(8, '0')
}

function formatDate(value: unknown) {
  const text = normalizeText(value)
  if (text === '-') {
    return '-'
  }
  return text.replace(/\//g, '-').split(/[ T]/)[0]
}

async function copyCode(text: string, label: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label} 已复制`)
  }
  catch {
    ElMessage.error('复制失败')
  }
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

function toneClass(item: ArchiveItem, index = 0) {
  const palette = ['tone-blue', 'tone-green', 'tone-amber', 'tone-rose', 'tone-slate']
  const seed = `${item.bah || ''}-${item.type || ''}-${index}`
  let hash = 0
  for (let i = 0; i < seed.length; i += 1) {
    hash = (hash * 31 + seed.charCodeAt(i)) >>> 0
  }
  return palette[hash % palette.length]
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
    path: `/archive/${item.bah}`,
    query: {
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
    const res = await exportStatisticsCsv(params)
    const blob = new Blob([res.data as BlobPart], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `statistics-${Date.now()}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  }
  catch {
    ElMessage.error('导出失败')
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
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">
          Archive Detail
        </p>
        <h2>病案明细档案袋</h2>
        <p class="subtitle">
          统一查看患者、住院、归档与扫描信息，支持筛选、排序、影像查看和整袋下载。
        </p>
      </div>
      <div class="header-actions">
        <el-button :icon="Download" @click="handleExportCsv">
          导出 CSV
        </el-button>
        <el-button :icon="Refresh" :loading="loading" @click="refreshAll">
          刷新
        </el-button>
        <el-button :icon="DataBoard" @click="goBackToStatistics">
          返回统计
        </el-button>
      </div>
    </div>

    <section class="mrr-metric-grid">
      <el-card
        v-for="item in summaryCards"
        :key="item.label"
        shadow="never"
        class="mrr-metric-card"
        :class="`mrr-metric-card--${item.tone}`"
      >
        <div class="mrr-metric-card__icon">
          <i :class="item.icon" />
        </div>
        <div class="mrr-metric-card__body">
          <span class="mrr-metric-card__label">{{ item.label }}</span>
          <strong class="mrr-metric-card__value">
            {{ Number.isFinite(Number(item.value)) ? Number(item.value).toLocaleString('zh-CN') : item.value }}
          </strong>
          <p class="mrr-metric-card__note">
            {{ item.note }}
          </p>
        </div>
      </el-card>
    </section>

    <el-card shadow="never">
      <template #header>
        <div class="panel-header">
          <div>
            <span class="panel-title">档案筛选</span>
            <span class="panel-subtitle">可按患者姓名、病人 ID、住院科室、设备、负责人或日期定位档案袋</span>
          </div>
          <el-tag type="info">
            {{ listData.total || 0 }} 条
          </el-tag>
        </div>
      </template>

      <div class="filter-grid">
        <el-input
          v-model="filters.bah"
          class="filter-bah"
          clearable
          placeholder="病案号"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="filters.sjh"
          class="filter-sjh"
          clearable
          placeholder="上架号"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="filters.keyword"
          class="filter-keyword"
          clearable
          placeholder="患者姓名、病人 ID、科室、设备或负责人"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="filters.type" class="filter-type" clearable placeholder="全部类型" @change="handleSearch">
          <el-option v-for="item in typeOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-date-picker
          v-model="filters.dateRange"
          class="filter-date"
          type="daterange"
          range-separator="至"
          start-placeholder="归档开始日期"
          end-placeholder="归档结束日期"
          value-format="YYYY-MM-DD"
        />
        <el-select v-model="sortKey" class="filter-sort" @change="handleSearch">
          <el-option v-for="item in sortOptions" :key="item.key" :label="item.label" :value="item.key" />
        </el-select>
        <div class="filter-actions">
          <el-button type="primary" :icon="Search" @click="handleSearch">
            查询
          </el-button>
          <el-button @click="resetSearch">
            重置
          </el-button>
        </div>
      </div>
    </el-card>

    <section class="content-layout">
      <div ref="archiveShelfRef" class="archive-shelf">
        <div class="archive-toolbar">
          <span class="archive-toolbar-title">档案列表</span>
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
          <article
            v-for="(item, index) in listData.list"
            :key="archiveKey(item, index)"
            class="archive-folder-card"
            :class="[toneClass(item, index), { 'is-selected': selectedArchiveKey === archiveKey(item, index) }]"
            role="button"
            tabindex="0"
            :aria-pressed="selectedArchiveKey === archiveKey(item, index)"
            :aria-label="`患者 ${normalizeText(item.patientName)}，病案号 ${padTo8Digits(item.bah)}，上架号 ${padTo8Digits(item.sjh)}，${Number(item.pages || 0)} 页`"
            @click="selectArchive(item, index)"
            @keyup.enter="selectArchive(item, index)"
            @keyup.space.prevent="selectArchive(item, index)"
          >
            <div class="folder-layer folder-layer-back" />
            <div class="folder-layer folder-layer-middle" />
            <div class="folder-card-body">
              <div class="folder-top">
                <div class="folder-identity">
                  <span class="folder-index">NO. A{{ tableIndex(index) }}</span>
                  <span
                    v-if="selectedArchiveKey === archiveKey(item, index)"
                    class="folder-selected-label"
                  >
                    <i />已选中
                  </span>
                </div>
                <el-tag size="small" :type="typeTone(item.type)" effect="plain">
                  {{ normalizeText(item.type) }}
                </el-tag>
              </div>

              <div class="folder-code-block folder-code-block-full">
                <span class="folder-code-label">病人姓名</span>
                <strong class="folder-code-value">{{ normalizeText(item.patientName) }}</strong>
              </div>

              <div class="folder-code-grid">
                <div class="folder-code-block">
                  <span class="folder-code-label">住院科室</span>
                  <strong class="folder-code-value">{{ normalizeText(item.inpatientDepartment) }}</strong>
                </div>
                <div class="folder-code-block">
                  <span class="folder-code-label">出院日期</span>
                  <strong class="folder-code-value">{{ formatDate(item.dischargeDate) }}</strong>
                </div>
                <div class="folder-code-block folder-code-copyable" title="点击复制病案号" @click="copyCode(padTo8Digits(item.bah), '病案号')">
                  <span class="folder-code-label">病案号</span>
                  <strong class="folder-code-value">{{ padTo8Digits(item.bah) }}</strong>
                </div>
                <div class="folder-code-block folder-code-copyable" title="点击复制上架号" @click="copyCode(padTo8Digits(item.sjh), '上架号')">
                  <span class="folder-code-label">上架号</span>
                  <strong class="folder-code-value">{{ padTo8Digits(item.sjh) }}</strong>
                </div>
              </div>


              <div class="folder-footer">
                <div class="folder-page-count">
                  <strong>{{ Number(item.pages || 0).toLocaleString('zh-CN') }}</strong>
                  <span>扫描页</span>
                </div>
                <el-button class="folder-action" text type="primary" @click.stop="openArchive(item)">
                  查看影像
                  <span aria-hidden="true">→</span>
                </el-button>
              </div>
            </div>
          </article>
        </div>
        <div v-else class="archive-list-wrap">
          <el-table
            :data="listData.list"
            :row-class-name="archiveRowClassName"
            class="archive-list"
            row-key="id"
            @row-click="selectArchiveFromList"
          >
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
            <el-table-column label="操作" width="96" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click.stop="openArchive(row)">
                  查看影像
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="pageSize"
            :total="listData.total"
            layout="total, prev, pager, next, jumper"
            @current-change="loadArchiveList"
          />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 20px;
}

.page-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.header-actions {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

h2 {
  margin: 0;
  font-size: 28px;
}

.subtitle {
  margin: 8px 0 0;
  color: var(--text-secondary);
}

.panel-header {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.panel-title {
  margin-right: 8px;
  font-weight: 700;
}

.panel-subtitle {
  font-size: 13px;
  color: var(--text-secondary);
}

.filter-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
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
  flex: 0 0 170px;
}

.filter-date {
  flex: 1 1 290px;
  min-width: 260px;
}

.filter-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
  margin-left: auto;
}

.content-layout,
.archive-shelf {
  display: grid;
  gap: 16px;
}

.archive-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.archive-toolbar-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.archive-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 18px;
}

.archive-list-wrap {
  overflow-x: auto;
  border: 1px solid var(--divider);
  border-radius: 8px;
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
  font-size: 11px;
  color: var(--text-secondary);
}

:deep(.el-table__row) {
  cursor: pointer;
}

:deep(.el-table__row.archive-list-row-selected > td.el-table__cell) {
  background: hsl(var(--primary) / 8%);
}

.archive-folder-card {
  --folder-accent: #1d4ed8;
  --folder-tint: #eef4ff;

  position: relative;
  min-height: 412px;
  padding: 16px 8px 8px;
  cursor: pointer;
  outline: none;
  isolation: isolate;
  transition: z-index 0s linear 0.24s;
}

.archive-folder-card:hover,
.archive-folder-card:focus-visible {
  z-index: 2;
  transition-delay: 0s;
}

.folder-layer {
  position: absolute;
  pointer-events: none;
  border: 1px solid color-mix(in srgb, var(--folder-accent) 22%, var(--divider));
  border-radius: 14px;
  transition: transform 0.24s ease, border-color 0.24s ease, box-shadow 0.24s ease;
}

.folder-layer-back {
  inset: 6px 24px 18px 20px;
  z-index: -2;
  background: color-mix(in srgb, var(--folder-tint) 72%, var(--surface));
  transform: rotate(-1.4deg);
}

.folder-layer-middle {
  inset: 11px 12px 11px 14px;
  z-index: -1;
  background: color-mix(in srgb, var(--folder-tint) 48%, var(--surface));
  transform: rotate(0.8deg);
}

.folder-card-body {
  position: relative;
  display: grid;
  gap: 13px;
  min-height: 388px;
  padding: 21px 20px 17px;
  overflow: hidden;
  background: linear-gradient(155deg, color-mix(in srgb, var(--folder-tint) 35%, var(--surface)) 0%, var(--surface) 42%);
  border: 1px solid var(--divider);
  border-radius: 14px;
  box-shadow: 0 7px 20px rgb(15 23 42 / 6%);
  transition: transform 0.24s ease, border-color 0.24s ease, box-shadow 0.24s ease, background-color 0.24s ease;
}

.folder-card-body::before {
  position: absolute;
  top: 0;
  right: 22px;
  left: 22px;
  height: 3px;
  content: "";
  background: var(--folder-accent);
  border-radius: 0 0 999px 999px;
  opacity: 0.34;
  transform: scaleX(0.42);
  transform-origin: center;
  transition: opacity 0.24s ease, transform 0.24s ease;
}

.folder-card-body::after {
  position: absolute;
  right: -42px;
  bottom: -58px;
  width: 150px;
  height: 150px;
  pointer-events: none;
  content: "";
  background: radial-gradient(circle, color-mix(in srgb, var(--folder-accent) 9%, transparent), transparent 68%);
  transition: opacity 0.24s ease, transform 0.24s ease;
}

.archive-folder-card:hover .folder-card-body,
.archive-folder-card:focus-visible .folder-card-body {
  background: linear-gradient(155deg, color-mix(in srgb, var(--folder-tint) 54%, var(--surface)) 0%, var(--surface) 48%);
  border-color: color-mix(in srgb, var(--folder-accent) 72%, var(--divider));
  box-shadow: 0 18px 42px rgb(15 23 42 / 14%), 0 0 0 3px color-mix(in srgb, var(--folder-accent) 11%, transparent);
  transform: translateY(-4px);
}

.archive-folder-card:hover .folder-card-body::before,
.archive-folder-card:focus-visible .folder-card-body::before,
.archive-folder-card.is-selected .folder-card-body::before {
  opacity: 0.9;
  transform: scaleX(1);
}

.archive-folder-card:hover .folder-card-body::after,
.archive-folder-card:focus-visible .folder-card-body::after {
  opacity: 1;
  transform: translate(-8px, -8px) scale(1.08);
}

.archive-folder-card:hover .folder-layer-back,
.archive-folder-card:focus-visible .folder-layer-back {
  border-color: color-mix(in srgb, var(--folder-accent) 42%, var(--divider));
  box-shadow: 0 8px 18px color-mix(in srgb, var(--folder-accent) 10%, transparent);
  transform: translate(-5px, -4px) rotate(-2.6deg);
}

.archive-folder-card:hover .folder-layer-middle,
.archive-folder-card:focus-visible .folder-layer-middle {
  border-color: color-mix(in srgb, var(--folder-accent) 34%, var(--divider));
  transform: translate(5px, -2px) rotate(1.6deg);
}

.archive-folder-card.is-selected .folder-card-body {
  border-color: var(--folder-accent);
  box-shadow: 0 12px 30px rgb(15 23 42 / 11%), 0 0 0 3px color-mix(in srgb, var(--folder-accent) 13%, transparent);
}

.archive-folder-card.is-selected .folder-layer-back {
  transform: translate(-2px, -2px) rotate(-2deg);
}

.archive-folder-card.is-selected .folder-layer-middle {
  transform: translate(2px, -1px) rotate(1.2deg);
}

.tone-blue { --folder-accent: #2563eb; --folder-tint: #eef4ff; }
.tone-green { --folder-accent: #0f766e; --folder-tint: #ecfdf7; }
.tone-amber { --folder-accent: #b86b0b; --folder-tint: #fff6e8; }
.tone-rose { --folder-accent: #be185d; --folder-tint: #fff0f5; }
.tone-slate { --folder-accent: #64748b; --folder-tint: var(--surface-alt); }

:global(.dark) .tone-blue { --folder-tint: color-mix(in srgb, #2563eb 16%, var(--surface)); }
:global(.dark) .tone-green { --folder-tint: color-mix(in srgb, #0f766e 16%, var(--surface)); }
:global(.dark) .tone-amber { --folder-tint: color-mix(in srgb, #b86b0b 16%, var(--surface)); }
:global(.dark) .tone-rose { --folder-tint: color-mix(in srgb, #be185d 16%, var(--surface)); }

.folder-top,
.folder-footer {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.folder-identity {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.folder-index {
  font-size: 10px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  color: var(--text-secondary);
  letter-spacing: 0.11em;
}

.folder-selected-label {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  padding: 3px 7px;
  font-size: 10px;
  font-weight: 700;
  color: var(--folder-accent);
  background: color-mix(in srgb, var(--folder-accent) 9%, transparent);
  border-radius: 999px;
}

.folder-selected-label i {
  width: 5px;
  height: 5px;
  background: var(--folder-accent);
  border-radius: 50%;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--folder-accent) 14%, transparent);
}

.folder-code-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.folder-code-block {
  position: relative;
  min-width: 0;
  padding: 11px 12px;
  background: color-mix(in srgb, var(--folder-accent) 4%, var(--surface));
  border: 1px solid color-mix(in srgb, var(--folder-accent) 13%, var(--divider));
  border-radius: 10px;
}

.folder-code-copyable {
  cursor: pointer;
}

.folder-code-copyable:hover {
  background: color-mix(in srgb, var(--folder-accent) 8%, var(--surface));
  border-color: color-mix(in srgb, var(--folder-accent) 24%, var(--divider));
}

.folder-code-label {
  display: block;
  margin-bottom: 5px;
  font-size: 10px;
  font-weight: 700;
  color: var(--text-secondary);
  letter-spacing: 0.08em;
}

.folder-code-value {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: clamp(15px, 1.4vw, 18px);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  color: color-mix(in srgb, var(--text-primary) 90%, var(--bg));
  white-space: nowrap;
}

.folder-code-block-full {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.folder-code-block-full .folder-code-label {
  flex: none;
}

.folder-code-block-full .folder-code-value {
  flex: 1;
  min-width: 0;
  font-size: clamp(24px, 2.6vw, 30px);
  color: color-mix(in srgb, var(--text-primary) 88%, var(--bg));
  text-align: right;
}

.folder-code-block:nth-child(-n+2) .folder-code-value {
  font-size: clamp(13px, 1.2vw, 14px);
  font-weight: 700;
  color: color-mix(in srgb, var(--text-primary) 88%, var(--bg));
}

.folder-footer {
  padding-top: 11px;
  border-top: 1px dashed color-mix(in srgb, var(--folder-accent) 18%, var(--divider));
}

.folder-page-count {
  display: flex;
  gap: 7px;
  align-items: baseline;
  color: var(--folder-accent);
}

.folder-page-count strong {
  font-size: 22px;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.folder-page-count span {
  font-size: 11px;
  font-weight: 700;
  color: var(--text-secondary);
}

.folder-action {
  font-weight: 700;
}

.folder-action span {
  margin-left: 5px;
  font-size: 15px;
  transition: transform 0.2s ease;
}

.archive-folder-card:hover .folder-action span,
.archive-folder-card:focus-visible .folder-action span {
  transform: translateX(4px);
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
}

@media (prefers-reduced-motion: reduce) {
  .archive-folder-card,
  .folder-layer,
  .folder-card-body,
  .folder-card-body::before,
  .folder-card-body::after,
  .folder-code-block,
  .folder-action span {
    transition: none;
  }

  .archive-folder-card:hover .folder-card-body,
  .archive-folder-card:focus-visible .folder-card-body {
    transform: none;
  }
}

@media (width <= 720px) {
  .page-header {
    flex-direction: column;
  }

  .header-actions {
    justify-content: flex-start;
  }

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

@media (width <= 480px) {
  .archive-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .folder-code-grid {
    grid-template-columns: 1fr;
  }
}
</style>
