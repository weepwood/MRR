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

interface ArchiveItem extends StatisticsRecord {}
type ArchiveDisplayMode = 'folder' | 'list'

interface ListData {
  total: number
  size: number
  totalPages: number
  page: number
  list: ArchiveItem[]
}

/** 统计明细查询参数 */
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
  { key: 'date-desc', label: '按日期倒序', prop: 'date', order: 'desc' },
  { key: 'date-asc', label: '按日期升序', prop: 'date', order: 'asc' },
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
  { label: '当前选中', value: selectedArchive.value?.bah ? padTo8Digits(selectedArchive.value.bah) : '未选择', note: '可进入影像档案袋查看原图', tone: 'amber', icon: 'i-ant-design:select-outlined' },
])

function normalizeText(value: unknown) {
  const text = String(value ?? '').trim()
  return text && text.toUpperCase() !== 'NULL' ? text : '-'
}

function padTo8Digits(value: unknown) {
  const text = normalizeText(value)
  if (text === '-') { return '-' }
  const num = Number.parseInt(text, 10)
  if (Number.isNaN(num)) { return text }
  return String(num).padStart(8, '0')
}

function formatDate(value: string | undefined) {
  if (!value || value.toUpperCase?.() === 'NULL') {
    return '-'
  }
  return String(value).replace(/\//g, '-')
}

function archiveKey(item: ArchiveItem, index = 0) {
  return [item.bah, item.cid, item.date, item.type, item.pages, item.openerNo, item.sjh, index].join('|')
}

function tableIndex(index: number) {
  return (currentPage.value - 1) * pageSize.value + index + 1
}

function typeTone(type: string | undefined) {
  const value = String(type || '')
  if (value.includes('首页')) { return 'success' }
  if (value.includes('手术')) { return 'warning' }
  if (value.includes('护理')) { return 'primary' }
  if (value.includes('其它') || value.includes('其他')) { return 'info' }
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
    const payload = res.data ?? { list: [], total: 0, size: pageSize.value, totalPages: 0, page: currentPage.value }
    const list = Array.isArray(payload.list) ? payload.list.filter(Boolean) : []
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
  if (filters.keyword.trim()) { params.keyword = filters.keyword.trim() }
  if (filters.bah.trim()) { params.bah = filters.bah.trim() }
  if (filters.sjh.trim()) { params.sjh = filters.sjh.trim() }
  if (filters.type) { params.type = filters.type }
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
          以档案袋方式查看病案统计明细，支持筛选、排序、影像查看和整袋下载。
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
            <span class="panel-subtitle">按病案号、设备、类型和日期范围定位档案袋</span>
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
          placeholder="搜索设备、人员或日期"
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
          start-placeholder="开始日期"
          end-placeholder="结束日期"
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
            :aria-label="`病案 ${padTo8Digits(item.bah)}，${Number(item.pages || 0)} 页`"
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

              <div class="folder-primary">
                <span class="folder-primary-label">病案号</span>
                <h4 class="folder-title">
                  {{ padTo8Digits(item.bah) }}
                </h4>
                <div class="folder-subtitle">
                  <span>{{ formatDate(item.date) }}</span>
                  <i />
                  <span>设备 {{ normalizeText(item.cid) }}</span>
                </div>
              </div>

              <dl class="folder-meta-grid">
                <div>
                  <dt>负责人</dt>
                  <dd>{{ normalizeText(item.openerNo) }}</dd>
                </div>
                <div>
                  <dt>上架号</dt>
                  <dd>{{ padTo8Digits(item.sjh) }}</dd>
                </div>
                <div>
                  <dt>归档日期</dt>
                  <dd>{{ formatDate(item.date) }}</dd>
                </div>
                <div>
                  <dt>扫描设备</dt>
                  <dd>{{ normalizeText(item.cid) }}</dd>
                </div>
              </dl>

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
            <el-table-column label="#" width="92" align="center">
              <template #default="{ $index }">
                {{ tableIndex($index) }}
              </template>
            </el-table-column>
            <el-table-column label="病案号" min-width="120">
              <template #default="{ row }">
                <strong class="archive-bah">{{ padTo8Digits(row.bah) }}</strong>
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
            <el-table-column label="上架号" min-width="100">
              <template #default="{ row }">
                {{ padTo8Digits(row.sjh) }}
              </template>
            </el-table-column>
            <el-table-column label="页数" width="80" align="right">
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
  gap: 10px;
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
  flex: 1 1 220px;
  min-width: 180px;
}

.filter-type,
.filter-sort {
  flex: 0 0 160px;
}

.filter-date {
  flex: 1 1 280px;
  min-width: 240px;
}

.filter-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
  margin-left: auto;
}

