<script setup lang="ts">
import type { ResponseMetricTrendPoint } from '@/api/types'
import { computed } from 'vue'

defineOptions({ name: 'ResponseTrendChart' })

const props = defineProps<{
  data: ResponseMetricTrendPoint[]
}>()

const chartWidth = 600
const chartHeight = 220
const padding = { top: 24, right: 24, bottom: 42, left: 48 }
const plotWidth = chartWidth - padding.left - padding.right
const plotHeight = chartHeight - padding.top - padding.bottom

const maxRequests = computed(() => Math.max(1, ...props.data.map(item => item.requestCount)))
const maxDuration = computed(() => Math.max(
  1,
  ...props.data.flatMap(item => [item.avgClientDurationMs, item.avgServerDurationMs]),
))
const slotWidth = computed(() => plotWidth / Math.max(1, props.data.length))
const barWidth = computed(() => Math.min(28, Math.max(6, slotWidth.value * 0.42)))

function x(index: number) {
  return padding.left + slotWidth.value * index + slotWidth.value / 2
}

function requestY(value: number) {
  return padding.top + plotHeight - value / maxRequests.value * plotHeight
}

function durationY(value: number) {
  return padding.top + plotHeight - value / maxDuration.value * plotHeight
}

function linePoints(key: 'avgClientDurationMs' | 'avgServerDurationMs') {
  return props.data.map((item, index) => `${x(index)},${durationY(item[key])}`).join(' ')
}

function shortBucket(bucket: string) {
  return bucket.length > 5 ? bucket.slice(5) : bucket
}
</script>

<template>
  <div class="chart-scroll">
    <svg
      class="response-trend-chart"
      :viewBox="`0 0 ${chartWidth} ${chartHeight}`"
      role="img"
      aria-label="接口请求量与响应耗时趋势"
    >
      <g class="grid-lines">
        <line
          v-for="index in 4"
          :key="index"
          :x1="padding.left"
          :x2="chartWidth - padding.right"
          :y1="padding.top + (index - 1) * plotHeight / 3"
          :y2="padding.top + (index - 1) * plotHeight / 3"
        />
      </g>

      <g class="request-bars">
        <rect
          v-for="(item, index) in data"
          :key="`bar-${item.bucket}`"
          :x="x(index) - barWidth / 2"
          :y="requestY(item.requestCount)"
          :width="barWidth"
          :height="padding.top + plotHeight - requestY(item.requestCount)"
          rx="3"
        >
          <title>{{ item.bucket }}：{{ item.requestCount }} 次请求</title>
        </rect>
      </g>

      <polyline class="client-line" :points="linePoints('avgClientDurationMs')" />
      <polyline class="server-line" :points="linePoints('avgServerDurationMs')" />

      <g class="axis-labels">
        <text
          v-for="(item, index) in data"
          :key="`label-${item.bucket}`"
          :x="x(index)"
          :y="chartHeight - 14"
          text-anchor="middle"
        >
          {{ shortBucket(item.bucket) }}
        </text>
      </g>
    </svg>
  </div>
  <div class="legend" aria-hidden="true">
    <span><i class="legend-bar" />请求量</span>
    <span><i class="legend-client" />客户端平均耗时</span>
    <span><i class="legend-server" />服务端平均耗时</span>
  </div>
</template>

<style scoped>
.chart-scroll {
  width: 100%;
  overflow-x: auto;
}

.response-trend-chart {
  display: block;
  width: 100%;
  height: auto;
  min-width: 620px;
  aspect-ratio: 600 / 220;
}

.grid-lines line {
  stroke: rgb(0 0 0 / 8%);
  stroke-width: 1;
}

.request-bars rect {
  fill: #dceeff;
}

.client-line,
.server-line {
  fill: none;
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.client-line {
  stroke: #0075de;
}

.server-line {
  stroke: #615d59;
}

.axis-labels text {
  font-size: 11px;
  fill: #615d59;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  justify-content: center;
  padding-top: 12px;
  font-size: 12px;
  color: #615d59;
  border-top: 1px solid rgb(0 0 0 / 10%);
}

.legend span {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.legend i {
  display: inline-block;
  width: 16px;
  height: 3px;
  border-radius: 9999px;
}

.legend .legend-bar {
  height: 8px;
  background: #dceeff;
}

.legend-client {
  background: #0075de;
}

.legend-server {
  background: #615d59;
}
</style>
