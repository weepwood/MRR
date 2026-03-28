<template>
  <div class="statistics-detail-view pmr-page">
    <section class="pmr-page-header archive-hero">
      <div>
        <p class="module-eyebrow">Archive Detail</p>
        <h2 class="pmr-page-title">病案明细档案袋</h2>
        <p class="pmr-page-subtitle">以仿真的档案袋卡片形式展示病案明细，保留筛选、分页和排序能力。</p>
      </div>

      <div class="pmr-toolbar-actions">
        <el-button :icon="Refresh" :loading="loading" @click="refreshAll">刷新档案</el-button>
        <el-button :icon="DataBoard" @click="goBackToStatistics">返回统计</el-button>
      </div>
    </section>

    <section class="summary-grid">
      <el-card class="pmr-panel summary-card" shadow="never">
        <div class="summary-label">档案总数</div>
        <div class="summary-value">{{ statisticsListData.total || 0 }}</div>
        <div class="summary-note">来自病案明细接口的总记录数</div>
      </el-card>
      <el-card class="pmr-panel summary-card" shadow="never">
        <div class="summary-label">类型种类</div>
        <div class="summary-value">{{ statisticsTypeOptions.length }}</div>
        <div class="summary-note">按病案类型聚合后的分类数量</div>
      </el-card>
      <el-card class="pmr-panel summary-card" shadow="never">
        <div class="summary-label">当前页</div>
        <div class="summary-value">{{ statisticsListData.page || currentPage }}</div>
        <div class="summary-note">每页 {{ pageSize }} 条，支持翻页查看</div>
      </el-card>
      <el-card class="pmr-panel summary-card" shadow="never">
        <div class="summary-label">已选档案</div>
        <div class="summary-value">{{ selectedArchive ? formatArchiveLabel(selectedArchive) : '未选择' }}</div>
        <div class="summary-note">点击任意档案袋可查看档案详情</div>
      </el-card>
    </section>

    <el-alert v-if="error" :title="error" type="error" show-icon class="archive-alert" />

    <el-card class="pmr-panel archive-filter-panel" shadow="never">
      <template #header>
        <div class="pmr-panel-header">
          <div>
            <h3 class="pmr-panel-title">档案筛选</h3>
            <p class="pmr-panel-subtitle">按病案号、扫描设备、类型和日期范围缩小结果范围。</p>
          </div>
          <span class="pmr-badge">{{ statisticsListData.total || 0 }} 条记录</span>
        </div>
      </template>

      <div class="filter-grid">
        <el-input
          v-model="listSearchKeyword"
          placeholder="搜索病案号或扫描设备"
          clearable
          class="filter-item keyword"
          @keyup.enter="handleListSearch"
        />

        <el-select
          v-model="listSearchType"
          placeholder="全部类型"
          clearable
          class="filter-item type"
          @change="handleListSearch"
        >
          <el-option
            v-for="item in statisticsTypeOptions"
            :key="item"
            :label="item"
            :value="item"
          />
        </el-select>

        <el-date-picker
          v-model="listSearchDateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          class="filter-item date"
        />

        <el-select v-model="sortKey" class="filter-item sort" @change="handleSortChange">
          <el-option
            v-for="item in sortOptions"
            :key="item.key"
            :label="item.label"
            :value="item.key"
          />
        </el-select>

        <div class="filter-actions">
          <el-button type="primary" :icon="Search" @click="handleListSearch">筛选</el-button>
          <el-button :icon="Delete" @click="resetListSearch">重置</el-button>
        </div>
      </div>
    </el-card>


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
          <div class="folder-tab"></div>
          <div class="folder-top">
            <span class="folder-index">A{{ computeTableIndex(index) }}</span>
            <el-tag size="small" :type="getTypeTagType(item.type)">
              {{ item.type || '未分类' }}
            </el-tag>
          </div>

          <h4 class="folder-title">{{ item.bah || '未命名病案' }}</h4>
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
            <el-button text type="primary" @click.stop="selectArchive(item, index)">
              查看详情
            </el-button>
          </div>
        </article>
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
          class="custom-pagination"
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { DataBoard, Delete, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getStatisticsList, getStatisticsSummary } from '@/services/api/index.js'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const summaryData = ref({ byType: [] })
const statisticsListData = ref({
  total: 0,
  size: 100,
  totalPages: 0,
  page: 1,
  list: []
})
const currentPage = ref(1)
const pageSize = ref(18)
const listSearchKeyword = ref('')
const listSearchType = ref('')
const listSearchDateRange = ref([])
const sortKey = ref('date-desc')
const selectedArchive = ref(null)
const selectedArchiveKey = ref('')

