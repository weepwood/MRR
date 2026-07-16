<script setup lang="ts">
import type { StatisticsRecord } from '@/api/types'
import type { MrrBarSeries, MrrLineSeries } from '@/types/chart'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import {
  getStatisticsDateSummary,
  getStatisticsList,
  getStatisticsSummary,
} from '@/api/modules/statistics'
import { MrrChartCard, MrrDualAxisChart } from '@/components/charts'
import MrrDataTablePanel from '@/components/MrrDataTablePanel/index.vue'
import MrrFilterBar from '@/components/MrrFilterBar/index.vue'
import MrrMetricCard from '@/components/MrrMetricCard/index.vue'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'

defineOptions({ name: 'StatisticsPage' })

type MetricTone = 'blue' | 'green' | 'violet' | 'slate' | 'teal'

interface SummaryData {
  total?: { totalRecords?: number, totalPages?: number }
  uniqueBAHCount?: number
  byType?: Array<{ type?: string, recordCount?: number, totalPages?: number }>
}

interface DateStatItem {
  date: string
  recordCount?: number
  totalPages?: number
  pages?: number
}

interface StatisticsListResult {
  total: number
  size: number
  totalPages: number
  page: number
  list: StatisticsRecord[]
}

interface MetricItem {
  label: string
  value: string | number
  note: string
  tone: MetricTone
  icon: string
}

const overviewLoading = ref(false)
const listLoading = ref(false)
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
  return {
    start: formatDate(sortedDateData.value[0]?.date),
    end: formatDate(sortedDateData.value.at(-1)?.date),
  }
})

const trendCategories = computed(() => sortedDateData.value.map(item => formatDateShort(item.date)))
const trendBars = computed<MrrBarSeries[]>(() => [{
  name: '扫描页数',
  data: sortedDateData.value.map(item => Number(item.totalPages ?? item.pages ?? 0)),
}])
const trendLines = computed<MrrLineSeries[]>(() => [{
  name: '扫描记录数',
  data: sortedDateData.value.map(item => Number(item.recordCount ?? 0)),
  smooth: true,
}])

const trendRecordTotal = computed(() => sortedDateData.value.reduce(
  (total, item) => total + Number(item.recordCount ?? 0),
  0,
))
const trendPageTotal = computed(() => sortedDateData.value.reduce(
  (total, item) => total + Number(item.totalPages ?? item.pages ?? 0),
  0,
))
const averageDailyPages = computed(() => sortedDateData.value.length
  ? Math.round(trendPageTotal.value / sortedDateData.value.length)
  : 0)
const typeOptions = computed(() => Array.from(new Set(
  (summaryData.value.byType ?? [])
    .map(item => String(item.type || '').trim())
    .filter(Boolean),
)))

const summaryCards = computed<MetricItem[]>(() => [
  {
    label: '扫描记录总数',
    value: formatNumber(summaryData.value.total?.totalRecords ?? 0),
    note: '系统累计登记的扫描记录',
    tone: 'blue',
    icon: 'i-ant-design:database-outlined',
  },
  {
    label: '扫描总页数',
    value: formatNumber(summaryData.value.total?.totalPages ?? 0),
    note: '全部扫描记录包含的影像页数',
    tone: 'violet',
    icon: 'i-ant-design:file-image-outlined',
  },
  {
    label: '扫描病案数',
    value: formatNumber(summaryData.value.uniqueBAHCount ?? 0),
    note: '按病案号去重后的病案数量',
    tone: 'teal',
    icon: 'i-ant-design:folder-open-outlined',
  },
  {
    label: '日均扫描页数',
    value: formatNumber(averageDailyPages.value),
    note: `基于 ${sortedDateData.value.length} 个有效统计日`,
    tone: 'slate',
    icon: 'i-ant-design:rise-outlined',
  },
])

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

async function loadOverview() {
  overviewLoading.value = true
  try {
    const [summaryRes, dateRes] = await Promise.all([
      getStatisticsSummary(),
      getStatisticsDateSummary(),
    ])
    summaryData.value = summaryRes.data ?? { byType: [], total: {} }
    dateSummaryData.value = Array.isArray(dateRes.data) ? dateRes.data : []
  }
  catch (error: unknown) {
    const message = error instanceof Error ? error.message : '未知错误'
    ElMessage.error(`加载统计概览失败：${message}`)
  }
  finally {
    overviewLoading.value = false
  }
}

async function loadStatisticsList() {
  listLoading.value = true
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
    listLoading.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadOverview(), loadStatisticsList()])
}

function handleListSearch() {
  currentPage.value = 1
  void loadStatisticsList()
}

function resetListSearch() {
  listSearchKeyword.value = ''
  listSearchType.value = ''
  listSearchDateRange.value = []
  currentPage.value = 1
  void loadStatisticsList()
}

function handleListSizeChange() {
  currentPage.value = 1
  void loadStatisticsList()
}

function handleSortChange({ prop, order }: { prop: string, order: string | null }) {
  tableSort.value = { prop: prop || 'date', order: order || 'descending' }
  void loadStatisticsList()
}

onMounted(refreshAll)
</script>

