<script setup lang="ts">
import { DataBoard, Delete, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getStatisticsList, getStatisticsSummary } from '@/api/modules/statistics'

defineOptions({ name: 'StatisticsDetailPage' })

interface ArchiveItem {
  bah?: string
  cid?: string
  openerNo?: string
  date?: string
  type?: string
  pages?: number | null
}

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
const summaryData = ref<any>({ byType: [] })
const statisticsListData = ref<ListData>({
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
  type: '',
  dateRange: [] as string[],
})

const sortKey = ref('date-desc')
const sortOptions = [
  { key: 'date-desc', label: '按日期倒序', prop: 'date', order: 'descending' },
  { key: 'bah-asc', label: '按病案号升序', prop: 'bah', order: 'ascending' },
  { key: 'pages-desc', label: '按页数倒序', prop: 'pages', order: 'descending' },
]

const statisticsTypeOptions = computed(() => {
  const source: any[] = summaryData.value?.byType || []
  return source.map((item: any) => item?.type).filter((t: any) => t && t !== 'NULL')
})

const currentSort = computed(
  () => sortOptions.find(item => item.key === sortKey.value) || sortOptions[0],
)

function getBackendSortOrder(order: string) {
  return order === 'ascending' ? 'asc' : 'desc'
}

function formatDate(dateStr: string | undefined) {
  if (!dateStr) { return '无日期' }
  return String(dateStr).replace(/\//g, '-')
}

function getTypeTagType(type: string | undefined): '' | 'success' | 'warning' | 'danger' | 'primary' | 'info' {
  const value = String(type || '').toLowerCase()
  if (value.includes('急')) { return 'danger' }
  if (value.includes('高')) { return 'warning' }
  if (value.includes('住')) { return 'success' }
  if (value.includes('门')) { return 'primary' }
  return 'info'
}

function pickToneClass(seed: string) {
  const palette = ['tone-blue', 'tone-green', 'tone-amber', 'tone-rose', 'tone-slate']
  let hash = 0
  for (let i = 0; i < seed.length; i += 1) {
    hash = (hash * 31 + seed.charCodeAt(i)) >>> 0
  }
  return palette[hash % palette.length]
}

function getToneClass(item: ArchiveItem, index = 0) {
  return pickToneClass(`${item?.bah || ''}-${item?.cid || ''}-${index}`)
}

function buildArchiveKey(item: ArchiveItem, index = 0) {
  return [item?.bah, item?.cid, item?.date, item?.type, item?.pages, item?.openerNo, index].join('|')
}

function computeTableIndex(index: number) {
  return (currentPage.value - 1) * pageSize.value + index + 1
}

function formatArchiveLabel(item: ArchiveItem | null) {
  return item?.bah || '未命名病案'
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

async function loadStatisticsList() {
  loading.value = true
  error.value = ''
  try {
    const params: Record<string, any> = {
      page: currentPage.value,
      size: pageSize.value,
      sortBy: currentSort.value.prop || 'date',
      sortOrder: getBackendSortOrder(currentSort.value.order),
    }
    if (filters.keyword.trim()) { params.keyword = filters.keyword.trim() }
    if (filters.type) { params.type = filters.type }
    if (Array.isArray(filters.dateRange) && filters.dateRange.length === 2) {
      params.startDate = filters.dateRange[0]
      params.endDate = filters.dateRange[1]
    }

    const res = await getStatisticsList(params)
    const payload = (res as any).data || {}
    statisticsListData.value = {
      total: Number(payload.total || 0),
      size: Number(payload.size || pageSize.value),
      totalPages: Number(payload.totalPages || 0),
      page: Number(payload.page || currentPage.value),
      list: Array.isArray(payload.list)
        ? payload.list.filter((item: any) => item !== null)
        : [],
    }

    const firstItem = statisticsListData.value.list[0] || null
    selectedArchive.value = firstItem
    selectedArchiveKey.value = firstItem ? buildArchiveKey(firstItem, 0) : ''
  }
  catch (err: any) {
    error.value = err?.message || '加载病案明细失败'
    ElMessage.error(error.value)
    statisticsListData.value = { total: 0, size: pageSize.value, totalPages: 0, page: 1, list: [] }
  }
  finally {
    loading.value = false
  }
}

async function refreshAll() {
  await loadSummary()
  await loadStatisticsList()
}

function handleSearch() {
  currentPage.value = 1
  loadStatisticsList()
}

function resetSearch() {
  filters.keyword = ''
  filters.type = ''
  filters.dateRange = []
  sortKey.value = 'date-desc'
  currentPage.value = 1
  loadStatisticsList()
}

function handleSortChange() {
  currentPage.value = 1
  loadStatisticsList()
}

function handleSizeChange(newSize: number) {
  pageSize.value = newSize
  currentPage.value = 1
  loadStatisticsList()
}

function handleCurrentChange(newPage: number) {
  currentPage.value = newPage
  loadStatisticsList()
}

function selectArchive(item: ArchiveItem, index = 0) {
  selectedArchive.value = item
  selectedArchiveKey.value = buildArchiveKey(item, index)
}

function openArchiveImages(item: ArchiveItem, index = 0) {
  if (!item?.bah) {
    ElMessage.warning('当前档案缺少病案号，无法打开图片页')
    return
  }
  router.push({
    path: `/statistics/archive/${item.bah}`,
    query: {
      bah: item.bah,
      cid: item.cid || '',
      type: item.type || '',
      date: item.date || '',
      pages: String(item.pages ?? ''),
      openerNo: item.openerNo || '',
      index: String(index),
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
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <p class="eyebrow">
          Archive Detail
        </p>
        <h2>病案明细档案袋</h2>
        <p class="subtitle">
          以仿真的档案袋卡片形式展示病案明细，保留筛选、分页和排序能力。
        </p>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" :loading="loading" @click="refreshAll">
          刷新档案
        </el-button>
        <el-button :icon="DataBoard" @click="goBackToStatistics">
          返回统计
        </el-button>
      </div>
    </div>

    <!-- 摘要卡片 -->
    <section class="summary-grid">
      <el-card shadow="never">
        <div class="summary-label">
          档案总数
        </div>
        <div class="summary-value">
          {{ statisticsListData.total || 0 }}
        </div>
        <div class="summary-note">
          来自病案明细接口的总记录数
        </div>
      </el-card>
      <el-card shadow="never">
        <div class="summary-label">
          类型种类
        </div>
        <div class="summary-value">
          {{ statisticsTypeOptions.length }}
        </div>
        <div class="summary-note">
          按病案类型聚合后的分类数量
        </div>
      </el-card>
      <el-card shadow="never">
        <div class="summary-label">
          当前页
        </div>
        <div class="summary-value">
          {{ statisticsListData.page || currentPage }}
        </div>
        <div class="summary-note">
          每页 {{ pageSize }} 条，支持翻页查看
        </div>
      </el-card>
      <el-card shadow="never">
        <div class="summary-label">
          已选档案
        </div>
        <div class="summary-value selected-archive-val">
          {{ selectedArchive ? formatArchiveLabel(selectedArchive) : '未选择' }}
        </div>
        <div class="summary-note">
          点击任意档案袋可查看档案详情
        </div>
      </el-card>
    </section>

    <!-- 错误提示 -->
    <el-alert v-if="error" :title="error" type="error" show-icon />

    <!-- 筛选栏 -->
    <el-card shadow="never">
      <template #header>
        <div class="panel-header">
          <div>
            <span class="panel-title">档案筛选</span>
            <span class="panel-subtitle">按病案号、扫描设备、类型和日期范围缩小结果范围。</span>
          </div>
          <el-tag type="info">
            {{ statisticsListData.total || 0 }} 条记录
          </el-tag>
        </div>
      </template>

      <div class="filter-grid">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索病案号或扫描设备"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-select
          v-model="filters.type"
          placeholder="全部类型"
          clearable
          @change="handleSearch"
        >
          <el-option
            v-for="item in statisticsTypeOptions"
            :key="item"
            :label="item"
            :value="item"
          />
        </el-select>
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
        />
        <el-select v-model="sortKey" @change="handleSortChange">
          <el-option
            v-for="item in sortOptions"
            :key="item.key"
            :label="item.label"
            :value="item.key"
          />
        </el-select>
        <div class="filter-actions">
          <el-button type="primary" :icon="Search" @click="handleSearch">
            筛选
          </el-button>
          <el-button :icon="Delete" @click="resetSearch">
            重置
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 档案袋书架 -->
    <section class="archive-shelf">
      <div v-if="loading && !statisticsListData.list.length" class="archive-loading">
        <el-skeleton :rows="6" animated />
      </div>

      <div v-else class="archive-grid">
        <article
          v-for="(item, index) in statisticsListData.list"
          :key="buildArchiveKey(item, index)"
          class="archive-folder-card"
          :class="[getToneClass(item, index), { 'is-selected': selectedArchiveKey === buildArchiveKey(item, index) }]"
          @click="selectArchive(item, index)"
        >
          <div class="folder-tab" />
          <div class="folder-top">
            <span class="folder-index">A{{ computeTableIndex(index) }}</span>
            <el-tag size="small" :type="getTypeTagType(item.type)">
              {{ item.type || '未分类' }}
            </el-tag>
          </div>

          <h4 class="folder-title">
            {{ item.bah || '未命名病案' }}
          </h4>
          <p class="folder-subtitle">
            {{ formatDate(item.date) }} · {{ item.cid || '未识别设备' }}
          </p>

          <dl class="folder-meta-grid">
            <div>
              <dt>扫描设备</dt>
              <dd>{{ item.cid || '-' }}</dd>
            </div>
            <div>
              <dt>扫描负责人</dt>
              <dd>{{ item.openerNo === 'NULL' ? '-' : item.openerNo || '-' }}</dd>
            </div>
            <div>
              <dt>归档日期</dt>
              <dd>{{ formatDate(item.date) }}</dd>
            </div>
            <div>
              <dt>页数</dt>
              <dd>{{ item.pages ?? 0 }} 页</dd>
            </div>
          </dl>

          <div class="folder-footer">
            <span class="folder-pages">{{ item.pages ?? 0 }} 页档案</span>
            <el-button text type="primary" @click.stop="openArchiveImages(item, index)">
              查看详情
            </el-button>
          </div>
        </article>
      </div>

      <div v-if="!loading && statisticsListData.list.length === 0" class="empty-wrap">
        <el-empty description="暂无档案数据" />
      </div>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[18, 50, 100, 200]"
          :total="statisticsListData.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
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
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.header-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #64748b;
  font-weight: 700;
}

h2 {
  margin: 0;
  font-size: 28px;
}

.subtitle {
  margin: 8px 0 0;
  color: #64748b;
}

/* 摘要卡片 */
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-label {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.summary-value {
  margin-top: 10px;
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
  word-break: break-all;
}

.selected-archive-val {
  font-size: 16px;
}

.summary-note {
  margin-top: 8px;
  font-size: 13px;
  color: #64748b;
}

/* 筛选栏 */
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-title {
  font-weight: 700;
  margin-right: 8px;
}

.panel-subtitle {
  font-size: 13px;
  color: #64748b;
}

.filter-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) 180px 260px 180px auto;
  gap: 12px;
  align-items: center;
}

.filter-actions {
  display: flex;
  gap: 8px;
}

/* 档案袋书架 */
.archive-shelf {
  display: grid;
  gap: 16px;
}

.archive-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.archive-loading {
  padding: 10px 4px 6px;
}

.empty-wrap {
  padding: 40px 0;
}

/* 档案袋卡片 */
.archive-folder-card {
  position: relative;
  display: grid;
  gap: 12px;
  padding: 18px 18px 16px;
  border-radius: 24px 24px 20px 20px;
  border: 1px solid rgba(195, 197, 215, 0.18);
  border-top-width: 6px;
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.95), rgba(247, 250, 255, 0.92));
  box-shadow: 0 14px 34px rgba(24, 65, 134, 0.08);
  cursor: pointer;
  overflow: hidden;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.archive-folder-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 20px 40px rgba(24, 65, 134, 0.12);
}

.archive-folder-card.is-selected {
  transform: translateY(-2px);
  box-shadow: 0 24px 48px rgba(0, 63, 177, 0.18);
}

/* 配色变量 */
.archive-folder-card.tone-blue {
  --folder-accent: #1d4ed8;
  --folder-bg: #eef4ff;
}

.archive-folder-card.tone-green {
  --folder-accent: #0f766e;
  --folder-bg: #ecfdf7;
}

.archive-folder-card.tone-amber {
  --folder-accent: #c97b18;
  --folder-bg: #fff6e8;
}

.archive-folder-card.tone-rose {
  --folder-accent: #be185d;
  --folder-bg: #fff0f5;
}

.archive-folder-card.tone-slate {
  --folder-accent: #475569;
  --folder-bg: #f1f5f9;
}

.archive-folder-card {
  border-top-color: var(--folder-accent, #1d4ed8);
  background-image: linear-gradient(180deg, var(--folder-bg, #eef4ff), rgba(255, 255, 255, 0) 55%);
}

/* 档案袋标签页 */
.folder-tab {
  position: absolute;
  top: -1px;
  left: 18px;
  width: 108px;
  height: 20px;
  border-radius: 0 0 16px 16px;
  background: linear-gradient(135deg, var(--folder-accent, #1d4ed8), rgba(255, 255, 255, 0.82));
  box-shadow: 0 10px 20px rgba(24, 65, 134, 0.14);
}

.folder-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 14px;
}

.folder-index {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: #64748b;
}

.folder-title {
  margin: 0;
  font-size: 18px;
  line-height: 1.3;
  font-weight: 800;
  color: #1f2b42;
}

.folder-subtitle {
  margin: 0;
  font-size: 13px;
  color: #667085;
}

.folder-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 14px;
  margin: 2px 0 0;
}

.folder-meta-grid div {
  display: grid;
  gap: 4px;
}

.folder-meta-grid dt {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #80879a;
}

.folder-meta-grid dd {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #24324b;
  word-break: break-word;
}

.folder-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 6px;
  border-top: 1px dashed rgba(128, 135, 154, 0.24);
}

.folder-pages {
  font-size: 12px;
  font-weight: 700;
  color: var(--folder-accent, #1d4ed8);
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

/* 响应式 */
@media (max-width: 1220px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .summary-grid,
  .filter-grid,
  .folder-meta-grid {
    grid-template-columns: 1fr;
  }

  .filter-actions,
  .pagination-wrapper {
    justify-content: stretch;
  }

  .filter-actions :deep(.el-button) {
    width: 100%;
  }
}
</style>
