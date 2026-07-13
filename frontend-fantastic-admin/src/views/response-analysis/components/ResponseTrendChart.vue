<script setup lang="ts">
import type { ResponseMetricTrendPoint } from '@/api/types'
import { computed } from 'vue'

defineOptions({ name: 'ResponseTrendChart' })

const props = defineProps<{
  data: ResponseMetricTrendPoint[]
}>()

const chartHeight = 320
const minimumChartWidth = 760
const padding = { top: 34, right: 62, bottom: 58, left: 62 }
const plotHeight = chartHeight - padding.top - padding.bottom

const chartWidth = computed(() => Math.max(
  minimumChartWidth,
  padding.left + padding.right + props.data.length * 72,
))
const plotWidth = computed(() => chartWidth.value - padding.left - padding.right)
const slotWidth = computed(() => plotWidth.value / Math.max(1, props.data.length))
const barWidth = computed(() => Math.min(34, Math.max(10, slotWidth.value * 0.46)))
const labelStep = computed(() => Math.max(1, Math.ceil(props.data.length / 10)))

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

const maxRequests = computed(() => niceMaximum(Math.max(
  1,
  ...props.data.map(item => item.requestCount ?? 0),
)))
const maxDuration = computed(() => niceMaximum(Math.max(
  1,
  ...props.data.flatMap(item => [item.avgClientDurationMs ?? 0, item.avgServerDurationMs ?? 0]),
)))

const requestTicks = computed(() => Array.from({ length: 5 }, (_, index) => {
  const ratio = 1 - index / 4
  return {
    value: maxRequests.value * ratio,
    y: padding.top + plotHeight * index / 4,
  }
}))
const durationTicks = computed(() => Array.from({ length: 5 }, (_, index) => {
  const ratio = 1 - index / 4
  return {
    value: maxDuration.value * ratio,
    y: padding.top + plotHeight * index / 4,
  }
}))

function x(index: number) {
  return padding.left + slotWidth.value * index + slotWidth.value / 2
}

function valueY(value: number, maximum: number) {
  return padding.top + plotHeight - value / maximum * plotHeight
}

function requestY(value: number) {
  return valueY(value, maxRequests.value)
}

function durationY(value: number) {
  return valueY(value, maxDuration.value)
}

function linePoints(key: 'avgClientDurationMs' | 'avgServerDurationMs') {
  return props.data
    .map((item, index) => `${x(index)},${durationY(item[key] ?? 0)}`)
    .join(' ')
}

function areaPoints(key: 'avgClientDurationMs' | 'avgServerDurationMs') {
  if (!props.data.length) {
    return ''
  }
  const baseline = padding.top + plotHeight
  return `${linePoints(key)} ${x(props.data.length - 1)},${baseline} ${x(0)},${baseline}`
}

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

