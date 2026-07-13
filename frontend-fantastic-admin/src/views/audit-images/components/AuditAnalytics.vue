<script setup lang="ts">
import type { ImageAuditAnalytics } from '@/api/types'
import { DataAnalysis, Files, Timer, User, Warning } from '@element-plus/icons-vue'
import { computed } from 'vue'

defineOptions({ name: 'AuditAnalytics' })

const props = withDefaults(defineProps<{
  analytics: ImageAuditAnalytics
  loading?: boolean
}>(), {
  loading: false,
})

const chartColors = ['#4f46e5', '#0f9f8f', '#f97316', '#8b5cf6']
const actionLabels: Record<string, string> = {
  LIST: '查询图片列表',
  VIEW_IMAGE: '查看本地图片',
  VIEW_OSS_IMAGE: '查看 OSS 图片',
  DOWNLOAD: '下载病案压缩包',
}

const hasData = computed(() => props.analytics.totalAccesses > 0)
const abnormalRate = computed(() => {
  if (!props.analytics.totalAccesses) {
    return 0
  }
  return props.analytics.abnormalAccesses / props.analytics.totalAccesses * 100
})

const trendChartHeight = 300
const trendPadding = { top: 38, right: 30, bottom: 56, left: 56 }
const trendPlotHeight = trendChartHeight - trendPadding.top - trendPadding.bottom
const trendChartWidth = computed(() => Math.max(
  720,
  trendPadding.left + trendPadding.right + props.analytics.trend.length * 72,
))
const trendPlotWidth = computed(() => trendChartWidth.value - trendPadding.left - trendPadding.right)
const trendLabelStep = computed(() => Math.max(1, Math.ceil(props.analytics.trend.length / 9)))

function niceMaximum(value: number) {
  if (!Number.isFinite(value) || value <= 0) {
    return 1
  }
  const exponent = Math.floor(Math.log10(value))
  const base = 10 ** exponent
  const fraction = value / base
  const niceFraction = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10
  return niceFraction * base
}

const trendMax = computed(() => niceMaximum(Math.max(
  1,
  ...props.analytics.trend.map(item => item.count),
)))
const trendTicks = computed(() => Array.from({ length: 5 }, (_, index) => ({
  value: trendMax.value * (1 - index / 4),
  y: trendPadding.top + trendPlotHeight * index / 4,
})))
const trendPoints = computed(() => {
  const items = props.analytics.trend
  return items.map((item, index) => {
    const x = trendPadding.left + (
      items.length === 1
        ? trendPlotWidth.value / 2
        : index * trendPlotWidth.value / (items.length - 1)
    )
    const y = trendPadding.top + trendPlotHeight - item.count / trendMax.value * trendPlotHeight
    return { ...item, x, y }
  })
})
const trendPolyline = computed(() => trendPoints.value.map(point => `${point.x},${point.y}`).join(' '))
const trendArea = computed(() => {
  if (!trendPoints.value.length) {
    return ''
  }
  const first = trendPoints.value[0]
  const last = trendPoints.value.at(-1)!
  const baseline = trendPadding.top + trendPlotHeight
  return `${trendPolyline.value} ${last.x},${baseline} ${first.x},${baseline}`
})
const trendAverage = computed(() => {
  if (!props.analytics.trend.length) {
    return 0
  }
  return props.analytics.trend.reduce((sum, item) => sum + item.count, 0) / props.analytics.trend.length
})
const trendPeak = computed(() => props.analytics.trend.reduce(
  (peak, item) => item.count > peak.count ? item : peak,
  { date: '—', count: 0 },
))
const latestTrend = computed(() => props.analytics.trend.at(-1) ?? { date: '—', count: 0 })

const actionTotal = computed(() => props.analytics.actionDistribution.reduce((sum, item) => sum + item.count, 0))
const donutBackground = computed(() => {
  if (!actionTotal.value) {
    return '#ebe9e7'
  }
  let start = 0
  const segments = props.analytics.actionDistribution.map((item, index) => {
    const end = start + item.count / actionTotal.value * 100
    const segment = `${chartColors[index % chartColors.length]} ${start}% ${end}%`
    start = end
    return segment
  })
  return `conic-gradient(${segments.join(', ')})`
})

