<script setup lang="ts">
import type { StatisticsRecord } from '@/api/types'
import type { MrrBarSeries, MrrLineSeries } from '@/types/chart'
import { ElMessage } from 'element-plus'
import {
  CalendarDays as Calendar,
  Download,
  RefreshCw as Refresh,
} from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import {
  exportStatisticsCsv,
  getStatisticsDateSummary,
  getStatisticsList,
  getStatisticsSummary,
} from '@/api/modules/statistics'
import { MrrChartCard, MrrDualAxisChart } from '@/components/charts'
import MrrDataTablePanel from '@/components/MrrDataTablePanel/index.vue'
import MrrFilterActionButton from '@/components/MrrFilterActionButton/index.vue'
import MrrFilterBar from '@/components/MrrFilterBar/index.vue'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import { rangePickerPopperOptions } from '@/ui/provider/picker-options'

defineOptions({ name: 'StatisticsPage' })

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

interface StatisticsFilterParams {
  keyword?: string
  type?: string
  startDate?: string
  endDate?: string
}

const overviewLoading = ref(false)
const listLoading = ref(false)
const listError = ref('')
const exportLoading = ref(false)
const summaryData = ref<SummaryData>({
  total: { totalRecords: undefined, totalPages: undefined },
  uniqueBAHCount: undefined,
  byType: [],
})
const dateSummaryData = ref<DateStatItem[]>([])

/* 空库/空时间范围内数值指标全零时收纳指标区，为图表让出首屏（N5）。 */
const hasMetricActivity = computed(() => [
  summaryData.value.total?.totalRecords,
  summaryData.value.total?.totalPages,
  summaryData.value.uniqueBAHCount,
].some(value => Number(value ?? 0) > 0))
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
const listSearchYear = ref('')
const listSearchDateRange = ref<string[]>([])
const tableSort = ref({ prop: 'date', order: 'descending' })
let listRequestId = 0
const loadedFilters = ref<StatisticsFilterParams>()
const canExportList = computed(() => {
  const loaded = loadedFilters.value
  const draft = buildStatisticsFilters()
  return Boolean(!listLoading.value && loaded
    && loaded.keyword === draft.keyword && loaded.type === draft.type
    && loaded.startDate === draft.startDate && loaded.endDate === draft.endDate)
})

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
const yearOptions = computed(() => {
  const years = new Set<string>()
  for (const item of sortedDateData.value) {
    const year = extractYear(item.date)
    if (year) {
      years.add(year)
    }
  }

  if (!years.size) {
    years.add(String(new Date().getFullYear()))
  }

  return [...years].sort((left, right) => Number(right) - Number(left))
})

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

function extractYear(dateStr?: string) {
  if (!dateStr) {
    return ''
  }
  return dateStr.trim().match(/^(\d{4})[-/.]/)?.[1] ?? ''
}

function getBackendSortOrder(order: string) {
  return order === 'ascending' ? 'asc' : 'desc'
}

function buildStatisticsFilters(): StatisticsFilterParams {
  const params: StatisticsFilterParams = {}
  if (listSearchKeyword.value.trim()) {
    params.keyword = listSearchKeyword.value.trim()
  }
  if (listSearchType.value) {
    params.type = listSearchType.value
  }
  if (listSearchDateRange.value.length === 2) {
    params.startDate = listSearchDateRange.value[0]
    params.endDate = listSearchDateRange.value[1]
  }
  return params
}

function syncYearFromDateRange() {
  if (listSearchDateRange.value.length !== 2) {
    listSearchYear.value = ''
    return
  }

  const [startDate, endDate] = listSearchDateRange.value
  const year = extractYear(startDate)
  listSearchYear.value = year
    && startDate === `${year}-01-01`
    && endDate === `${year}-12-31`
    ? year
    : ''
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
  const requestId = ++listRequestId
  const filters = buildStatisticsFilters()
  loadedFilters.value = undefined
  listLoading.value = true
  listError.value = ''
  try {
    const { prop, order } = tableSort.value
    const res = await getStatisticsList({
      page: currentPage.value,
      size: pageSize.value,
      sortBy: prop || 'date',
      sortOrder: getBackendSortOrder(order),
      ...filters,
    })
    if (requestId !== listRequestId) {
      return
    }
    const raw = res.data ?? { list: [], total: 0, page: 1, size: 20 }
    statisticsListData.value = {
      ...raw,
      totalPages: raw.totalPages ?? 0,
      list: Array.isArray(raw.list)
        ? raw.list.filter((item): item is StatisticsRecord => item != null)
        : [],
    }
    loadedFilters.value = filters
  }
  catch (error: unknown) {
    if (requestId === listRequestId) {
      const message = error instanceof Error ? error.message : '未知错误'
      listError.value = `加载病案列表失败：${message}`
    }
  }
  finally {
    if (requestId === listRequestId) {
      listLoading.value = false
    }
  }
}

