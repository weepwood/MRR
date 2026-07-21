<script setup lang="ts">
import type { ResponseMetricTrendPoint } from '@/api/types'
import type { MrrLineSeries, MrrRequestHeatmapItem } from '@/types/chart'
import { computed } from 'vue'
import { MrrLineChart, MrrRequestHeatmap } from '@/components/charts'

defineOptions({ name: 'ResponseTrendChart' })

const props = defineProps<{
  data: ResponseMetricTrendPoint[]
}>()

const categories = computed(() => props.data.map(item => formatBucket(item.bucket)))
const requestHeatmap = computed<MrrRequestHeatmapItem[]>(() => props.data.map(item => ({
  date: formatDateKey(item.bucket),
  total: item.requestCount ?? 0,
  error: item.errorCount ?? 0,
  avgClientDurationMs: item.avgClientDurationMs ?? 0,
  avgServerDurationMs: item.avgServerDurationMs ?? 0,
})))
const durationSeries = computed<MrrLineSeries[]>(() => [
  {
    name: '客户端耗时',
    data: props.data.map(item => item.avgClientDurationMs ?? 0),
    color: '#2563eb',
    smooth: true,
    area: true,
  },
  {
    name: '服务端耗时',
    data: props.data.map(item => item.avgServerDurationMs ?? 0),
    color: '#64748b',
    smooth: true,
    dashed: true,
  },
])

const peakRequests = computed(() => Math.max(0, ...props.data.map(item => item.requestCount ?? 0)))
const totalRequests = computed(() => props.data.reduce((sum, item) => sum + (item.requestCount ?? 0), 0))
const totalErrors = computed(() => props.data.reduce((sum, item) => sum + (item.errorCount ?? 0), 0))
const overallErrorRate = computed(() => totalRequests.value > 0 ? totalErrors.value / totalRequests.value * 100 : 0)
const averageClientDuration = computed(() => average(props.data.map(item => item.avgClientDurationMs ?? 0)))
const averageServerDuration = computed(() => average(props.data.map(item => item.avgServerDurationMs ?? 0)))

function average(values: number[]) {
  return values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : 0
}

function formatNumber(value: number) {
  return value.toLocaleString('zh-CN', { maximumFractionDigits: 1 })
}

function formatDuration(value: number) {
  if (value >= 1000) {
    return `${(value / 1000).toLocaleString('zh-CN', { maximumFractionDigits: 1 })} s`
  }
  return `${formatNumber(value)} ms`
}

function formatDateKey(bucket: string) {
  const match = bucket.match(/^(\d{4})-(\d{2})-(\d{2})/)
  return match ? `${match[1]}-${match[2]}-${match[3]}` : bucket.slice(0, 10)
}

function formatBucket(bucket: string) {
  const dateMatch = bucket.match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (dateMatch) {
    return `${dateMatch[2]}/${dateMatch[3]}`
  }
  return bucket.length > 10 ? bucket.slice(-10) : bucket
}
</script>

<template>
  <div class="chart-shell">
    <div class="chart-overview" aria-label="近一年趋势摘要">
      <div class="overview-item request-overview">
        <span>单日峰值请求</span>
        <strong>{{ formatNumber(peakRequests) }}</strong>
        <i>次</i>
      </div>
      <div class="overview-item error-overview">
        <span>近一年错误请求</span>
        <strong>{{ formatNumber(totalErrors) }}</strong>
        <i>{{ overallErrorRate.toFixed(2) }}%</i>
      </div>
      <div class="overview-item client-overview">
        <span>客户端日均耗时</span>
        <strong>{{ formatDuration(averageClientDuration) }}</strong>
      </div>
      <div class="overview-item server-overview">
        <span>服务端日均耗时</span>
        <strong>{{ formatDuration(averageServerDuration) }}</strong>
      </div>
    </div>

    <section class="trend-section" aria-labelledby="request-heatmap-heading">
      <div class="section-heading">
        <div>
          <strong id="request-heatmap-heading">每日请求热力图</strong>
          <span>一个方块代表一天，颜色越深表示当天请求数量越多</span>
        </div>
        <small>最近 365 天</small>
      </div>
      <MrrRequestHeatmap
        :data="requestHeatmap"
        :days="365"
      />
    </section>

    <section class="duration-section" aria-labelledby="duration-trend-heading">
      <div class="section-heading">
        <div>
          <strong id="duration-trend-heading">响应耗时趋势</strong>
          <span>客户端耗时包含网络和浏览器处理，服务端耗时仅统计后端处理阶段</span>
        </div>
      </div>
      <MrrLineChart
        :categories="categories"
        :series="durationSeries"
        y-axis-name="平均耗时"
        unit="ms"
        :height="252"
      />
    </section>
  </div>
</template>

<style scoped>
.chart-shell {
  display: grid;
  gap: 20px;
}

.chart-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.overview-item {
  --series-color: #2563eb;

  display: grid;
  grid-template-columns: auto auto;
  gap: 2px 5px;
  align-content: center;
  min-width: 0;
  padding: 0 18px;
}

.overview-item:first-child {
  padding-left: 0;
}

.overview-item + .overview-item {
  border-left: 1px solid var(--el-border-color-lighter);
}

.overview-item span {
  display: flex;
  grid-column: 1 / -1;
  gap: 7px;
  align-items: center;
  min-width: 0;
  overflow: hidden;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.overview-item span::before {
  width: 7px;
  height: 7px;
  flex: none;
  content: "";
  background: var(--series-color);
  border-radius: 2px;
}

.overview-item strong {
  min-width: 0;
  overflow: hidden;
  font-size: 18px;
  font-weight: 650;
  line-height: 1.3;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.overview-item i {
  align-self: end;
  padding-bottom: 2px;
  overflow: hidden;
  font-size: 10px;
  font-style: normal;
  color: var(--el-text-color-placeholder);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.request-overview { --series-color: #2563eb; }
.error-overview { --series-color: #ef4444; }
.client-overview { --series-color: #2563eb; }
.server-overview { --series-color: #64748b; }

.trend-section,
.duration-section {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.duration-section {
  padding-top: 18px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.section-heading {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.section-heading > div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.section-heading strong {
  font-size: 14px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.section-heading span,
.section-heading small {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.section-heading small {
  flex: none;
  padding: 4px 8px;
  color: #2563eb;
  background: color-mix(in srgb, #2563eb 8%, var(--el-bg-color));
  border: 1px solid color-mix(in srgb, #2563eb 14%, transparent);
  border-radius: 999px;
}

@media (width <= 900px) {
  .chart-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px 0;
  }

  .overview-item:nth-child(3) {
    padding-left: 0;
    border-left: 0;
  }
}

@media (width <= 600px) {
  .chart-overview {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .overview-item,
  .overview-item:first-child,
  .overview-item:nth-child(3) {
    grid-template-columns: 1fr auto;
    padding: 10px 0;
    border-top: 1px solid var(--el-border-color-lighter);
    border-left: 0;
  }

  .overview-item:first-child {
    padding-top: 0;
    border-top: 0;
  }

  .section-heading {
    display: grid;
  }

  .section-heading small {
    justify-self: start;
  }
}
</style>