const sortOptions = [
  { key: 'date-desc', label: '按日期倒序', prop: 'date', order: 'descending' },
  { key: 'bah-asc', label: '按病案号升序', prop: 'bah', order: 'ascending' },
  { key: 'pages-desc', label: '按页数倒序', prop: 'pages', order: 'descending' }
]

const statisticsTypeOptions = computed(() => {
  const source = summaryData.value?.byType || []
  return source.map((item) => item?.type).filter((item) => item && item !== 'NULL')
})

const currentSort = computed(() => sortOptions.find((item) => item.key === sortKey.value) || sortOptions[0])
const getBackendSortOrder = (order) => (order === 'ascending' ? 'asc' : 'desc')

const formatDate = (dateStr) => {
  if (!dateStr) return '无日期'
  return String(dateStr).replace(/\//g, '-')
}

const getTypeTagType = (type) => {
  const value = String(type || '').toLowerCase()
  if (value.includes('急')) return 'danger'
  if (value.includes('高')) return 'warning'
  if (value.includes('住')) return 'success'
  if (value.includes('门')) return 'primary'
  return 'info'
}

const pickToneClass = (seed) => {
  const palette = ['tone-blue', 'tone-green', 'tone-amber', 'tone-rose', 'tone-slate']
  const text = String(seed || '')
  let hash = 0
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 31 + text.charCodeAt(i)) >>> 0
  }
  return palette[hash % palette.length]
}

const getToneClass = (item, index = 0) => {
  const seed = `${item?.bah || ''}-${item?.cid || ''}-${index}`
  return pickToneClass(seed)
}

const buildArchiveKey = (item, index = 0) =>
  [item?.bah, item?.cid, item?.date, item?.type, item?.pages, item?.openerNo, index].join('|')

const computeTableIndex = (index) => (currentPage.value - 1) * pageSize.value + index + 1
const formatArchiveLabel = (item) => item?.bah || '未命名病案'

const selectedArchiveIndex = computed(() => {
  if (!selectedArchive.value) return 0
  const index = statisticsListData.value.list.findIndex(
    (item, i) => buildArchiveKey(item, i) === selectedArchiveKey.value
  )
  return index >= 0 ? index : 0
})

const loadSummary = async () => {
  try {
    const response = await getStatisticsSummary()
    if (response.data?.code === 200) {
      summaryData.value = response.data.data || {}
    } else if (response.data) {
      summaryData.value = response.data
    }
  } catch (err) {
    console.error('加载统计摘要失败:', err)
  }
}

const loadStatisticsList = async () => {
  loading.value = true
  error.value = ''
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value,
      sortBy: currentSort.value.prop || 'date',
      sortOrder: getBackendSortOrder(currentSort.value.order)
    }

    if (listSearchKeyword.value.trim()) params.keyword = listSearchKeyword.value.trim()
    if (listSearchType.value) params.type = listSearchType.value
    if (Array.isArray(listSearchDateRange.value) && listSearchDateRange.value.length === 2) {
      params.startDate = listSearchDateRange.value[0]
      params.endDate = listSearchDateRange.value[1]
    }

    const response = await getStatisticsList(params)
    if (response.data?.code === 200) {
      statisticsListData.value = response.data.data || {}
    } else if (response.data) {
      statisticsListData.value = response.data
    }

    if (Array.isArray(statisticsListData.value.list)) {
      statisticsListData.value.list = statisticsListData.value.list.filter((item) => item !== null)
    } else {
      statisticsListData.value.list = []
    }

    const firstItem = statisticsListData.value.list[0] || null
    selectedArchive.value = firstItem
    selectedArchiveKey.value = firstItem ? buildArchiveKey(firstItem, 0) : ''
  } catch (err) {
    console.error('加载病案明细失败:', err)
    error.value = err?.message || '加载病案明细失败'
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

const refreshAll = async () => {
  await loadSummary()
  await loadStatisticsList()
}

const handleListSearch = async () => {
  currentPage.value = 1
  await loadStatisticsList()
}

const resetListSearch = async () => {
  listSearchKeyword.value = ''
  listSearchType.value = ''
  listSearchDateRange.value = []
  sortKey.value = 'date-desc'
  currentPage.value = 1
  await loadStatisticsList()
}

const handleSortChange = async () => {
  currentPage.value = 1
  await loadStatisticsList()
}

const handleSizeChange = async (newSize) => {
  pageSize.value = newSize
  currentPage.value = 1
  await loadStatisticsList()
}

const handleCurrentChange = async (newPage) => {
  currentPage.value = newPage
  await loadStatisticsList()
}

const selectArchive = (item, index = 0) => {
  selectedArchive.value = item
  selectedArchiveKey.value = buildArchiveKey(item, index)
}

const goBackToStatistics = () => {
  router.push('/admin/statistics')
}

onMounted(async () => {
  await refreshAll()
})
</script>

<style scoped>
.statistics-detail-view {
  display: grid;
  gap: 20px;
}

.archive-hero {
  padding-bottom: 6px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-card {
  padding: 20px;
  border-radius: 22px;
  border: 1px solid rgba(195, 197, 215, 0.18);
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.92), rgba(244, 248, 255, 0.9));
  box-shadow: 0 12px 30px rgba(24, 65, 134, 0.06);
}