async function refreshAll() {
  await Promise.all([loadOverview(), loadStatisticsList()])
}

function handleListSearch() {
  currentPage.value = 1
  loadStatisticsList()
}

function handleYearChange(year: string) {
  listSearchDateRange.value = year
    ? [`${year}-01-01`, `${year}-12-31`]
    : []
  handleListSearch()
}

function handleDateRangeChange() {
  syncYearFromDateRange()
  handleListSearch()
}

function resetListSearch() {
  listSearchKeyword.value = ''
  listSearchType.value = ''
  listSearchYear.value = ''
  listSearchDateRange.value = []
  currentPage.value = 1
  loadStatisticsList()
}

function handleListSizeChange() {
  currentPage.value = 1
  loadStatisticsList()
}

function handleSortChange({ prop, order }: { prop: string | null, order: string | null }) {
  tableSort.value = { prop: prop || 'date', order: order || 'descending' }
  loadStatisticsList()
}

async function handleExportTable() {
  const filters = loadedFilters.value
  if (!filters || !canExportList.value || exportLoading.value) {
    return
  }
  const scope = listSearchYear.value || 'all'
  exportLoading.value = true
  try {
    const response = await exportStatisticsCsv(filters)
    const payload: unknown = response instanceof Blob ? response : response.data
    if (!(payload instanceof Blob)) {
      throw new TypeError('导出接口未返回有效文件')
    }

    const blob = payload.type
      ? payload
      : new Blob([payload], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    const date = new Date().toISOString().slice(0, 10)
    link.href = url
    link.download = `statistics-${scope}-${date}.csv`
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 0)
    ElMessage.success('表格导出成功')
  }
  catch (error: unknown) {
    const message = error instanceof Error ? error.message : '未知错误'
    ElMessage.error(`导出失败：${message}`)
  }
  finally {
    exportLoading.value = false
  }
}

onMounted(refreshAll)
</script>

