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

const chartColors = ['#0075de', '#2a9d99', '#dd5b00', '#391c57']
const actionLabels: Record<string, string> = {
  LIST: '查询图片列表',
  VIEW_IMAGE: '查看本地图片',
  VIEW_OSS_IMAGE: '查看 OSS 图片',
  DOWNLOAD: '下载病案压缩包',
}

const hasData = computed(() => props.analytics.totalAccesses > 0)
const abnormalRate = computed(() => {
  if (!props.analytics.totalAccesses) { return 0 }
  return props.analytics.abnormalAccesses / props.analytics.totalAccesses * 100
})

const trendMax = computed(() => Math.max(...props.analytics.trend.map(item => item.count), 1))
const trendPoints = computed(() => {
  const items = props.analytics.trend
  const chartWidth = 576
  const chartHeight = 144
  return items.map((item, index) => {
    const x = 40 + (items.length === 1 ? chartWidth / 2 : index * chartWidth / (items.length - 1))
    const y = 20 + chartHeight - item.count / trendMax.value * chartHeight
    return { ...item, x, y }
  })
})
const trendPolyline = computed(() => trendPoints.value.map(point => `${point.x},${point.y}`).join(' '))
const trendArea = computed(() => {
  if (!trendPoints.value.length) { return '' }
  const first = trendPoints.value[0]
  const last = trendPoints.value.at(-1)!
  return `${trendPolyline.value} ${last.x},164 ${first.x},164`
})