<template>
  <MrrPageShell width="fluid">
    <MrrPageHeader
      title="病案扫描数据统计"
      description="汇总扫描规模、每日变化与病案明细，在同一页面完成趋势分析和数据检索。"
      icon="i-ant-design:area-chart-outlined"
    >
      <template #actions>
        <el-button :loading="overviewLoading || listLoading" @click="refreshAll">
          <FaIcon name="i-ri:refresh-line" />
          刷新数据
        </el-button>
      </template>
    </MrrPageHeader>

    <section class="mrr-metric-grid" aria-label="核心统计指标">
      <MrrMetricCard
        v-for="item in summaryCards"
        :key="item.label"
        :label="item.label"
        :value="item.value"
        :note="item.note"
        :tone="item.tone"
        :icon="item.icon"
      />
    </section>

    <MrrChartCard
      title="扫描趋势"
      description="柱形展示扫描页数，折线展示扫描记录数；默认展示近 90 日，可在图表内滚动鼠标滚轮调整时间范围。"
      :loading="overviewLoading"
      :empty="!sortedDateData.length"
      empty-description="暂无扫描趋势数据"
    >
      <template #actions>
        <span class="trend-range">
          {{ dateRange.start }} 至 {{ dateRange.end }}
        </span>
      </template>
      <template #summary>
        <div class="trend-summary" aria-label="扫描趋势摘要">
          <div>
            <span>区间记录</span>
            <strong>{{ formatNumber(trendRecordTotal) }}</strong>
          </div>
          <div>
            <span>区间页数</span>
            <strong>{{ formatNumber(trendPageTotal) }}</strong>
          </div>
          <div>
            <span>有效统计日</span>
            <strong>{{ formatNumber(sortedDateData.length) }}</strong>
          </div>
        </div>
      </template>
      <MrrDualAxisChart
        :categories="trendCategories"
        :bars="trendBars"
        :lines="trendLines"
        :loading="overviewLoading"
        left-axis-name="扫描页数"
        right-axis-name="记录数"
        left-unit="页"
        right-unit="条"
        :initial-visible-count="90"
        :height="340"
      />
    </MrrChartCard>

    <MrrDataTablePanel
      title="病案统计明细"
      description="按病案号、上架号、操作员、类型和日期范围检索统计记录。"
      icon="i-ant-design:profile-outlined"
      :count="statisticsListData.total"
    >
      <template #filters>
        <MrrFilterBar variant="embedded">
          <el-input
            v-model="listSearchKeyword"
            class="statistics-filter statistics-filter--keyword"
            clearable
            aria-label="搜索病案号、上架号或操作员"
            placeholder="病案号 / 上架号 / 操作员"
            @keyup.enter="handleListSearch"
          />
          <el-select
            v-model="listSearchType"
            class="statistics-filter statistics-filter--type"
            clearable
            aria-label="按病案类型筛选"
            placeholder="全部类型"
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
            class="statistics-filter statistics-filter--date"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />

          <template #actions>
            <el-button type="primary" :loading="listLoading" @click="handleListSearch">
              <FaIcon name="i-ri:search-line" />
              查询
            </el-button>
            <el-button @click="resetListSearch">
              <FaIcon name="i-ri:restart-line" />
              重置
            </el-button>
          </template>
        </MrrFilterBar>
      </template>

      <el-table
        v-loading="listLoading"
        :data="statisticsListData.list"
        empty-text="暂无病案明细数据"
        @sort-change="handleSortChange"
      >
        <el-table-column prop="bah" label="病案号" min-width="130" sortable="custom" show-overflow-tooltip />
        <el-table-column prop="sjh" label="上架号" min-width="130" show-overflow-tooltip />
        <el-table-column prop="date" label="日期" width="120" sortable="custom" />
        <el-table-column prop="type" label="类型" min-width="120" />
        <el-table-column prop="pages" label="页数" width="80" align="right" sortable="custom" />
        <el-table-column prop="openerNo" label="操作员" width="110" />
        <el-table-column prop="cid" label="CID" min-width="140" show-overflow-tooltip />
      </el-table>

      <template #pagination>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="statisticsListData.total ?? 0"
          :page-sizes="[20, 50, 100, 200]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadStatisticsList"
          @size-change="handleListSizeChange"
        />
      </template>
    </MrrDataTablePanel>
  </MrrPageShell>
</template>

<style scoped>
.trend-range {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 3px 9px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--mrr-muted-foreground);
  background: var(--mrr-secondary);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-pill);
}

.trend-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--mrr-space-3);
}

.trend-summary > div {
  padding: 12px 14px;
  background: color-mix(in srgb, var(--mrr-muted) 32%, var(--mrr-card));
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-lg);
}

.trend-summary span,
.trend-summary strong {
  display: block;
}

.trend-summary span {
  font-size: 11px;
  color: var(--mrr-muted-foreground);
}

.trend-summary strong {
  margin-top: 6px;
  font-size: 18px;
  font-variant-numeric: tabular-nums;
}

.statistics-filter--keyword {
  flex: 1 1 240px;
  min-width: 200px;
}

.statistics-filter--type {
  width: 168px;
}

.statistics-filter--date {
  flex: 1 1 300px;
  min-width: 260px;
}

@media (width <= 600px) {
  .trend-summary {
    grid-template-columns: 1fr;
  }

  .statistics-filter {
    flex: 1 1 100%;
    width: 100%;
    min-width: 0;
  }
}
</style>