function showLabel(index: number) {
  return index % labelStep.value === 0 || index === props.data.length - 1
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
      <div class="series-legend" aria-label="图例">
        <span class="request-series"><i />请求量</span>
        <span class="client-series"><i />客户端耗时</span>
        <span class="server-series"><i />服务端耗时</span>
      </div>
    </div>

    <div class="chart-scroll">
      <svg
        class="response-trend-chart"
        :viewBox="`0 0 ${chartWidth} ${chartHeight}`"
        :style="{ minWidth: `${chartWidth}px` }"
        role="img"
        aria-label="接口请求量与客户端、服务端响应耗时趋势"
      >
        <defs>
          <linearGradient id="responseRequestGradient" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0" stop-color="#818cf8" />
            <stop offset="1" stop-color="#4f46e5" />
          </linearGradient>
          <linearGradient id="responseClientArea" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0" stop-color="#0ea5e9" stop-opacity="0.2" />
            <stop offset="1" stop-color="#0ea5e9" stop-opacity="0" />
          </linearGradient>
        </defs>

        <rect
          class="plot-background"
          :x="padding.left"
          :y="padding.top"
          :width="plotWidth"
          :height="plotHeight"
          rx="12"
        />

        <g class="grid-lines">
          <line
            v-for="tick in requestTicks"
            :key="`grid-${tick.y}`"
            :x1="padding.left"
            :x2="chartWidth - padding.right"
            :y1="tick.y"
            :y2="tick.y"
          />
        </g>

        <g class="left-axis axis-labels">
          <text
            v-for="tick in requestTicks"
            :key="`request-${tick.y}`"
            :x="padding.left - 12"
            :y="tick.y + 4"
            text-anchor="end"
          >
            {{ formatNumber(tick.value) }}
          </text>
          <text class="axis-caption" :x="padding.left" :y="18" text-anchor="start">请求量 / 次</text>
        </g>

        <g class="right-axis axis-labels">
          <text
            v-for="tick in durationTicks"
            :key="`duration-${tick.y}`"
            :x="chartWidth - padding.right + 12"
            :y="tick.y + 4"
            text-anchor="start"
          >
            {{ formatNumber(tick.value) }}
          </text>
          <text class="axis-caption" :x="chartWidth - padding.right" :y="18" text-anchor="end">耗时 / ms</text>
        </g>

        <g class="request-bars">
          <g
            v-for="(item, index) in data"
            :key="`bar-${item.bucket}`"
            class="request-column"
            tabindex="0"
            role="img"
            :aria-label="`${item.bucket}，${item.requestCount} 次请求`"
          >
            <rect
              class="request-bar"
              :x="x(index) - barWidth / 2"
              :y="requestY(item.requestCount ?? 0)"
              :width="barWidth"
              :height="Math.max(2, padding.top + plotHeight - requestY(item.requestCount ?? 0))"
              rx="6"
            >
              <title>{{ item.bucket }}：{{ formatNumber(item.requestCount ?? 0) }} 次请求</title>
            </rect>
          </g>
        </g>

        <polygon class="client-area" :points="areaPoints('avgClientDurationMs')" />
        <polyline class="client-line" :points="linePoints('avgClientDurationMs')" />
        <polyline class="server-line" :points="linePoints('avgServerDurationMs')" />

        <g v-for="(item, index) in data" :key="`points-${item.bucket}`" class="data-point-group">
          <circle
            class="point-halo client-halo"
            :cx="x(index)"
            :cy="durationY(item.avgClientDurationMs ?? 0)"
            r="8"
          />
          <circle
            class="data-point client-point"
            :cx="x(index)"
            :cy="durationY(item.avgClientDurationMs ?? 0)"
            r="4"
          >
            <title>{{ item.bucket }}：客户端 {{ formatDuration(item.avgClientDurationMs ?? 0) }}</title>
          </circle>
          <circle
            class="point-halo server-halo"
            :cx="x(index)"
            :cy="durationY(item.avgServerDurationMs ?? 0)"
            r="7"
          />
          <circle
            class="data-point server-point"
            :cx="x(index)"
            :cy="durationY(item.avgServerDurationMs ?? 0)"
            r="3.5"
          >
            <title>{{ item.bucket }}：服务端 {{ formatDuration(item.avgServerDurationMs ?? 0) }}</title>
          </circle>
        </g>

        <g class="x-axis axis-labels">
          <template v-for="(item, index) in data" :key="`label-${item.bucket}`">
            <line
              v-if="showLabel(index)"
              :x1="x(index)"
              :x2="x(index)"
              :y1="padding.top + plotHeight"
              :y2="padding.top + plotHeight + 6"
            />
            <text
              v-if="showLabel(index)"
              :x="x(index)"
              :y="chartHeight - 24"
              text-anchor="middle"
            >
              {{ formatBucket(item.bucket) }}
            </text>
          </template>
        </g>
      </svg>
    </div>
  </div>
</template>

<style scoped>
.chart-shell {
  --request-color: #4f46e5;
  --client-color: #0284c7;
  --server-color: #059669;

  display: grid;
  gap: 14px;
}

.chart-overview {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: stretch;
}

.overview-item {
  display: grid;
  grid-template-columns: auto auto;
  gap: 2px 6px;
  align-content: center;
  min-width: 136px;
  padding: 10px 12px;
  background: color-mix(in srgb, var(--series-color) 7%, var(--el-bg-color));
  border: 1px solid color-mix(in srgb, var(--series-color) 18%, transparent);
  border-radius: 10px;
}

.overview-item::before {
  grid-row: 1 / span 2;
  align-self: stretch;
  width: 3px;
  min-height: 30px;
  content: "";
  background: var(--series-color);
  border-radius: 999px;
}

.overview-item span {
  font-size: 11px;
  color: var(--text-secondary);
}

.overview-item strong {
  font-size: 16px;
  line-height: 1.2;
  color: var(--text-primary);
}

.overview-item i {
  align-self: end;
  margin-left: -2px;
  font-size: 11px;
  font-style: normal;
  color: var(--text-secondary);
}

.request-overview { --series-color: var(--request-color); }
.client-overview { --series-color: var(--client-color); }
.server-overview { --series-color: var(--server-color); }

.series-legend {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 8px 14px;
  align-items: center;
  justify-content: flex-end;
  min-width: 260px;
  padding: 0 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.series-legend span {
  display: inline-flex;
  gap: 7px;
  align-items: center;
  white-space: nowrap;
}

.series-legend i {
  display: inline-block;
  width: 18px;
  height: 3px;
  background: var(--series-color);
  border-radius: 999px;
}

.series-legend .request-series { --series-color: var(--request-color); }
.series-legend .client-series { --series-color: var(--client-color); }
.series-legend .server-series { --series-color: var(--server-color); }
.series-legend .request-series i { height: 9px; border-radius: 3px; }
.series-legend .server-series i { background: repeating-linear-gradient(90deg, var(--server-color) 0 5px, transparent 5px 8px); }

.chart-scroll {
  width: 100%;
  overflow-x: auto;
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--el-color-primary) 4%, var(--el-bg-color)) 0%,
    var(--el-bg-color) 100%
  );
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  scrollbar-width: thin;
}

