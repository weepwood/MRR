<script setup lang="ts">
import type { StatisticsRecord } from '@/api/types'
import {
  Document,
  Grid,
  Tickets,
  TrendCharts,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  getDashboardData,
  getStatisticsDateSummary,
  getStatisticsList,
  getStatisticsSummary,
} from '@/api/modules/statistics'

defineOptions({ name: 'RecordsStatisticsPage' })

// ---------- types ----------
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

// ---------- state ----------
const loading = ref(false)
const chartContainerRef = ref<HTMLElement | null>(null)
const svgWidth = ref(800)

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

// ---------- list filters ----------
const currentPage = ref(1)
const pageSize = ref(100)
const listSearchKeyword = ref('')
const listSearchType = ref('')
const listSearchDateRange = ref<string[]>([])
const tableSort = ref({ prop: 'date', order: 'descending' })

// ---------- chart toggles ----------
const showBarSeries = ref(true)
const showLineSeries = ref(true)

// ---------- svg layout ----------
const svgHeight = 320
const paddingLeft = 60
const paddingRight = 30
const paddingTop = 30
const paddingBottom = 50
const chartHeight = svgHeight - paddingTop - paddingBottom
const minDateCountForScroll = 100

// ---------- computed ----------
const sortedDateData = computed<DateStatItem[]>(() => {
  const valid = (dateSummaryData.value ?? []).filter(
    i => i && i.date && i.date.trim() !== '' && i.date !== 'NULL',
  )
  return [...valid].sort((a, b) => {
    const da = new Date(a.date.replace(/\//g, '-'))
    const db = new Date(b.date.replace(/\//g, '-'))
    return da.getTime() - db.getTime()
  })
})

const dateRange = computed(() => {
  if (!sortedDateData.value.length) { return { start: '-', end: '-' } }
  const lastItem = sortedDateData.value.at(-1)
  return {
    start: formatDate(sortedDateData.value[0].date),
    end: formatDate(lastItem?.date ?? ''),
  }
})

const displayDateLabels = computed(() => {
  const data = sortedDateData.value
  if (data.length <= 5) { return data.map(i => formatDateShort(i.date)) }
  const step = Math.ceil(data.length / 8)
  const labels: string[] = []
  for (let i = 0; i < data.length; i += step) { labels[i] = formatDateShort(data[i].date) }
  const lastIndex = data.length - 1
  if (lastIndex >= 0 && data[lastIndex]) {
    labels[lastIndex] = formatDateShort(data[lastIndex].date)
  }
  return labels
})

const chartWidth = computed(() => svgWidth.value - paddingLeft - paddingRight)

const xStep = computed(() =>
  sortedDateData.value.length <= 1 ? chartWidth.value : chartWidth.value / (sortedDateData.value.length - 1),
)

const maxRecordCount = computed(() => {
  const vals = sortedDateData.value.map(i => i.recordCount ?? 0)
  return Math.ceil(Math.max(...vals, 100) / 100) * 100
})

const maxCumulativeCount = computed(() => {
  const max = Math.max(...calculateCumulativeRecords(), 100)
  return Math.ceil(max / 500) * 500
})

const barWidth = computed(() => {
  const n = sortedDateData.value.length
  if (n <= 1) { return Math.min(40, chartWidth.value) }
  const slot = chartWidth.value / n
  return Math.max(6, Math.min(35, slot - 6))
})

const cumulativeRecords = computed(() => calculateCumulativeRecords())

const cumulativeRecordPoints = computed(() => {
  if (!sortedDateData.value.length) { return [] }
  const cum = calculateCumulativeRecords()
  return sortedDateData.value.map((_, idx) => {
    const x = paddingLeft + idx * xStep.value
    const y = calcCumY(cum[idx])
    return `${x},${y}`
  })
})

// ---------- helpers ----------
function calculateCumulativeRecords(): number[] {
  let sum = 0
  return sortedDateData.value.map((i) => {
    sum += i.recordCount ?? 0
    return sum
  })
}

function calcY(value: number) {
  if (!value || value <= 0) { return paddingTop + chartHeight }
  return paddingTop + chartHeight - (Math.min(value / maxRecordCount.value, 1) * chartHeight)
}

function calcCumY(value: number) {
  if (!value || value <= 0) { return paddingTop + chartHeight }
  return paddingTop + chartHeight - (Math.min(value / maxCumulativeCount.value, 1) * chartHeight)
}

function getBarX(index: number) {
  return paddingLeft + index * xStep.value - barWidth.value / 2
}
function getBarY(value: number) { return calcY(value) }
function getBarHeight(value: number) {
  if (!value || value <= 0) { return 0 }
  return paddingTop + chartHeight - calcY(value)
}

function getAreaPath(points: string[]) {
  if (!points.length) { return '' }
  const fx = points[0].split(',')[0]
  const lastPoint = points.at(-1)
  if (!lastPoint) { return '' }
  const lx = lastPoint.split(',')[0]
  const by = paddingTop + chartHeight
  return `${points.join(' ')} L ${lx} ${by} L ${fx} ${by} Z`
}

function formatDate(dateStr?: string) {
  if (!dateStr) { return '无日期' }
  return dateStr.replace(/\//g, '-')
}
function formatDateShort(dateStr?: string) {
  if (!dateStr) { return '' }
  const parts = dateStr.split('/')
  return parts.length >= 2 ? `${parts[1]}/${parts[2]}` : dateStr
}
function getBackendSortOrder(order: string) {
  return order === 'ascending' ? 'asc' : 'desc'
}

// ---------- chart width ----------
function updateChartWidth() {
  if (!chartContainerRef.value) { return }
  const containerWidth = chartContainerRef.value.offsetWidth
  const n = sortedDateData.value.length
  if (n > minDateCountForScroll) {
    svgWidth.value = Math.max(containerWidth, n * 12 + paddingLeft + paddingRight)
  }
  else {
    svgWidth.value = Math.max(containerWidth * 0.95, 600)
  }
}

// ---------- API ----------
async function loadSummary() {
  try {
    const res = await getStatisticsSummary()
    summaryData.value = res.data ?? { byType: [], total: {} }
  }
  catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '未知错误'
    ElMessage.error(`加载统计概览失败：${msg}`)
  }
}
async function loadDateSummary() {
  try {
    const res = await getStatisticsDateSummary()
    dateSummaryData.value = Array.isArray(res.data) ? res.data : []
  }
  catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '未知错误'
    ElMessage.error(`加载日期统计失败：${msg}`)
  }
}
async function loadDashboard() {
  try {
    await getDashboardData()
  }
  catch {}
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
    if (listSearchKeyword.value.trim()) { params.keyword = listSearchKeyword.value.trim() }
    if (listSearchType.value) { params.type = listSearchType.value }
    if (Array.isArray(listSearchDateRange.value) && listSearchDateRange.value.length === 2) {
      params.startDate = listSearchDateRange.value[0]
      params.endDate = listSearchDateRange.value[1]
    }
    const res = await getStatisticsList(params as unknown as {
      page: number; size: number; keyword?: string; type?: string
      startDate?: string; endDate?: string; sortBy?: string; sortOrder?: string
    })
    const raw = res.data ?? { list: [], total: 0, page: 1, size: 20 }
    statisticsListData.value = { ...raw, totalPages: raw.totalPages ?? 0 }
    if (statisticsListData.value.list) {
      statisticsListData.value.list = statisticsListData.value.list.filter((i) => i !== null)
    }
  }
  catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '未知错误'
    ElMessage.error(`加载病案列表失败：${msg}`)
  }
  finally {
    loading.value = false
  }
}

// ---------- lifecycle ----------
onMounted(() => {
  loadSummary()
  loadDateSummary()
  loadDashboard()
  loadStatisticsList()
  nextTick(updateChartWidth)
  window.addEventListener('resize', updateChartWidth)
})
onUnmounted(() => window.removeEventListener('resize', updateChartWidth))

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

watch(sortedDateData, (v) => {
  if (v.length) { nextTick(updateChartWidth) }
})
</script>

<template>
  <div class="page-shell">
    <!-- 页头 -->
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

    <!-- 顶部统计卡片 -->
    <section class="summary-grid">
      <el-card shadow="never" class="stat-card total-records">
        <div class="stat-icon">
          <el-icon><Grid /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-label">
            总记录数
          </div>
          <div class="stat-value">
            {{ (summaryData.total?.totalRecords ?? 0).toLocaleString('zh-CN') }}
          </div>
        </div>
      </el-card>
      <el-card shadow="never" class="stat-card total-pages">
        <div class="stat-icon">
          <el-icon><Tickets /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-label">
            项目期间总页数
          </div>
          <div class="stat-value">
            {{ (summaryData.total?.totalPages ?? 0).toLocaleString('zh-CN') }}
          </div>
        </div>
      </el-card>
      <el-card shadow="never" class="stat-card unique-bah">
        <div class="stat-icon">
          <el-icon><Document /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-label">
            项目期间扫描病案数
          </div>
          <div class="stat-value">
            {{ (summaryData.uniqueBAHCount ?? 0).toLocaleString('zh-CN') }}
          </div>
        </div>
      </el-card>
      <el-card shadow="never" class="stat-card overview">
        <div class="stat-icon">
          <el-icon><TrendCharts /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-label">
            统计时间范围
          </div>
          <div class="stat-value range-text">
            {{ dateRange.start }} — {{ dateRange.end }}
          </div>
        </div>
      </el-card>
    </section>

    <!-- 每日趋势图 -->
    <el-card shadow="never" class="chart-card">
      <template #header>
        <div class="section-header">
          <el-icon><TrendCharts /></el-icon>
          <span>每日扫描记录</span>
          <el-tag size="small" type="success">
            最近 {{ sortedDateData.length }} 天
          </el-tag>
        </div>
      </template>

      <div ref="chartContainerRef" class="chart-container">
        <svg :width="svgWidth" :height="svgHeight" class="trend-chart" preserveAspectRatio="xMidYMid meet">
          <!-- 网格线 -->
          <g>
            <line
              v-for="i in 5" :key="`h${i}`"
              :x1="paddingLeft" :y1="paddingTop + (i - 1) * chartHeight / 4"
              :x2="svgWidth - paddingRight" :y2="paddingTop + (i - 1) * chartHeight / 4"
              stroke="#e0e0e0" stroke-width="1" opacity="0.4"
            />
          </g>
          <!-- Y 轴标签 -->
          <g>
            <text
              v-for="i in 5" :key="`yl${i}`"
              :x="paddingLeft - 10" :y="paddingTop + (i - 1) * chartHeight / 4 + 4"
              text-anchor="end" class="axis-label"
            >{{ Math.round(maxRecordCount * (1 - (i - 1) / 4)) }}</text>
          </g>
          <!-- 柱状图 -->
          <g v-if="showBarSeries">
            <rect
              v-for="(item, idx) in sortedDateData" :key="`bar${idx}`"
              :x="getBarX(idx)" :y="getBarY(item.recordCount ?? 0)"
              :width="barWidth" :height="getBarHeight(item.recordCount ?? 0)"
              fill="url(#gradientBar)" rx="4" ry="4" class="bar-item"
            >
              <title>{{ formatDate(item.date) }}: {{ item.recordCount }} 条记录</title>
            </rect>
          </g>
          <!-- 累计折线 -->
          <g v-if="showLineSeries">
            <path
              v-if="cumulativeRecordPoints.length > 1"
              :d="getAreaPath(cumulativeRecordPoints)"
              fill="url(#gradientCumulative)" opacity="0.12"
            />
            <polyline
              v-if="cumulativeRecordPoints.length > 1"
              :points="cumulativeRecordPoints.join(' ')"
              fill="none" stroke="url(#gradientCumulative)"
              stroke-width="3" stroke-linecap="round" stroke-linejoin="round"
            />
            <circle
              v-for="(pt, idx) in cumulativeRecordPoints" :key="`cr${idx}`"
              :cx="pt.split(',')[0]" :cy="pt.split(',')[1]"
              r="4" fill="#ff2d55" stroke="#ffffff" stroke-width="2" class="data-point"
            >
              <title>{{ formatDate(sortedDateData[idx]?.date) }}: 累计 {{ cumulativeRecords[idx] }}</title>
            </circle>
          </g>
          <!-- X 轴标签 -->
          <g>
            <text
              v-for="(label, idx) in displayDateLabels" :key="`xl${idx}`"
              :x="paddingLeft + idx * xStep" :y="svgHeight - paddingBottom + 20"
              text-anchor="middle" class="axis-label date-label"
            >{{ label }}</text>
          </g>
          <!-- 渐变 -->
          <defs>
            <linearGradient id="gradientBar" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" style="stop-color: #00c6fb;stop-opacity: 1;" />
              <stop offset="100%" style="stop-color: #0071e3;stop-opacity: 1;" />
            </linearGradient>
            <linearGradient id="gradientCumulative" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" style="stop-color: #ff2d55;stop-opacity: 1;" />
              <stop offset="100%" style="stop-color: #ff6b8a;stop-opacity: 1;" />
            </linearGradient>
          </defs>
        </svg>
      </div>

      <!-- 图例 -->
      <div class="chart-legend">
        <div class="legend-item">
          <span class="legend-dot bar" />
          <span class="legend-text">每日记录数</span>
        </div>
        <div
          class="legend-item legend-toggle"
          :class="{ 'legend-inactive': !showLineSeries }"
          @click="showLineSeries = !showLineSeries"
        >
          <span class="legend-dot line" />
          <span class="legend-text">累计记录数趋势（点击显示/隐藏）</span>
        </div>
      </div>
    </el-card>

    <!-- 病案明细列表 -->
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
          class="list-search-input"
          @keyup.enter="handleListSearch"
        />
        <el-select
          v-model="listSearchType"
          placeholder="类型筛选"
          clearable
          class="list-search-type"
          @change="handleListSearch"
        >
          <el-option label="全部" value="" />
        </el-select>
        <el-date-picker
          v-model="listSearchDateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          @change="handleListSearch"
        />
        <el-button type="primary" @click="handleListSearch">查询</el-button>
        <el-button @click="resetListSearch">重置</el-button>
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
/* ===== 页面壳 ===== */
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

/* ===== 统计卡片 ===== */
.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

/* el-card 本身只承载顶部彩条，内容由 __body 控制 */
.stat-card {
  position: relative;
  overflow: hidden;
  border-top: 3px solid transparent;
}

.stat-card.total-records { border-top-color: #0071e3; }
.stat-card.total-pages { border-top-color: #ff2d55; }
.stat-card.unique-bah { border-top-color: #34c759; }
.stat-card.overview { border-top-color: #ff9500; }

/* 穿透 el-card__body，实现 icon + body 横排 */
.stat-card :deep(.el-card__body) {
  display: flex;
  gap: 18px;
  align-items: center;
  padding: 20px 22px;
}

.stat-icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  font-size: 26px;
  border-radius: 14px;
}

.stat-card.total-records .stat-icon { color: #0071e3; background: rgb(0 113 227 / 9%); }
.stat-card.total-pages .stat-icon { color: #ff2d55; background: rgb(255 45 85 / 9%); }
.stat-card.unique-bah .stat-icon { color: #34c759; background: rgb(52 199 89 / 9%); }
.stat-card.overview .stat-icon { color: #ff9500; background: rgb(255 149 0 / 9%); }

.stat-body { flex: 1; min-width: 0; }

.stat-label {
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  white-space: nowrap;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--text-primary);
  word-break: break-all;
}

.stat-value.range-text {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  word-break: break-all;
}

/* ===== 区块卡片 ===== */
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

/* ===== 图表 ===== */
.chart-container {
  width: 100%;
  margin-bottom: 12px;
  overflow: auto hidden;
  scrollbar-color: rgb(0 0 0 / 15%) transparent;
  scrollbar-width: thin;
}

.chart-container::-webkit-scrollbar { height: 6px; }
.chart-container::-webkit-scrollbar-track { background: transparent; }

.chart-container::-webkit-scrollbar-thumb {
  background: rgb(0 113 227 / 20%);
  border-radius: 8px;
}

.trend-chart {
  display: block;
  max-width: 100%;
}

.axis-label { font-size: 12px; fill: var(--text-secondary); }
.date-label { font-size: 11px; fill: var(--text-secondary); }

.bar-item {
  cursor: pointer;
  transition: opacity 0.2s;
}
.bar-item:hover { opacity: 0.75; }

.data-point { cursor: pointer; }

.chart-legend {
  display: flex;
  gap: 24px;
  justify-content: center;
  padding-top: 14px;
  border-top: 1px solid rgb(0 0 0 / 5%);
}

.legend-item {
  display: flex;
  gap: 8px;
  align-items: center;
}

.legend-item.legend-toggle {
  cursor: pointer;
  user-select: none;
}
.legend-item.legend-toggle:hover { opacity: 0.75; }
.legend-item.legend-inactive .legend-dot.line { background: #d3d3d3 !important; }
.legend-item.legend-inactive .legend-text { color: #b0b0b0; }

.legend-dot {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  border-radius: 4px;
}
.legend-dot.bar { background: linear-gradient(180deg, #00c6fb, #0071e3); }

.legend-dot.line {
  height: 3px;
  background: linear-gradient(90deg, #ff2d55, #ff6b8a);
  border-radius: 2px;
}

.legend-text { font-size: 13px; font-weight: 500; color: var(--text-secondary); }

/* ===== 搜索栏 ===== */
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

.search-keyword { flex: 1 1 200px; min-width: 160px; }
.search-type { flex: 0 0 150px; }
.search-date { flex: 1 1 260px; min-width: 220px; }

/* ===== 表格 ===== */
:deep(.records-header-cell) {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  background: transparent !important;
}

.bah-badge {
  padding: 2px 10px;
  font-family: "SF Mono", "Roboto Mono", monospace;
  font-size: 13px;
  font-weight: 600;
  color: #0071e3;
  background: rgb(0 113 227 / 6%);
  border: 1px solid rgb(0 113 227 / 12%);
  border-radius: 6px;
}

.pages-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  padding: 2px 8px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  background: #eef2f7;
  border: 1px solid var(--divider);
  border-radius: 999px;
}

/* ===== 分页 ===== */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding-top: 20px;
  margin-top: 12px;
  border-top: 1px solid rgb(0 0 0 / 5%);
}

/* ===== 响应式 ===== */
@media (width <= 900px) {
  .summary-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (width <= 600px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .search-keyword,
  .search-type,
  .search-date {
    flex: 1 1 100%;
  }
}
</style>
