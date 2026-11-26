<script setup lang="ts">
import type { ResponseMetricTrendPoint } from '@/api/types'
import type { MrrLineSeries, MrrRequestHeatmapItem } from '@/types/chart'
import { computed, ref } from 'vue'
import { MrrLineChart, MrrRequestHeatmap } from '@/components/charts'
import { mrrChartColorToken } from '@/config/chart-theme'

defineOptions({ name: 'ResponseTrendChart' })

const props = defineProps<{
  data: ResponseMetricTrendPoint[]
}>()

const visiblePoints = ref(16)
const categories = computed(() => props.data.map(item => formatDateKey(item.bucket)))
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
    color: mrrChartColorToken('primary'),
    smooth: false,
    area: true,
  },
  {
    name: '服务端耗时',
    data: props.data.map(item => item.avgServerDurationMs ?? 0),
    color: mrrChartColorToken('neutral'),
    smooth: false,
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

    <section class="trend-section" aria-label="每日请求热力图">
      <MrrRequestHeatmap
        :data="requestHeatmap"
        :days="365"
      />
    </section>

    <section class="duration-section" aria-labelledby="duration-trend-heading">
      <div class="section-heading">
        <div>
          <strong id="duration-trend-heading">响应耗时趋势</strong>
          <span>客户端耗时包含网络与浏览器处理；服务端仅统计后端处理。可选择初始展示的记录日数，滚轮可继续调整范围。</span>
        </div>
        <el-select v-model="visiblePoints" aria-label="耗时趋势显示范围" class="trend-range mrr-single-select">
          <el-option label="最近 16 个记录日" :value="16" />
          <el-option label="最近 90 个记录日" :value="90" />
          <el-option label="全部记录日" :value="365" />
        </el-select>
      </div>
      <MrrLineChart
        :categories="categories"
        :series="durationSeries"
        y-axis-name="平均耗时"
        unit="ms"
        :visible-points="visiblePoints"
        :min-interval="0"
        :height="252"
      />
    </section>
  </div>
</template>

<style scoped>
.chart-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--mrr-space-5);
  min-width: 0;
}

.chart-overview {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 190px), 1fr));
  gap: 0;
  padding-bottom: var(--mrr-space-4);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.overview-item {
  --series-color: var(--color-primary);

  display: grid;
  grid-template-columns: auto auto;
  gap: 2px var(--mrr-space-1);
  align-content: center;
  min-width: 0;
  padding: 0 var(--mrr-space-4);
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
  gap: var(--mrr-space-2);
  align-items: center;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--mrr-font-size-xs);
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.overview-item span::before {
  flex: none;
  width: 7px;
  height: 7px;
  content: "";
  background: var(--series-color);
  border-radius: 2px;
}

.overview-item strong {
  min-width: 0;
  font-size: 18px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  line-height: 1.3;
  color: var(--el-text-color-primary);
  overflow-wrap: anywhere;
}

.overview-item i {
  align-self: end;
  padding-bottom: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--mrr-font-size-xs);
  font-style: normal;
  color: var(--el-text-color-placeholder);
  white-space: nowrap;
}

.request-overview { --series-color: var(--color-primary); }
.error-overview { --series-color: var(--mrr-tone-danger); }
.client-overview { --series-color: var(--color-primary); }
.server-overview { --series-color: var(--mrr-tone-neutral); }

.trend-section,
.duration-section {
  display: grid;
  gap: var(--mrr-space-3);
  min-width: 0;
}

.duration-section {
  padding-top: var(--mrr-space-4);
  border-top: 1px solid var(--el-border-color-lighter);
}

.section-heading {
  display: flex;
  gap: var(--mrr-space-4);
  align-items: flex-start;
  justify-content: space-between;
}

.trend-range { width: 174px; max-width: 100%; }

.section-heading > div {
  display: grid;
  gap: var(--mrr-space-1);
  min-width: 0;
}

.section-heading strong {
  font-size: var(--mrr-font-size-md);
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.section-heading span,
.section-heading small {
  font-size: var(--mrr-font-size-xs);
  color: var(--el-text-color-secondary);
}

.section-heading small {
  flex: none;
  padding: var(--mrr-space-1) var(--mrr-space-2);
  color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 8%, var(--el-bg-color));
  border: 1px solid color-mix(in srgb, var(--color-primary) 14%, transparent);
  border-radius: var(--mrr-radius-pill);
}

/* Chrome 86 回退：小节标题胶囊的底色与描边不能一起失效。 */
@supports not (color: color-mix(in srgb, #000 50%, #fff)) {
  .section-heading small {
    background: var(--mrr-tone-primary-soft);
    border-color: var(--mrr-tone-primary-border);
  }
}

@media (max-width: 900px) {
  .chart-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--mrr-space-3) 0;
  }

  .overview-item:nth-child(3) {
    padding-left: 0;
    border-left: 0;
  }
}

@media (max-width: 600px) {
  .chart-overview {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .overview-item,
  .overview-item:first-child,
  .overview-item:nth-child(3) {
    grid-template-columns: 1fr auto;
    padding: var(--mrr-space-3) 0;
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