.summary-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--pmr-color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.summary-value {
  margin-top: 10px;
  font-size: 24px;
  font-weight: 800;
  color: var(--pmr-color-text-primary);
  word-break: break-all;
}

.summary-note {
  margin-top: 8px;
  font-size: 13px;
  color: var(--pmr-color-text-secondary);
}

.archive-alert {
  margin-top: -4px;
}

.archive-filter-panel,
.archive-focus-panel {
  border-radius: 24px;
}

.filter-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) 180px 240px 180px auto;
  gap: 12px;
  align-items: center;
}

.filter-item {
  width: 100%;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.focus-shell {
  display: grid;
  gap: 18px;
}

.detail-cover {
  display: grid;
  gap: 12px;
  padding: 20px;
  border-radius: 22px;
  color: #fff;
  background: linear-gradient(160deg, #1d4ed8 0%, #003fb1 100%);
  box-shadow: 0 16px 36px rgba(0, 63, 177, 0.22);
}

.detail-cover.tone-green {
  background: linear-gradient(160deg, #0f766e 0%, #115e59 100%);
}

.detail-cover.tone-amber {
  background: linear-gradient(160deg, #c97b18 0%, #a65b0f 100%);
}

.detail-cover.tone-rose {
  background: linear-gradient(160deg, #be185d 0%, #9d174d 100%);
}

.detail-cover.tone-slate {
  background: linear-gradient(160deg, #475569 0%, #334155 100%);
}

.detail-cover-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.cover-mark {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.18em;
  opacity: 0.9;
}

.cover-case {
  font-size: 20px;
  font-weight: 800;
  line-height: 1.35;
}

.cover-note {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  opacity: 0.9;
}

.detail-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.detail-list div,
.folder-meta-grid div {
  display: grid;
  gap: 4px;
}

.detail-list dt,
.folder-meta-grid dt {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #80879a;
}

.detail-list dd,
.folder-meta-grid dd {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #24324b;
  word-break: break-word;
}

.detail-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.archive-shelf {
  display: grid;
  gap: 16px;
}

.archive-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.archive-folder-card {
  position: relative;
  max-width: 288px;
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
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.archive-folder-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 20px 40px rgba(24, 65, 134, 0.12);
}

.archive-folder-card.is-selected {
  transform: translateY(-2px);
  box-shadow: 0 24px 48px rgba(0, 63, 177, 0.18);
}

.archive-folder-card::before {
  content: '';
  position: absolute;
  left: 18px;
  top: -1px;
  width: 112px;
  height: 18px;
  border-radius: 0 0 14px 14px;
  background: rgba(255, 255, 255, 0.28);
}

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
  border-top-color: var(--folder-accent);
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.95), rgba(247, 250, 255, 0.92)),
    linear-gradient(180deg, var(--folder-bg), rgba(255, 255, 255, 0) 55%);
}

.folder-tab {
  position: absolute;
  top: -1px;
  left: 18px;
  width: 108px;
  height: 20px;
  border-radius: 0 0 16px 16px;
  background: linear-gradient(135deg, var(--folder-accent), color-mix(in srgb, var(--folder-accent) 70%, white));
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
  color: var(--pmr-color-text-secondary);
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
  color: var(--folder-accent);
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.archive-loading {
  padding: 10px 4px 6px;
}

.mb-16 {
  margin-bottom: 16px;
}

@media (max-width: 1220px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .summary-grid,
  .filter-grid,
  .folder-meta-grid,
  .detail-list {
    grid-template-columns: 1fr;
  }

  .folder-footer,
  .detail-actions,
  .filter-actions {
    width: 100%;
  }

  .filter-actions,
  .pagination-wrapper {
    justify-content: stretch;
  }

  .filter-actions :deep(.el-button),
  .detail-actions :deep(.el-button) {
    width: 100%;
  }
}
</style>
