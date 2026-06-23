<script setup lang="ts">
import type { StatisticsRecord } from '@/api/types'
import { ElMessage } from 'element-plus'
import { DataBoard, Refresh, Search } from '@element-plus/icons-vue'

import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getStatisticsList, getStatisticsSummary } from '@/api/modules/statistics'

defineOptions({ name: 'StatisticsDetailPage' })

interface ArchiveItem extends StatisticsRecord {}

interface ListData {
  total: number
  size: number
  totalPages: number
  page: number
  list: ArchiveItem[]
}

const router = useRouter()
const loading = ref(false)

const error = ref('')
const summaryData = ref<any>({ byType: [], total: {} })
const listData = ref<ListData>({
  total: 0,
  size: 18,
  totalPages: 0,
  page: 1,
  list: [],
})

const currentPage = ref(1)
const pageSize = ref(18)
const selectedArchive = ref<ArchiveItem | null>(null)
const selectedArchiveKey = ref('')

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
  const source: any[] = summaryData.value?.byType || []
  return source
    .map(item => String(item?.type || '').trim())
    .filter(item => item && item.toUpperCase() !== 'NULL')
})

const currentSort = computed(() => sortOptions.find(item => item.key === sortKey.value) || sortOptions[0])



const summaryCards = computed(() => [
  { label: '档案袋总数', value: listData.value.total || 0, note: '符合当前筛选条件的统计记录' },
  { label: '病案数量', value: summaryData.value?.uniqueBAHCount ?? 0, note: '系统内已归档病案号数量' },
  { label: '总页数', value: summaryData.value?.total?.totalPages ?? 0, note: '统计表累计扫描页数' },
  { label: '当前选中', value: selectedArchive.value?.bah || '未选择', note: '可进入影像档案袋查看原图' },
])

function normalizeText(value: unknown) {
  const text = String(value ?? '').trim()
  return text && text.toUpperCase() !== 'NULL' ? text : '-'
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
    summaryData.value = (res as any).data || {}
  }
  catch (err) {
    console.error('加载统计摘要失败:', err)
  }
}