.response-trend-chart {
  display: block;
  width: 100%;
  height: 320px;
}

.plot-background {
  fill: color-mix(in srgb, var(--el-fill-color-light) 38%, transparent);
}

.grid-lines line {
  stroke: var(--el-border-color-lighter);
  stroke-width: 1;
  stroke-dasharray: 4 5;
  vector-effect: non-scaling-stroke;
}

.axis-labels text {
  font-size: 11px;
  fill: var(--text-secondary);
}

.axis-labels .axis-caption {
  font-size: 10px;
  font-weight: 600;
  fill: var(--text-secondary);
  letter-spacing: 0.04em;
}

.x-axis line {
  stroke: var(--el-border-color);
  vector-effect: non-scaling-stroke;
}

.request-bar {
  fill: url(#responseRequestGradient);
  opacity: 0.76;
  transition: opacity 160ms ease;
  vector-effect: non-scaling-stroke;
}

.request-column:hover .request-bar,
.request-column:focus .request-bar {
  opacity: 1;
}

.client-area {
  fill: url(#responseClientArea);
  pointer-events: none;
}

.client-line,
.server-line {
  fill: none;
  stroke-width: 2.6;
  stroke-linecap: round;
  stroke-linejoin: round;
  pointer-events: none;
  vector-effect: non-scaling-stroke;
}

.client-line { stroke: var(--client-color); }
.server-line { stroke: var(--server-color); stroke-dasharray: 7 5; }

.point-halo {
  opacity: 0;
  transition: opacity 160ms ease;
}

.client-halo { fill: color-mix(in srgb, var(--client-color) 18%, transparent); }
.server-halo { fill: color-mix(in srgb, var(--server-color) 18%, transparent); }

.data-point {
  fill: var(--el-bg-color);
  stroke-width: 2.5;
  cursor: help;
  vector-effect: non-scaling-stroke;
}

.client-point { stroke: var(--client-color); }
.server-point { stroke: var(--server-color); }
.data-point-group:hover .point-halo { opacity: 1; }

@media (width <= 760px) {
  .series-legend {
    flex-basis: 100%;
    justify-content: flex-start;
    min-width: 0;
  }

  .overview-item {
    flex: 1 1 130px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .request-bar,
  .point-halo {
    transition: none;
  }
}
</style>