const topUserMax = computed(() => Math.max(...props.analytics.topUsers.map(item => item.count), 1))

function formatNumber(value: number, maximumFractionDigits = 0) {
  return value.toLocaleString('zh-CN', { maximumFractionDigits })
}

function formatDateLabel(value: string) {
  const parts = value.split('-')
  return parts.length === 3 ? `${parts[1]}/${parts[2]}` : value
}

function actionLabel(value: string) {
  return actionLabels[value] || value
}

function showTrendLabel(index: number) {
  return index % trendLabelStep.value === 0 || index === trendPoints.value.length - 1
}
</script>

<template>
  <section class="analytics-shell" :aria-busy="loading">
    <div class="mrr-metric-grid mrr-metric-grid--compact">
      <el-card shadow="never" class="mrr-metric-card">
        <div class="mrr-metric-card__icon">
          <el-icon><DataAnalysis /></el-icon>
        </div>
        <div class="mrr-metric-card__body">
          <span class="mrr-metric-card__label">访问总量</span>
          <strong class="mrr-metric-card__value">{{ loading ? '—' : formatNumber(analytics.totalAccesses) }}</strong>
        </div>
      </el-card>
      <el-card shadow="never" class="mrr-metric-card mrr-metric-card--teal">
        <div class="mrr-metric-card__icon">
          <el-icon><User /></el-icon>
        </div>
        <div class="mrr-metric-card__body">
          <span class="mrr-metric-card__label">独立用户</span>
          <strong class="mrr-metric-card__value">{{ loading ? '—' : formatNumber(analytics.uniqueUsers) }}</strong>
        </div>
      </el-card>
      <el-card shadow="never" class="mrr-metric-card mrr-metric-card--violet">
        <div class="mrr-metric-card__icon">
          <el-icon><Files /></el-icon>
        </div>
        <div class="mrr-metric-card__body">
          <span class="mrr-metric-card__label">独立访问对象</span>
          <strong class="mrr-metric-card__value">{{ loading ? '—' : formatNumber(analytics.uniqueTargets) }}</strong>
        </div>
      </el-card>
      <el-card shadow="never" class="mrr-metric-card mrr-metric-card--danger">
        <div class="mrr-metric-card__icon">
          <el-icon><Warning /></el-icon>
        </div>
        <div class="mrr-metric-card__body">
          <span class="mrr-metric-card__label">异常请求</span>
          <div class="mrr-metric-card__value-row">
            <strong class="mrr-metric-card__value">{{ loading ? '—' : formatNumber(analytics.abnormalAccesses) }}</strong>
            <span v-if="!loading" class="mrr-metric-card__suffix">{{ abnormalRate.toFixed(1) }}%</span>
          </div>
        </div>
      </el-card>
      <el-card shadow="never" class="mrr-metric-card mrr-metric-card--slate">
        <div class="mrr-metric-card__icon">
          <el-icon><Timer /></el-icon>
        </div>
        <div class="mrr-metric-card__body">
          <span class="mrr-metric-card__label">平均响应耗时</span>
          <div class="mrr-metric-card__value-row">
            <strong class="mrr-metric-card__value">{{ loading ? '—' : analytics.averageDurationMs.toFixed(1) }}</strong>
            <span v-if="!loading" class="mrr-metric-card__suffix">ms</span>
          </div>
        </div>
      </el-card>
    </div>

    <div v-if="loading && !hasData" class="analytics-loading" aria-label="正在加载审计分析数据">
      <span v-for="index in 3" :key="index" />
    </div>

    <div v-else-if="!hasData" class="analytics-empty">
      <el-icon><DataAnalysis /></el-icon>
      <p>当前筛选条件下暂无可分析数据</p>
      <span>调整筛选范围后可查看访问趋势与分布。</span>
    </div>

    <div v-else class="chart-grid">
      <el-card shadow="never" class="chart-card trend-card">
        <template #header>
          <div class="chart-title trend-title">
            <div>
              <strong>访问趋势</strong>
              <span>按日期统计病案图片访问次数</span>
            </div>
            <span class="date-count">{{ analytics.trend.length }} 个数据点</span>
          </div>
        </template>

        <div class="trend-insights" aria-label="访问趋势摘要">
          <div>
            <span>单日峰值</span>
            <strong>{{ formatNumber(trendPeak.count) }}</strong>
            <small>{{ formatDateLabel(trendPeak.date) }}</small>
          </div>
          <div>
            <span>日均访问</span>
            <strong>{{ formatNumber(trendAverage, 1) }}</strong>
            <small>次 / 日</small>
          </div>
          <div>
            <span>最近记录</span>
            <strong>{{ formatNumber(latestTrend.count) }}</strong>
            <small>{{ formatDateLabel(latestTrend.date) }}</small>
          </div>
        </div>

        <div data-testid="audit-trend-chart" class="trend-chart" role="img" aria-label="病案图片每日访问趋势折线图">
          <div class="trend-chart-scroll">
            <svg
              :viewBox="`0 0 ${trendChartWidth} ${trendChartHeight}`"
              :style="{ minWidth: `${trendChartWidth}px` }"
              preserveAspectRatio="xMidYMid meet"
            >
              <defs>
                <linearGradient id="auditTrendLine" x1="0" x2="1">
                  <stop offset="0" stop-color="#38bdf8" />
                  <stop offset="0.55" stop-color="#2563eb" />
                  <stop offset="1" stop-color="#4f46e5" />
                </linearGradient>
                <linearGradient id="auditTrendArea" x1="0" x2="0" y1="0" y2="1">
                  <stop offset="0" stop-color="#2563eb" stop-opacity="0.24" />
                  <stop offset="0.72" stop-color="#2563eb" stop-opacity="0.04" />
                  <stop offset="1" stop-color="#2563eb" stop-opacity="0" />
                </linearGradient>
              </defs>

              <rect
                class="trend-plot-background"
                :x="trendPadding.left"
                :y="trendPadding.top"
                :width="trendPlotWidth"
                :height="trendPlotHeight"
                rx="12"
              />

              <g class="trend-grid">
                <template v-for="tick in trendTicks" :key="tick.y">
                  <line
                    :x1="trendPadding.left"
                    :x2="trendChartWidth - trendPadding.right"
                    :y1="tick.y"
                    :y2="tick.y"
                  />
                  <text
                    :x="trendPadding.left - 12"
                    :y="tick.y + 4"
                    text-anchor="end"
                    class="trend-axis-label"
                  >
                    {{ formatNumber(tick.value) }}
                  </text>
                </template>
              </g>

              <text :x="trendPadding.left" y="20" class="trend-axis-caption">访问次数 / 次</text>
              <polygon v-if="trendPoints.length" :points="trendArea" class="trend-area" />
              <polyline v-if="trendPoints.length" :points="trendPolyline" class="trend-line" />

              <g v-for="(point, index) in trendPoints" :key="point.date" class="trend-point-group">
                <circle :cx="point.x" :cy="point.y" r="10" class="trend-point-halo" />
                <circle :cx="point.x" :cy="point.y" r="4.5" class="trend-point">
                  <title>{{ point.date }}：{{ formatNumber(point.count) }} 次访问</title>
                </circle>
                <text
                  v-if="point.count === trendPeak.count || index === trendPoints.length - 1"
                  :x="point.x"
                  :y="Math.max(26, point.y - 13)"
                  text-anchor="middle"
                  class="trend-value-label"
                >
                  {{ formatNumber(point.count) }}
                </text>
                <template v-if="showTrendLabel(index)">
                  <line
                    :x1="point.x"
                    :x2="point.x"
                    :y1="trendPadding.top + trendPlotHeight"
                    :y2="trendPadding.top + trendPlotHeight + 6"
                    class="trend-tick"
                  />
                  <text
                    :x="point.x"
                    :y="trendChartHeight - 24"
                    text-anchor="middle"
                    class="trend-axis-label"
                  >
                    {{ formatDateLabel(point.date) }}
                  </text>
                </template>
              </g>
            </svg>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="chart-card">
        <template #header>
          <div class="chart-title">
            <div><strong>操作类型分布</strong><span>按敏感访问动作汇总</span></div>
          </div>
        </template>
        <div data-testid="audit-action-chart" class="donut-layout" role="img" aria-label="病案图片访问操作类型环形图">
          <div class="donut" :style="{ background: donutBackground }">
            <div><strong>{{ formatNumber(actionTotal) }}</strong><span>次访问</span></div>
          </div>
          <div class="legend-list">
            <div v-for="(item, index) in analytics.actionDistribution" :key="item.label" class="legend-row">
              <i :style="{ background: chartColors[index % chartColors.length] }" />
              <span>{{ actionLabel(item.label) }}</span><strong>{{ formatNumber(item.count) }}</strong>
            </div>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="chart-card">
        <template #header>
          <div class="chart-title">
            <div><strong>高频访问用户</strong><span>访问次数 Top {{ analytics.topUsers.length }}</span></div>
          </div>
        </template>
        <div data-testid="audit-user-chart" class="bar-list" role="img" aria-label="病案图片高频访问用户条形图">
          <div v-for="(item, index) in analytics.topUsers" :key="item.label" class="bar-row">
            <div class="bar-meta">
              <span><b>{{ index + 1 }}</b>{{ item.label }}</span><strong>{{ formatNumber(item.count) }} 次</strong>
            </div>
            <div class="bar-track">
              <i :style="{ width: `${item.count / topUserMax * 100}%` }" />
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </section>
</template>