.content-layout {
  display: grid;
  gap: 16px;
}

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
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.archive-loading,
.empty-wrap {
  padding: 40px 0;
}

.archive-list-wrap {
  overflow-x: auto;
  border: 1px solid var(--divider);
  border-radius: 8px;
}

.archive-list {
  min-width: 940px;
}

.archive-bah {
  color: var(--text-primary);
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
  min-height: 318px;
  padding: 16px 8px 8px;
  cursor: pointer;
  outline: none;
  isolation: isolate;
}

.folder-layer {
  position: absolute;
  pointer-events: none;
  border: 1px solid color-mix(in srgb, var(--folder-accent) 22%, var(--divider));
  border-radius: 14px;
  transition: transform 0.22s ease, border-color 0.22s ease;
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
  gap: 15px;
  min-height: 294px;
  padding: 23px 20px 18px;
  overflow: hidden;
  background: linear-gradient(155deg, color-mix(in srgb, var(--folder-tint) 35%, var(--surface)) 0%, var(--surface) 42%);
  border: 1px solid var(--divider);
  border-radius: 14px;
  box-shadow: 0 8px 24px rgb(15 23 42 / 7%);
  transition: transform 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease;
}

.folder-card-body::after {
  position: absolute;
  right: -42px;
  bottom: -58px;
  width: 150px;
  height: 150px;
  pointer-events: none;
  content: "";
  background: radial-gradient(circle, color-mix(in srgb, var(--folder-accent) 8%, transparent), transparent 68%);
}

.archive-folder-card:hover .folder-card-body,
.archive-folder-card.is-selected .folder-card-body,
.archive-folder-card:focus-visible .folder-card-body {
  border-color: color-mix(in srgb, var(--folder-accent) 58%, var(--divider));
  box-shadow: 0 14px 34px rgb(15 23 42 / 12%), 0 0 0 3px color-mix(in srgb, var(--folder-accent) 9%, transparent);
  transform: translateY(-2px);
}

.archive-folder-card:hover .folder-layer-back,
.archive-folder-card.is-selected .folder-layer-back {
  transform: translate(-2px, -2px) rotate(-2deg);
}

.archive-folder-card:hover .folder-layer-middle,
.archive-folder-card.is-selected .folder-layer-middle {
  transform: translate(2px, -1px) rotate(1.2deg);
}

.archive-folder-card.is-selected .folder-card-body {
  border-color: var(--folder-accent);
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

.folder-primary {
  position: relative;
  z-index: 1;
  padding: 14px 15px;
  background: color-mix(in srgb, var(--folder-accent) 5%, var(--surface));
  border: 1px solid color-mix(in srgb, var(--folder-accent) 12%, var(--divider));
  border-radius: 11px;
}

.folder-primary-label {
  display: block;
  margin-bottom: 5px;
  font-size: 10px;
  font-weight: 700;
  color: var(--text-secondary);
  letter-spacing: 0.08em;
}

.folder-title {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: clamp(20px, 2vw, 24px);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  color: var(--text-primary);
  white-space: nowrap;
}

.folder-subtitle {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 8px;
  overflow: hidden;
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.folder-subtitle span {
  overflow: hidden;
  text-overflow: ellipsis;
}

.folder-subtitle i {
  flex: 0 0 3px;
  width: 3px;
  height: 3px;
  background: var(--folder-accent);
  border-radius: 50%;
  opacity: 0.7;
}

.folder-meta-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
}

.folder-meta-grid > div {
  min-width: 0;
  padding: 9px 10px;
  background: color-mix(in srgb, var(--surface-alt) 76%, transparent);
  border: 1px solid color-mix(in srgb, var(--divider) 72%, transparent);
  border-radius: 9px;
}

.folder-meta-grid dt {
  font-size: 10px;
  font-weight: 600;
  color: var(--text-secondary);
}

.folder-meta-grid dd {
  margin: 4px 0 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-primary);
  white-space: nowrap;
}

.folder-footer {
  z-index: 1;
  padding-top: 12px;
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

.archive-folder-card:hover .folder-action span {
  transform: translateX(3px);
}

@media (prefers-reduced-motion: reduce) {
  .folder-layer,
  .folder-card-body,
  .folder-action span {
    transition: none;
  }
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
}

@media (width <= 720px) {
  .page-header {
    flex-direction: column;
  }

  .summary-grid,
  .folder-meta-grid {
    grid-template-columns: 1fr;
  }

  .filter-bah,
  .filter-sjh,
  .filter-keyword,
  .filter-type,
  .filter-date,
  .filter-sort,
  .filter-actions {
    flex: 1 1 100%;
    margin-left: 0;
  }

  .archive-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