<template>
  <MrrPageShell class="statistics-page">
    <MrrPageHeader
      eyebrow="Records Statistics"
      title="病案扫描数据统计"
      description="汇总病案扫描规模、每日变化与病案明细，在一个页面完成统计查看和数据检索。"
    >
      <template #actions>
        <el-button
          :icon="Refresh"
          :loading="overviewLoading || listLoading"
          @click="refreshAll"
        >
          刷新数据
        </el-button>
      </template>
    </MrrPageHeader>

    <section v-if="hasMetricActivity" class="mrr-metric-grid" aria-label="核心统计指标">
      <MrrMetricCard
        label="总记录数"
        icon="i-lucide:layout-grid"
        :value="formatNumber(summaryData.total?.totalRecords ?? 0)"
      />
      <MrrMetricCard
        label="总扫描页数"
        icon="i-lucide:ticket"
        tone="rose"
        :value="formatNumber(summaryData.total?.totalPages ?? 0)"
      />
      <MrrMetricCard
        label="扫描病案数"
        icon="i-lucide:file-text"
        tone="green"
        :value="formatNumber(summaryData.uniqueBAHCount ?? 0)"
      />
      <MrrMetricCard
        label="统计时间范围"
        icon="i-lucide:chart-no-axes-combined"
        tone="amber"
        compact-value
        :value="`${dateRange.start} — ${dateRange.end}`"
      />
    </section>

    <MrrChartCard
      title="扫描趋势"
      description="柱形展示扫描页数，折线展示扫描记录数；默认展示近 90 日，可在图表内滚动鼠标滚轮调整时间范围"
      :loading="overviewLoading"
      :empty="!sortedDateData.length"
      empty-description="暂无扫描趋势数据"
    >
      <template #actions>
        <el-tag size="small" type="success">
          共 {{ sortedDateData.length }} 个统计日
        </el-tag>
      </template>
      <template #summary>
        <div class="mrr-stat-strip mrr-stat-strip--3" aria-label="扫描趋势摘要">
          <div class="mrr-stat-tile">
            <span class="mrr-stat-tile__label">区间记录</span>
            <strong class="mrr-stat-tile__value">{{ formatNumber(trendRecordTotal) }}</strong>
          </div>
          <div class="mrr-stat-tile">
            <span class="mrr-stat-tile__label">区间页数</span>
            <strong class="mrr-stat-tile__value">{{ formatNumber(trendPageTotal) }}</strong>
          </div>
          <div class="mrr-stat-tile">
            <span class="mrr-stat-tile__label">日均页数</span>
            <strong class="mrr-stat-tile__value">{{ formatNumber(averageDailyPages) }}</strong>
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
      class="list-card"
      title="病案明细列表"
      icon="i-lucide:file-text"
      :count="statisticsListData.total ?? 0"
    >
      <template #actions>
        <el-button
          type="success"
          plain
          :icon="Download"
          :loading="exportLoading"
          :disabled="!canExportList"
          :title="canExportList ? '导出当前筛选结果，单次最多 100,000 条' : '查询当前筛选条件后可导出'"
          @click="handleExportTable"
        >
          导出表格
        </el-button>
      </template>

      <template #filters>
        <MrrFilterBar class="list-search-bar" embedded :focus-ring="false">
          <el-input
            v-model="listSearchKeyword"
            placeholder="搜索病案号 / 上架号 / 操作员"
            clearable
            class="search-keyword"
            @keyup.enter="handleListSearch"
          />
          <el-select
            v-model="listSearchType"
            placeholder="全部类型"
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
          <el-select
            v-model="listSearchYear"
            placeholder="全部年份"
            clearable
            class="search-year"
            @change="handleYearChange"
          >
            <template #prefix>
              <el-icon><Calendar /></el-icon>
            </template>
            <el-option
              v-for="year in yearOptions"
              :key="year"
              :label="`${year} 年`"
              :value="year"
            />
          </el-select>
          <el-date-picker
            v-model="listSearchDateRange"
            type="daterange"
            :popper-options="rangePickerPopperOptions"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            class="search-date"
            @change="handleDateRangeChange"
          />
          <template #actions>
            <MrrFilterActionButton action="search" :pending="listLoading" :busy="listLoading" @click="handleListSearch">
              查询
            </MrrFilterActionButton>
            <MrrFilterActionButton action="reset" :pending="listLoading" :busy="listLoading" @click="resetListSearch">
              重置
            </MrrFilterActionButton>
          </template>
        </MrrFilterBar>
      </template>

      <AppError v-if="listError" :message="listError" @retry="loadStatisticsList" />
      <el-table
        v-show="!listError"
        v-loading="listLoading"
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

      <template v-if="!listError" #pagination>
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
/*
 * 页面壳层与页头由 MrrPageShell / MrrPageHeader 统一提供；底部留白交给布局工作区。
 * 明细列表使用 MrrDataTablePanel + MrrFilterBar（与用户、记录等列表页同一结构）：
 * 此前自写的灰底搜索条、居中分页与 15px 卡头，与其他列表页的留白、对齐和分页位置都不一致。
 */

/* 字段宽度：关键词与日期可伸展，类型 / 年份固定宽，与筛选栏 embedded 的拉伸上限配合。 */
.search-keyword {
  flex: 1 1 220px;
  min-width: 180px;
}

.search-type,
.search-year {
  flex: 0 1 180px;
  width: 180px;
}

.search-date {
  flex: 1 1 280px;
  min-width: 240px;
}

@media (max-width: 760px) {
  .search-keyword,
  .search-type,
  .search-year,
  .search-date {
    flex: 1 1 100%;
    width: 100%;
    min-width: 0;
  }
}
</style>