<style scoped>
.analytics-shell {
  display: grid;
  gap: 16px;
}

.chart-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr);
  gap: 16px;
}

.chart-card {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
}

.chart-card :deep(.el-card__header) {
  padding: 17px 20px;
  background: color-mix(in srgb, var(--el-fill-color-light) 48%, transparent);
  border-bottom-color: var(--el-border-color-lighter);
}

.chart-card :deep(.el-card__body) {
  padding: 20px;
}

.trend-card {
  grid-row: span 2;
}

.chart-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chart-title > div {
  display: grid;
  gap: 3px;
}

.chart-title strong {
  font-size: 15px;
  color: var(--text-primary);
}

.chart-title span {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-secondary);
}

.date-count {
  padding: 5px 9px;
  color: #4f46e5 !important;
  background: color-mix(in srgb, #4f46e5 9%, var(--el-bg-color));
  border: 1px solid color-mix(in srgb, #4f46e5 18%, transparent);
  border-radius: 999px;
}

.trend-insights {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.trend-insights > div {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 3px 8px;
  padding: 10px 12px;
  background: color-mix(in srgb, #2563eb 5%, var(--el-bg-color));
  border: 1px solid color-mix(in srgb, #2563eb 12%, var(--el-border-color-lighter));
  border-radius: 10px;
}

.trend-insights span {
  grid-column: 1 / -1;
  font-size: 11px;
  color: var(--text-secondary);
}

.trend-insights strong {
  font-size: 17px;
  color: var(--text-primary);
}

.trend-insights small {
  align-self: end;
  font-size: 10px;
  color: var(--text-secondary);
}

.trend-chart {
  min-height: 300px;
  overflow: hidden;
  background:
    linear-gradient(
      180deg,
      color-mix(in srgb, #2563eb 4%, var(--el-bg-color)) 0%,
      var(--el-bg-color) 100%
    );
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 13px;
}

.trend-chart-scroll {
  width: 100%;
  overflow-x: auto;
  scrollbar-width: thin;
}

.trend-chart svg {
  display: block;
  width: 100%;
  height: 300px;
}

.trend-plot-background {
  fill: color-mix(in srgb, var(--el-fill-color-light) 42%, transparent);
}

.trend-grid line {
  stroke: var(--el-border-color-lighter);
  stroke-width: 1;
  stroke-dasharray: 4 5;
  vector-effect: non-scaling-stroke;
}

.trend-axis-label,
.trend-axis-caption,
.trend-value-label {
  fill: var(--text-secondary);
}

.trend-axis-label {
  font-size: 11px;
}

.trend-axis-caption {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.trend-area {
  fill: url("#auditTrendArea");
}

.trend-line {
  fill: none;
  stroke: url("#auditTrendLine");
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
  vector-effect: non-scaling-stroke;
}

.trend-point-halo {
  opacity: 0;
  fill: color-mix(in srgb, #2563eb 18%, transparent);
  transition: opacity 160ms ease;
}

.trend-point {
  cursor: help;
  fill: var(--el-bg-color);
  stroke: #2563eb;
  stroke-width: 3;
  vector-effect: non-scaling-stroke;
}

.trend-point-group:hover .trend-point-halo {
  opacity: 1;
}

.trend-value-label {
  font-size: 10px;
  font-weight: 700;
  fill: #2563eb;
}

.trend-tick {
  stroke: var(--el-border-color);
  vector-effect: non-scaling-stroke;
}

.donut-layout {
  display: grid;
  grid-template-columns: 128px 1fr;
  gap: 20px;
  align-items: center;
  min-height: 150px;
}

.donut {
  display: grid;
  place-items: center;
  width: 128px;
  height: 128px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgb(0 0 0 / 4%);
}

.donut > div {
  display: grid;
  place-content: center;
  width: 82px;
  height: 82px;
  text-align: center;
  background: var(--el-bg-color);
  border-radius: 50%;
  box-shadow: 0 3px 12px rgb(15 23 42 / 8%);
}

.donut strong {
  font-size: 21px;
  color: var(--text-primary);
}

.donut span {
  font-size: 11px;
  color: var(--text-secondary);
}

.legend-list {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.legend-row {
  display: grid;
  grid-template-columns: 8px 1fr auto;
  gap: 8px;
  align-items: center;
  font-size: 12px;
}

.legend-row i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.legend-row span {
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text-secondary);
  white-space: nowrap;
}

.legend-row strong {
  color: var(--text-primary);
}

.bar-list {
  display: grid;
  gap: 14px;
  align-content: center;
  min-height: 150px;
}

.bar-row {
  display: grid;
  gap: 7px;
}

.bar-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
}

.bar-meta span {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
  color: var(--text-primary);
}

.bar-meta b {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  font-size: 10px;
  color: #4f46e5;
  background: color-mix(in srgb, #4f46e5 9%, var(--el-bg-color));
  border-radius: 50%;
}

.bar-meta strong {
  color: var(--text-secondary);
}

.bar-track {
  height: 7px;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border-radius: 999px;
}

.bar-track i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #38bdf8, #4f46e5);
  border-radius: inherit;
}

.analytics-empty {
  display: grid;
  place-content: center;
  justify-items: center;
  min-height: 220px;
  color: var(--text-secondary);
  background: var(--el-bg-color);
  border: 1px dashed var(--el-border-color);
  border-radius: 12px;
}

.analytics-empty .el-icon {
  font-size: 34px;
  color: #94a3b8;
}

.analytics-empty p {
  margin: 12px 0 4px;
  font-weight: 600;
  color: var(--text-primary);
}

.analytics-empty span {
  font-size: 12px;
}

.analytics-loading {
  display: grid;
  grid-template-rows: 1fr 1fr;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
  min-height: 420px;
}

.analytics-loading span {
  position: relative;
  min-height: 190px;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border-radius: 12px;
}

.analytics-loading span:first-child {
  grid-row: span 2;
}

.analytics-loading span::after {
  position: absolute;
  inset: 0;
  content: "";
  background: linear-gradient(100deg, transparent 25%, rgb(255 255 255 / 65%) 50%, transparent 75%);
  transform: translateX(-100%);
  animation: audit-loading 1.4s infinite;
}

@keyframes audit-loading {
  to { transform: translateX(100%); }
}

@media (width <= 1000px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }

  .trend-card {
    grid-row: auto;
  }

  .analytics-loading {
    grid-template-rows: repeat(3, 190px);
    grid-template-columns: 1fr;
  }

  .analytics-loading span:first-child {
    grid-row: auto;
  }
}

@media (width <= 560px) {
  .trend-insights {
    grid-template-columns: 1fr;
  }

  .donut-layout {
    grid-template-columns: 1fr;
    justify-items: center;
  }

  .legend-list {
    width: 100%;
  }

  .date-count {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .analytics-loading span::after {
    animation: none;
  }

  .trend-point-halo {
    transition: none;
  }
}
</style>
