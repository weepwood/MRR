<script setup lang="ts">
import type { StatisticsRecord } from '@/api/types'
import type { MrrBarSeries, MrrLineSeries } from '@/types/chart'
import {
  Document,
  Grid,
  Tickets,
  TrendCharts,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { MrrChartCard, MrrDualAxisChart } from '@/components/charts'
import {
  getStatisticsDateSummary,
  getStatisticsList,
  getStatisticsSummary,
} from '@/api/modules/statistics'

defineOptions({ name: 'RecordsStatisticsPage' })

interface SummaryData {
  total?: { totalRecords?: number, totalPages?: number }
  uniqueBAHCount?: number
  byType?: Array<{ type?: string, recordCount?: number, totalPages?: number }>
}

interface DateStatItem {
  date: string
  recordCount?: number
  pages?: number
}

interface StatisticsListResult {
  total: number
  size: number
  totalPages: number
  page: number
  list: StatisticsRecord[]
}

const loading = ref(false)
const summaryData = ref<SummaryData>({
  total: { totalRecords: undefined, totalPages: undefined },
  uniqueBAHCount: undefined,
  byType: [],
})
const dateSummaryData = ref<DateStatItem[]>([])
const statisticsListData = ref<StatisticsListResult>({
  total: 0,
  size: 100,
  totalPages: 0,
  page: 1,
  list: [],
})

const currentPage = ref(1)
const pageSize = ref(100)
const listSearchKeyword = ref('')
const listSearchType = ref('')
const listSearchDateRange = ref<string[]>([])
const tableSort = ref({ prop: 'date', order: 'descending' })

const sortedDateData = computed<DateStatItem[]>(() => {
  const valid = (dateSummaryData.value ?? []).filter(
    item => item && item.date && item.date.trim() !== '' && item.date !== 'NULL',
  )
  return [...valid].sort((left, right) => {
    const leftDate = new Date(left.date.replace(/\//g, '-'))
    const rightDate = new Date(right.date.replace(/\//g, '-'))
    return leftDate.getTime() - rightDate.getTime()
  })
})

const dateRange = computed(() => {
  if (!sortedDateData.value.length) {
    return { start: '-', end: '-' }
  }
  const lastItem = sortedDateData.value.at(-1)
  return {
    start: formatDate(sortedDateData.value[0]?.date),
    end: formatDate(lastItem?.date),
  }
})

const cumulativeRecords = computed(() => {
  let total = 0
  return sortedDateData.value.map((item) => {
    total += item.recordCount ?? 0
    return total
  })
})

const trendCategories = computed(() => sortedDateData.value.map(item => formatDateShort(item.date)))
const trendBars = computed<MrrBarSeries[]>(() => [{
  name: '每日记录数',
  data: sortedDateData.value.map(item => item.recordCount ?? 0),
}])
const trendLines = computed<MrrLineSeries[]>(() => [{
  name: '累计记录数',
  data: cumulativeRecords.value,
  smooth: true,
}])

const dailyRecordTotal = computed(() => cumulativeRecords.value.at(-1) ?? 0)
const dailyRecordAverage = computed(() => sortedDateData.value.length
  ? dailyRecordTotal.value / sortedDateData.value.length
  : 0)
const dailyRecordPeak = computed(() => Math.max(
  0,
  ...sortedDateData.value.map(item => item.recordCount ?? 0),
))
const typeOptions = computed(() => (summaryData.value.byType ?? [])
  .map(item => String(item.type || '').trim())
  .filter(Boolean))

function formatDate(dateStr?: string) {
  if (!dateStr) {
    return '无日期'
  }
  return dateStr.replace(/\//g, '-')
}

function formatDateShort(dateStr?: string) {
  if (!dateStr) {
    return ''
  }
  const normalized = dateStr.replace(/\//g, '-')
  const parts = normalized.split('-')
  return parts.length >= 3 ? `${parts.at(-2)}/${parts.at(-1)}` : normalized
}

function formatNumber(value: number, maximumFractionDigits = 0) {
  return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits })
}

function getBackendSortOrder(order: string) {
  return order === 'ascending' ? 'asc' : 'desc'
}

async function loadSummary() {
  try {
    const res = await getStatisticsSummary()
    summaryData.value = res.data ?? { byType: [], total: {} }
  }
  catch (error: unknown) {
    const message = error instanceof Error ? error.message : '未知错误'
    ElMessage.error(`加载统计概览失败：${message}`)
  }
}

async function loadDateSummary() {
  try {
    const res = await getStatisticsDateSummary()
    dateSummaryData.value = Array.isArray(res.data) ? res.data : []
  }
  catch (error: unknown) {
    const message = error instanceof Error ? error.message : '未知错误'
    ElMessage.error(`加载日期统计失败：${message}`)
  }
}

async function loadStatisticsList() {
  loading.value = true
  try {
    const { prop, order } = tableSort.value
    const params: Record<string, string | number> = {
      page: currentPage.value,
      size: pageSize.value,
      sortBy: prop || 'date',
      sortOrder: getBackendSortOrder(order),
    }
    if (listSearchKeyword.value.trim()) {
      params.keyword = listSearchKeyword.value.trim()
    }
    if (listSearchType.value) {
      params.type = listSearchType.value
    }
    if (listSearchDateRange.value.length === 2) {
      params.startDate = listSearchDateRange.value[0] ?? ''
      params.endDate = listSearchDateRange.value[1] ?? ''
    }

    const res = await getStatisticsList(params as unknown as {
      page: number
      size: number
      keyword?: string
      type?: string
      startDate?: string
      endDate?: string
      sortBy?: string
      sortOrder?: string
    })
    const raw = res.data ?? { list: [], total: 0, page: 1, size: 20 }
    statisticsListData.value = {
      ...raw,
      totalPages: raw.totalPages ?? 0,
      list: Array.isArray(raw.list)
        ? raw.list.filter((item): item is StatisticsRecord => item != null)
        : [],
    }
  }
  catch (error: unknown) {
    const message = error instanceof Error ? error.message : '未知错误'
    ElMessage.error(`加载病案列表失败：${message}`)
  }
  finally {
    loading.value = false
  }
}

function handleListSearch() {
  currentPage.value = 1
  loadStatisticsList()
}

function resetListSearch() {
  listSearchKeyword.value = ''
  listSearchType.value = ''
  listSearchDateRange.value = []
  currentPage.value = 1
  loadStatisticsList()
}

function handleListSizeChange() {
  currentPage.value = 1
  loadStatisticsList()
}

function handleSortChange({ prop, order }: { prop: string, order: string | null }) {
  tableSort.value = { prop: prop || 'date', order: order || 'descending' }
  loadStatisticsList()
}

onMounted(() => {
  loadSummary()
  loadDateSummary()
  loadStatisticsList()
})
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">
          Records Statistics
        </p>
        <h2>病案扫描数据统计</h2>
        <p class="subtitle">
          查看扫描总量、每日趋势与病案明细列表。
        </p>
      </div>
    </div>

    <section class="mrr-metric-grid">
      <el-card shadow="never" class="mrr-metric-card">
        <div class="mrr-metric-card__icon">
          <el-icon><Grid /></el-icon>
        </div>
        <div class="mrr-metric-card__body">
          <div class="mrr-metric-card__label">
            总记录数
          </div>
          <div class="mrr-metric-card__value">
            {{ formatNumber(summaryData.total?.totalRecords ?? 0) }}
          </div>
        </div>
      </el-card>
      <el-card shadow="never" class="mrr-metric-card mrr-metric-card--rose">
        <div class="mrr-metric-card__icon">
          <el-icon><Tickets /></el-icon>
        </div>
        <div class="mrr-metric-card__body">
          <div class="mrr-metric-card__label">
            项目期间总页数
          </div>
          <div class="mrr-metric-card__value">
            {{ formatNumber(summaryData.total?.totalPages ?? 0) }}
          </div>
        </div>
      </el-card>
      <el-card shadow="never" class="mrr-metric-card mrr-metric-card--green">
        <div class="mrr-metric-card__icon">
          <el-icon><Document /></el-icon>
        </div>
        <div class="mrr-metric-card__body">
          <div class="mrr-metric-card__label">
            项目期间扫描病案数
          </div>
          <div class="mrr-metric-card__value">
            {{ formatNumber(summaryData.uniqueBAHCount ?? 0) }}
          </div>
        </div>
      </el-card>
      <el-card shadow="never" class="mrr-metric-card mrr-metric-card--amber">
        <div class="mrr-metric-card__icon">
          <el-icon><TrendCharts /></el-icon>
        </div>
        <div class="mrr-metric-card__body">
          <div class="mrr-metric-card__label">
            统计时间范围
          </div>
          <div class="mrr-metric-card__value mrr-metric-card__value--compact">
            {{ dateRange.start }} — {{ dateRange.end }}
          </div>
        </div>
      </el-card>
    </section>

    <MrrChartCard
      title="每日扫描记录"
      description="柱形展示每日新增记录，折线展示累计记录变化"
      :empty="!sortedDateData.length"
      empty-description="暂无每日扫描趋势数据"
    >
      <template #actions>
        <el-tag size="small" type="success">
          共 {{ sortedDateData.length }} 天
        </el-tag>
      </template>
      <template #summary>
        <div class="trend-summary" aria-label="每日扫描记录摘要">
          <div>
            <span>区间累计</span>
            <strong>{{ formatNumber(dailyRecordTotal) }}</strong>
          </div>
          <div>
            <span>日均记录</span>
            <strong>{{ formatNumber(dailyRecordAverage, 1) }}</strong>
          </div>
          <div>
            <span>单日峰值</span>
            <strong>{{ formatNumber(dailyRecordPeak) }}</strong>
          </div>
        </div>
      </template>
      <MrrDualAxisChart
        :categories="trendCategories"
        :bars="trendBars"
        :lines="trendLines"
        left-axis-name="每日记录"
        right-axis-name="累计记录"
        left-unit="条"
        right-unit="条"
        :height="340"
      />
    </MrrChartCard>

    <el-card shadow="never" class="list-card">
      <template #header>
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>病案明细列表</span>
          <el-tag size="small" type="info">
            共 {{ statisticsListData.total ?? 0 }} 条
          </el-tag>
        </div>
      </template>

      <div class="list-search-bar">
        <el-input
          v-model="listSearchKeyword"
          placeholder="搜索病案号 / 上架号 / 操作员"
          clearable
          class="search-keyword"
          @keyup.enter="handleListSearch"
        />
        <el-select
          v-model="listSearchType"
          placeholder="类型筛选"
          clearable
          class="search-type"
          @change="handleListSearch"
        >
          <el-option label="全部" value="" />
          <el-option
            v-for="item in typeOptions"
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
          class="search-date"
          @change="handleListSearch"
        />
        <el-button type="primary" @click="handleListSearch">
          查询
        </el-button>
        <el-button @click="resetListSearch">
          重置
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="statisticsListData.list"
        stripe
        empty-text="暂无病案明细数据"
        @sort-change="handleSortChange"
      >
        <el-table-column prop="bah" label="病案号" min-width="130" sortable="custom" show-overflow-tooltip />
        <el-table-column prop="sjh" label="上架号" min-width="130" show-overflow-tooltip />
        <el-table-column prop="date" label="日期" width="120" sortable="custom" />
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column prop="pages" label="页数" width="80" sortable="custom" />
        <el-table-column prop="openerNo" label="操作员" width="100" />
        <el-table-column prop="cid" label="CID" min-width="120" show-overflow-tooltip />
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="statisticsListData.total ?? 0"
          :page-sizes="[20, 50, 100, 200]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadStatisticsList"
          @size-change="handleListSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-shell {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 4px;
}

.eyebrow {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary);
  text-transform: uppercase;
  letter-spacing: 0.1em;
  opacity: 0.7;
}

.page-header h2 {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 800;
  color: var(--text-primary);
}

.subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.section-header {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
}

.section-header .el-icon {
  font-size: 18px;
  color: var(--el-color-primary);
}

.trend-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.trend-summary > div {
  padding: 12px 14px;
  background: color-mix(in srgb, var(--surface-alt) 60%, var(--surface));
  border: 1px solid var(--divider);
  border-radius: 12px;
}

.trend-summary span,
.trend-summary strong {
  display: block;
}

.trend-summary span {
  font-size: 11px;
  color: var(--text-secondary);
}

.trend-summary strong {
  margin-top: 6px;
  font-size: 18px;
  font-variant-numeric: tabular-nums;
}

.list-search-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 16px;
  margin-bottom: 16px;
  background: var(--surface-muted);
  border: 1px solid rgb(0 0 0 / 4%);
  border-radius: 12px;
}

.search-keyword {
  flex: 1 1 200px;
  min-width: 160px;
}

.search-type {
  flex: 0 0 150px;
}

.search-date {
  flex: 1 1 260px;
  min-width: 220px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding-top: 20px;
  margin-top: 12px;
  border-top: 1px solid rgb(0 0 0 / 5%);
}

@media (width <= 600px) {
  .trend-summary {
    grid-template-columns: 1fr;
  }

  .search-keyword,
  .search-type,
  .search-date {
    flex: 1 1 100%;
  }
}
</style>