const actionTotal = computed(() => props.analytics.actionDistribution.reduce((sum, item) => sum + item.count, 0))
const donutBackground = computed(() => {
  if (!actionTotal.value) { return '#ebe9e7' }
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

function formatNumber(value: number) {
  return value.toLocaleString('zh-CN')
}

function formatDateLabel(value: string) {
  const parts = value.split('-')
  return parts.length === 3 ? `${parts[1]}/${parts[2]}` : value
}

function actionLabel(value: string) {
  return actionLabels[value] || value
}
</script>

<template>
  <section class="analytics-shell" :aria-busy="loading">
    <div class="summary-grid">
      <el-card shadow="never" class="metric-card metric-primary">
        <div class="metric-icon">
          <el-icon><DataAnalysis /></el-icon>
        </div>
        <div><p>访问总量</p><strong>{{ loading ? '—' : formatNumber(analytics.totalAccesses) }}</strong></div>
      </el-card>
      <el-card shadow="never" class="metric-card metric-teal">
        <div class="metric-icon">
          <el-icon><User /></el-icon>
        </div>
        <div><p>独立用户</p><strong>{{ loading ? '—' : formatNumber(analytics.uniqueUsers) }}</strong></div>
      </el-card>
      <el-card shadow="never" class="metric-card metric-purple">
        <div class="metric-icon">
          <el-icon><Files /></el-icon>
        </div>
        <div><p>独立访问对象</p><strong>{{ loading ? '—' : formatNumber(analytics.uniqueTargets) }}</strong></div>
      </el-card>
      <el-card shadow="never" class="metric-card metric-warning">
        <div class="metric-icon">
          <el-icon><Warning /></el-icon>
        </div>
        <div>
          <p>异常请求</p><strong>{{ loading ? '—' : formatNumber(analytics.abnormalAccesses) }}</strong>
          <span v-if="!loading">{{ abnormalRate.toFixed(1) }}%</span>
        </div>
      </el-card>
      <el-card shadow="never" class="metric-card metric-neutral">
        <div class="metric-icon">
          <el-icon><Timer /></el-icon>
        </div>
        <div><p>平均响应耗时</p><strong>{{ loading ? '—' : analytics.averageDurationMs.toFixed(1) }}</strong><span v-if="!loading">ms</span></div>
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
          <div class="chart-title">
            <div><strong>访问趋势</strong><span>最近 {{ analytics.trend.length }} 个有记录日期</span></div>
          </div>
        </template>
        <div data-testid="audit-trend-chart" class="trend-chart" role="img" aria-label="病案图片每日访问趋势折线图">
          <svg viewBox="0 0 656 210" preserveAspectRatio="none">
            <defs>
              <linearGradient id="auditTrendLine" x1="0" x2="1">
                <stop offset="0" stop-color="#62aef0" />
                <stop offset="1" stop-color="#0075de" />
              </linearGradient>
              <linearGradient id="auditTrendArea" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0" stop-color="#0075de" stop-opacity="0.2" />
                <stop offset="1" stop-color="#0075de" stop-opacity="0" />
              </linearGradient>
            </defs>
            <line v-for="index in 4" :key="index" x1="40" x2="616" :y1="20 + (index - 1) * 48" :y2="20 + (index - 1) * 48" class="grid-line" />
            <polygon v-if="trendPoints.length" :points="trendArea" fill="url(#auditTrendArea)" />
            <polyline v-if="trendPoints.length" :points="trendPolyline" fill="none" stroke="url(#auditTrendLine)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" />
            <g v-for="point in trendPoints" :key="point.date">
              <circle :cx="point.x" :cy="point.y" r="4" class="trend-point"><title>{{ point.date }}：{{ point.count }} 次</title></circle>
              <text :x="point.x" y="190" text-anchor="middle" class="axis-label">{{ formatDateLabel(point.date) }}</text>
            </g>
          </svg>
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
.analytics-shell { display: grid; gap: 16px; }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; }
.metric-card { position: relative; overflow: hidden; border-top: 3px solid var(--metric-color); }
.metric-card :deep(.el-card__body) { display: flex; gap: 14px; align-items: center; padding: 18px; }
.metric-icon { display: grid; flex: 0 0 42px; place-items: center; height: 42px; font-size: 21px; color: var(--metric-color); background: color-mix(in srgb, var(--metric-color) 10%, white); border-radius: 10px; }
.metric-card p { margin: 0 0 4px; font-size: 12px; color: var(--text-secondary); }
.metric-card strong { font-size: 24px; line-height: 1; color: var(--text-primary); }
.metric-card span { margin-left: 6px; font-size: 12px; color: var(--text-secondary); }
.metric-primary { --metric-color: #0075de; }
.metric-teal { --metric-color: #2a9d99; }
.metric-purple { --metric-color: #7c4d9e; }
.metric-warning { --metric-color: #dd5b00; }
.metric-neutral { --metric-color: #615d59; }
.chart-grid { display: grid; grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr); gap: 16px; }
.trend-card { grid-row: span 2; }
.chart-title { display: flex; align-items: center; justify-content: space-between; }
.chart-title div { display: grid; gap: 3px; }
.chart-title strong { font-size: 15px; color: var(--text-primary); }
.chart-title span { font-size: 12px; font-weight: 400; color: var(--text-secondary); }
.trend-chart { min-height: 260px; }
.trend-chart svg { display: block; width: 100%; height: 260px; overflow: visible; }
.grid-line { stroke: rgb(0 0 0 / 8%); stroke-width: 1; }
.trend-point { fill: #fff; stroke: #0075de; stroke-width: 3; }
.axis-label { font-size: 11px; fill: var(--text-secondary); }
.donut-layout { display: grid; grid-template-columns: 128px 1fr; gap: 20px; align-items: center; min-height: 150px; }
.donut { display: grid; place-items: center; width: 128px; height: 128px; border-radius: 50%; }
.donut > div { display: grid; place-content: center; width: 82px; height: 82px; text-align: center; background: var(--el-bg-color); border-radius: 50%; }
.donut strong { font-size: 21px; color: var(--text-primary); }
.donut span { font-size: 11px; color: var(--text-secondary); }
.legend-list { display: grid; gap: 10px; min-width: 0; }
.legend-row { display: grid; grid-template-columns: 8px 1fr auto; gap: 8px; align-items: center; font-size: 12px; }
.legend-row i { width: 8px; height: 8px; border-radius: 50%; }
.legend-row span { overflow: hidden; text-overflow: ellipsis; color: var(--text-secondary); white-space: nowrap; }
.legend-row strong { color: var(--text-primary); }
.bar-list { display: grid; gap: 14px; align-content: center; min-height: 150px; }
.bar-row { display: grid; gap: 7px; }
.bar-meta { display: flex; align-items: center; justify-content: space-between; font-size: 12px; }
.bar-meta span { display: flex; gap: 8px; align-items: center; min-width: 0; color: var(--text-primary); }
.bar-meta b { display: grid; place-items: center; width: 20px; height: 20px; font-size: 10px; color: #0075de; background: #f2f9ff; border-radius: 50%; }
.bar-meta strong { color: var(--text-secondary); }
.bar-track { height: 7px; overflow: hidden; background: #ebe9e7; border-radius: 999px; }
.bar-track i { display: block; height: 100%; background: linear-gradient(90deg, #62aef0, #0075de); border-radius: inherit; }
.analytics-empty { display: grid; place-content: center; justify-items: center; min-height: 220px; color: var(--text-secondary); background: var(--el-bg-color); border: 1px dashed rgb(0 0 0 / 15%); border-radius: 12px; }
.analytics-empty .el-icon { font-size: 34px; color: #a39e98; }
.analytics-empty p { margin: 12px 0 4px; font-weight: 600; color: var(--text-primary); }
.analytics-empty span { font-size: 12px; }
.analytics-loading { display: grid; grid-template-rows: 1fr 1fr; grid-template-columns: 2fr 1fr; gap: 16px; min-height: 420px; }
.analytics-loading span { position: relative; min-height: 190px; overflow: hidden; background: #f6f5f4; border-radius: 12px; }
.analytics-loading span:first-child { grid-row: span 2; }
.analytics-loading span::after { position: absolute; inset: 0; content: ""; background: linear-gradient(100deg, transparent 25%, rgb(255 255 255 / 65%) 50%, transparent 75%); transform: translateX(-100%); animation: audit-loading 1.4s infinite; }

@keyframes audit-loading { to { transform: translateX(100%); } }

@media (width <= 1000px) {
  .chart-grid { grid-template-columns: 1fr; }
  .trend-card { grid-row: auto; }
  .analytics-loading { grid-template-rows: repeat(3, 190px); grid-template-columns: 1fr; }
  .analytics-loading span:first-child { grid-row: auto; }
}

@media (width <= 560px) {
  .summary-grid { grid-template-columns: 1fr 1fr; }
  .donut-layout { grid-template-columns: 1fr; justify-items: center; }
  .legend-list { width: 100%; }
}

@media (width <= 380px) { .summary-grid { grid-template-columns: 1fr; } }

@media (prefers-reduced-motion: reduce) { .analytics-loading span::after { animation: none; } }
</style>