async function loadArchiveList() {
  loading.value = true
  error.value = ''
  try {
    const params: any = {
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

    const res = await getStatisticsList(params)
    const payload = (res as any).data || {}
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
  catch (err: any) {
    error.value = err?.message || '病案明细加载失败'
    ElMessage.error(error.value)
    listData.value = { total: 0, size: pageSize.value, totalPages: 0, page: 1, list: [] }
  }
  finally {
    loading.value = false
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

function handlePageSizeChange(value: number) {
  pageSize.value = value
  currentPage.value = 1
  loadArchiveList()
}

function selectArchive(item: ArchiveItem, index = 0) {
  selectedArchive.value = item
  selectedArchiveKey.value = archiveKey(item, index)
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

onMounted(refreshAll)
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
        <el-button :icon="Refresh" :loading="loading" @click="refreshAll">
          刷新
        </el-button>
        <el-button :icon="DataBoard" @click="goBackToStatistics">
          返回统计
        </el-button>
      </div>
    </div>

    <section class="summary-grid">
      <el-card v-for="item in summaryCards" :key="item.label" shadow="never">
        <div class="summary-label">
          {{ item.label }}
        </div>
        <div class="summary-value">
          {{ Number.isFinite(Number(item.value)) ? Number(item.value).toLocaleString('zh-CN') : item.value }}
        </div>
        <div class="summary-note">
          {{ item.note }}
        </div>
      </el-card>
    </section>

    <el-alert v-if="error" :title="error" type="error" show-icon />

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
      <div class="archive-shelf">
        <div v-if="loading && !listData.list.length" class="archive-loading">
          <el-skeleton :rows="8" animated />
        </div>
        <div v-else class="archive-grid">
          <article
            v-for="(item, index) in listData.list"
            :key="archiveKey(item, index)"
            class="archive-folder-card"
            :class="[toneClass(item, index), { 'is-selected': selectedArchiveKey === archiveKey(item, index) }]"
            @click="selectArchive(item, index)"
          >
            <div class="folder-tab" />
            <div class="folder-top">
              <span class="folder-index">A{{ tableIndex(index) }}</span>
              <el-tag size="small" :type="typeTone(item.type)">
                {{ normalizeText(item.type) }}
              </el-tag>
            </div>

            <h4 class="folder-title">
              {{ normalizeText(item.bah) }}
            </h4>
            <p class="folder-subtitle">
              {{ formatDate(item.date) }} / {{ normalizeText(item.cid) }}
            </p>

            <dl class="folder-meta-grid">
              <div>
                <dt>扫描设备</dt>
                <dd>{{ normalizeText(item.cid) }}</dd>
              </div>
              <div>
                <dt>负责人</dt>
                <dd>{{ normalizeText(item.openerNo) }}</dd>
              </div>
              <div>
                <dt>归档日期</dt>
                <dd>{{ formatDate(item.date) }}</dd>
              </div>
              <div>
                <dt>页数</dt>
                <dd>{{ Number(item.pages || 0).toLocaleString('zh-CN') }} 页</dd>
              </div>
              <div>
                <dt>上架号</dt>
                <dd>{{ normalizeText(item.sjh) }}</dd>
              </div>
            </dl>

            <div class="folder-footer">
              <span>{{ Number(item.pages || 0).toLocaleString('zh-CN') }} 页档案</span>
              <el-button text type="primary" @click.stop="openArchive(item)">
                查看影像
              </el-button>
            </div>
          </article>
        </div>

        <div v-if="!loading && listData.list.length === 0" class="empty-wrap">
          <el-empty description="暂无档案袋数据" />
        </div>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[18, 50, 100, 200]"
            :total="listData.total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handlePageSizeChange"
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
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

h2 {
  margin: 0;
  font-size: 28px;
}

.subtitle {
  margin: 8px 0 0;
  color: #64748b;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-label {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
}

.summary-value {
  margin-top: 10px;
  overflow-wrap: anywhere;
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
}

.summary-note {
  margin-top: 8px;
  font-size: 13px;
  color: #64748b;
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
  color: #64748b;
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

.archive-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.archive-loading,
.empty-wrap {
  padding: 40px 0;
}

.archive-folder-card {
  --folder-accent: #1d4ed8;
  --folder-bg: #eef4ff;

  position: relative;
  display: grid;
  gap: 12px;
  min-height: 270px;
  padding: 18px;
  overflow: hidden;
  cursor: pointer;
  background: linear-gradient(180deg, var(--folder-bg), #fff 58%);
  border: 1px solid rgb(15 23 42 / 14%);
  border-top: 6px solid var(--folder-accent);
  border-radius: 7px;
  box-shadow: 0 12px 28px rgb(15 23 42 / 7%);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.archive-folder-card:hover,
.archive-folder-card.is-selected {
  transform: translateY(-2px);
  box-shadow: 0 20px 42px rgb(15 23 42 / 12%);
}

.archive-folder-card.is-selected {
  outline: 2px solid color-mix(in srgb, var(--folder-accent) 70%, transparent);
}

.tone-blue { --folder-accent: #2563eb; --folder-bg: #eef4ff; }
.tone-green { --folder-accent: #0f766e; --folder-bg: #ecfdf7; }
.tone-amber { --folder-accent: #c97b18; --folder-bg: #fff6e8; }
.tone-rose { --folder-accent: #be185d; --folder-bg: #fff0f5; }
.tone-slate { --folder-accent: #475569; --folder-bg: #f1f5f9; }

.folder-tab {
  position: absolute;
  top: 0;
  left: 18px;
  width: 70px;
  height: 18px;
  background: var(--folder-accent);
  border-radius: 0 0 5px 5px;
  opacity: 0.22;
}

.folder-top,
.folder-footer {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.folder-top {
  padding-top: 14px;
}

.folder-index {
  font-size: 11px;
  font-weight: 800;
  color: #64748b;
  letter-spacing: 0.12em;
}

.folder-title {
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 18px;
  font-weight: 800;
  color: #172033;
}

.folder-subtitle {
  margin: 0;
  font-size: 13px;
  color: #64748b;
}

.folder-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.folder-meta-grid dt {
  font-size: 11px;
  font-weight: 700;
  color: #80879a;
}

.folder-meta-grid dd {
  margin: 4px 0 0;
  overflow-wrap: anywhere;
  font-size: 13px;
  font-weight: 600;
  color: #24324b;
}

.folder-footer {
  padding-top: 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--folder-accent);
  border-top: 1px dashed rgb(100 116 139 / 28%);
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1180px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
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
}
</style>
