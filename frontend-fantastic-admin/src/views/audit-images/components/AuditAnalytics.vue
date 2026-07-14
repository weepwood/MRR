<script setup lang="ts">
import type { ImageAuditAnalytics } from '@/api/types'
import type { MrrChartCountItem, MrrLineSeries } from '@/types/chart'
import { DataAnalysis, Files, Timer, User, Warning } from '@element-plus/icons-vue'
import { computed } from 'vue'
import {
  MrrChartCard,
  MrrDonutChart,
  MrrHorizontalBarChart,
  MrrLineChart,
} from '@/components/charts'

defineOptions({ name: 'AuditAnalytics' })

const props = withDefaults(defineProps<{
  analytics: ImageAuditAnalytics
  loading?: boolean
}>(), {
  loading: false,
})

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

const trendCategories = computed(() => props.analytics.trend.map(item => formatDateLabel(item.date)))
const trendSeries = computed<MrrLineSeries[]>(() => [
  {
    name: '访问次数',
    data: props.analytics.trend.map(item => item.count),
    color: '#2563eb',
    area: true,
    smooth: true,
  },
])
const actionData = computed<MrrChartCountItem[]>(() => props.analytics.actionDistribution.map(item => ({
  label: actionLabel(item.label),
  count: item.count,
})))
const topUserData = computed<MrrChartCountItem[]>(() => props.analytics.topUsers.map(item => ({
  label: item.label,
  count: item.count,
})))

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
      <MrrChartCard
        title="访问趋势"
        description="按日期统计病案图片访问次数"
        class="trend-card"
        :loading="loading"
        :empty="!analytics.trend.length"
        empty-description="暂无访问趋势数据"
      >
        <template #actions>
          <span class="date-count">{{ analytics.trend.length }} 个数据点</span>
        </template>
        <template #summary>
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
        </template>
        <div data-testid="audit-trend-chart">
          <MrrLineChart
            :categories="trendCategories"
            :series="trendSeries"
            y-axis-name="访问次数"
            unit="次"
            :show-legend="false"
            :height="300"
          />
        </div>
      </MrrChartCard>

      <MrrChartCard
        title="操作类型分布"
        description="按敏感访问动作汇总"
        :loading="loading"
        :empty="!actionData.length"
        empty-description="暂无操作分布数据"
      >
        <div data-testid="audit-action-chart">
          <MrrDonutChart
            :data="actionData"
            center-label="次访问"
            unit="次"
            legend-position="right"
            :height="230"
          />
        </div>
      </MrrChartCard>

      <MrrChartCard
        title="高频访问用户"
        :description="`访问次数 Top ${analytics.topUsers.length}`"
        :loading="loading"
        :empty="!topUserData.length"
        empty-description="暂无高频访问用户数据"
      >
        <div data-testid="audit-user-chart">
          <MrrHorizontalBarChart
            :data="topUserData"
            unit="次"
            :height="230"
          />
        </div>
      </MrrChartCard>
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

.trend-card {
  grid-row: span 2;
}

.date-count {
  display: inline-flex;
  padding: 5px 9px;
  font-size: 12px;
  color: #2563eb;
  background: color-mix(in srgb, #2563eb 9%, var(--el-bg-color));
  border: 1px solid color-mix(in srgb, #2563eb 18%, transparent);
  border-radius: 999px;
}

.trend-insights {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
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
  color: var(--el-text-color-secondary);
}

.trend-insights strong {
  font-size: 17px;
  color: var(--el-text-color-primary);
}

.trend-insights small {
  align-self: end;
  font-size: 10px;
  color: var(--el-text-color-secondary);
}

.analytics-loading {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.analytics-loading span {
  height: 280px;
  background:
    linear-gradient(
      90deg,
      var(--el-fill-color-light) 25%,
      var(--el-fill-color-lighter) 37%,
      var(--el-fill-color-light) 63%
    );
  background-size: 400% 100%;
  border-radius: var(--app-radius, 12px);
  animation: analytics-loading 1.4s ease infinite;
}

.analytics-empty {
  display: grid;
  gap: 8px;
  place-content: center;
  justify-items: center;
  min-height: 280px;
  color: var(--el-text-color-secondary);
  background: var(--el-bg-color);
  border: 1px dashed var(--el-border-color);
  border-radius: var(--app-radius, 12px);
}

.analytics-empty .el-icon {
  font-size: 32px;
}

.analytics-empty p {
  margin: 0;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.analytics-empty span {
  font-size: 12px;
}

@keyframes analytics-loading {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}

@media (width <= 1100px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }

  .trend-card {
    grid-row: auto;
  }
}

@media (width <= 640px) {
  .trend-insights {
    grid-template-columns: 1fr;
  }

  .analytics-loading {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .analytics-loading span {
    animation: none;
  }
}
</style>
