<script setup lang="ts">
import type { ResponseMetricTrendPoint } from '@/api/types'
import type { MrrBarSeries, MrrLineSeries } from '@/types/chart'
import { computed } from 'vue'
import { MrrDualAxisChart } from '@/components/charts'

defineOptions({ name: 'ResponseTrendChart' })

const props = defineProps<{
  data: ResponseMetricTrendPoint[]
}>()

const categories = computed(() => props.data.map(item => formatBucket(item.bucket)))
const barSeries = computed<MrrBarSeries[]>(() => [
  {
    name: '请求量',
    data: props.data.map(item => item.requestCount ?? 0),
    color: '#62aef0',
  },
])
const lineSeries = computed<MrrLineSeries[]>(() => [
  {
    name: '客户端耗时',
    data: props.data.map(item => item.avgClientDurationMs ?? 0),
    color: '#2563eb',
    smooth: true,
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
    <div class="chart-overview" aria-label="趋势摘要">
      <div class="overview-item request-overview">
        <span>峰值请求量</span>
        <strong>{{ formatNumber(peakRequests) }}</strong>
        <i>次</i>
      </div>
      <div class="overview-item client-overview">
        <span>客户端均值</span>
        <strong>{{ formatDuration(averageClientDuration) }}</strong>
      </div>
      <div class="overview-item server-overview">
        <span>服务端均值</span>
        <strong>{{ formatDuration(averageServerDuration) }}</strong>
      </div>
    </div>

    <MrrDualAxisChart
      :categories="categories"
      :bars="barSeries"
      :lines="lineSeries"
      left-axis-name="请求量"
      right-axis-name="响应耗时"
      left-unit="次"
      right-unit="ms"
      :height="304"
    />
  </div>
</template>

<style scoped>
.chart-shell {
  display: grid;
  gap: 14px;
}

.chart-overview {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  align-items: center;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.overview-item {
  --series-color: #2563eb;

  display: grid;
  grid-template-columns: auto auto;
  gap: 2px 5px;
  align-items: baseline;
  min-width: 146px;
  padding-right: 18px;
}

.overview-item + .overview-item {
  padding-left: 18px;
  border-left: 1px solid var(--el-border-color-lighter);
}

.overview-item span {
  display: flex;
  grid-column: 1 / -1;
  gap: 7px;
  align-items: center;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.overview-item span::before {
  width: 6px;
  height: 6px;
  content: "";
  background: var(--series-color);
  border-radius: 2px;
}

.overview-item strong {
  font-size: 17px;
  font-weight: 600;
  line-height: 1.25;
  color: var(--el-text-color-primary);
}

.overview-item i {
  font-size: 11px;
  font-style: normal;
  color: var(--el-text-color-placeholder);
}

.request-overview { --series-color: #62aef0; }
.client-overview { --series-color: #2563eb; }
.server-overview { --series-color: #64748b; }

@media (width <= 600px) {
  .chart-overview {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .overview-item {
    min-width: 0;
    padding-right: 10px;
  }

  .overview-item + .overview-item {
    padding-left: 10px;
  }

  .overview-item strong {
    font-size: 15px;
  }
}
</style>
