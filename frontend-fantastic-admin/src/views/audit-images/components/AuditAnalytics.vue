<script setup lang="ts">
import type { ImageAuditAnalytics } from '@/api/types'
import type { MrrLineSeries } from '@/types/chart'
import { DataAnalysis, Files, Timer, User, Warning } from '@element-plus/icons-vue'
import { computed } from 'vue'
import { MrrChartCard, MrrLineChart } from '@/components/charts'

defineOptions({ name: 'AuditAnalytics' })

const props = withDefaults(defineProps<{
  analytics: ImageAuditAnalytics
  loading?: boolean
}>(), { loading: false })

const emit = defineEmits<{
  exportUser: [username: string]
  exportTarget: [target: string]
}>()

const topTargets = computed(() => ((props.analytics as ImageAuditAnalytics & { topTargets?: { label: string, count: number }[] }).topTargets ?? []))
const maxUserCount = computed(() => Math.max(1, ...props.analytics.topUsers.map(item => item.count)))
const maxTargetCount = computed(() => Math.max(1, ...topTargets.value.map(item => item.count)))
const abnormalRate = computed(() => props.analytics.totalAccesses
  ? props.analytics.abnormalAccesses / props.analytics.totalAccesses * 100
  : 0)
const trendCategories = computed(() => props.analytics.trend.map(item => formatDate(item.date)))
const trendSeries = computed<MrrLineSeries[]>(() => [{
  name: '访问次数',
  data: props.analytics.trend.map(item => item.count),
  color: '#2563eb',
  area: true,
  smooth: true,
}])

function formatNumber(value: number, digits = 0) {
  return value.toLocaleString('zh-CN', { maximumFractionDigits: digits })
}

function formatDate(value: string) {
  const parts = value.split('-')
  return parts.length === 3 ? `${parts[1]}-${parts[2]}` : value
}
</script>

<template>
  <section class="analytics-shell" :aria-busy="loading">
    <div class="metric-grid">
      <el-card shadow="never" class="metric-card metric-card--blue">
        <el-icon><User /></el-icon>
        <div><span>访问用户数</span><strong>{{ loading ? '—' : formatNumber(analytics.uniqueUsers) }}</strong></div>
      </el-card>
      <el-card shadow="never" class="metric-card metric-card--teal">
        <el-icon><Files /></el-icon>
        <div><span>被访问病历数</span><strong>{{ loading ? '—' : formatNumber(analytics.uniqueTargets) }}</strong></div>
      </el-card>
      <el-card shadow="never" class="metric-card metric-card--blue">
        <el-icon><DataAnalysis /></el-icon>
        <div><span>总访问次数</span><strong>{{ loading ? '—' : formatNumber(analytics.totalAccesses) }}</strong></div>
      </el-card>
      <el-card shadow="never" class="metric-card metric-card--danger">
        <el-icon><Warning /></el-icon>
        <div><span>异常访问告警</span><strong>{{ loading ? '—' : formatNumber(analytics.abnormalAccesses) }}</strong><small>{{ abnormalRate.toFixed(1) }}%</small></div>
      </el-card>
      <el-card shadow="never" class="metric-card metric-card--slate">
        <el-icon><Timer /></el-icon>
        <div><span>平均响应耗时</span><strong>{{ loading ? '—' : formatNumber(analytics.averageDurationMs, 1) }}</strong><small>ms</small></div>
      </el-card>
    </div>

    <div class="ranking-grid">
      <el-card shadow="never" class="ranking-card">
        <template #header>
          <div class="section-title"><div><strong>按用户查看</strong><span>查看每位用户的病历访问量</span></div><el-tag type="info" effect="plain">可导出</el-tag></div>
        </template>
        <el-table :data="analytics.topUsers" size="small" table-layout="fixed">
          <el-table-column type="index" label="排名" width="64" />
          <el-table-column prop="label" label="用户" min-width="140" show-overflow-tooltip />
          <el-table-column label="访问次数" min-width="220">
            <template #default="{ row }">
              <div class="count-cell"><div class="bar-track"><span :style="{ width: `${row.count / maxUserCount * 100}%` }" /></div><b>{{ formatNumber(row.count) }}</b></div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="right">
            <template #default="{ row }"><el-button size="small" type="primary" plain @click="emit('exportUser', row.label)">导出访问明细</el-button></template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never" class="ranking-card">
        <template #header>
          <div class="section-title"><div><strong>按病历查看</strong><span>查看每份病历的访问用户</span></div><el-tag type="success" effect="plain">可导出</el-tag></div>
        </template>
        <el-table :data="topTargets" size="small" table-layout="fixed">
          <el-table-column type="index" label="排名" width="64" />
          <el-table-column prop="label" label="病历号" min-width="150" show-overflow-tooltip />
          <el-table-column label="总访问次数" min-width="220">
            <template #default="{ row }">
              <div class="count-cell"><div class="bar-track bar-track--teal"><span :style="{ width: `${row.count / maxTargetCount * 100}%` }" /></div><b>{{ formatNumber(row.count) }}</b></div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="right">
            <template #default="{ row }"><el-button size="small" type="success" plain @click="emit('exportTarget', row.label)">导出访问人员</el-button></template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <div class="trend-grid">
      <MrrChartCard title="用户访问趋势" description="按日期统计筛选范围内的访问次数" :loading="loading" :empty="!analytics.trend.length" empty-description="暂无趋势数据">
        <MrrLineChart :categories="trendCategories" :series="trendSeries" y-axis-name="访问次数" unit="次" :show-legend="false" :height="250" />
      </MrrChartCard>
      <MrrChartCard title="病历访问趋势" description="用于观察病历访问量随时间的变化" :loading="loading" :empty="!analytics.trend.length" empty-description="暂无趋势数据">
        <MrrLineChart :categories="trendCategories" :series="trendSeries" y-axis-name="访问次数" unit="次" :show-legend="false" :height="250" />
      </MrrChartCard>
    </div>
  </section>
</template>

<style scoped>
.analytics-shell { display: grid; gap: 16px; }
.metric-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 14px; }
.metric-card :deep(.el-card__body) { display: flex; gap: 14px; align-items: center; min-height: 92px; }
.metric-card .el-icon { display: grid; place-items: center; width: 48px; height: 48px; font-size: 24px; color: #2563eb; background: #eff6ff; border-radius: 50%; }
.metric-card div { display: grid; gap: 3px; }
.metric-card span { font-size: 13px; color: var(--el-text-color-secondary); }
.metric-card strong { font-size: 26px; line-height: 1; color: var(--el-text-color-primary); }
.metric-card small { font-size: 11px; color: var(--el-text-color-secondary); }
.metric-card--teal .el-icon { color: #0f9f8f; background: #ecfdf5; }
.metric-card--danger .el-icon { color: #ef4444; background: #fef2f2; }
.metric-card--slate .el-icon { color: #64748b; background: #f1f5f9; }
.ranking-grid, .trend-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.section-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.section-title > div { display: grid; gap: 4px; }
.section-title strong { font-size: 16px; }
.section-title span { font-size: 12px; color: var(--el-text-color-secondary); }
.count-cell { display: grid; grid-template-columns: minmax(90px, 1fr) 64px; gap: 12px; align-items: center; }
.count-cell b { font-variant-numeric: tabular-nums; text-align: right; }
.bar-track { height: 8px; overflow: hidden; background: var(--el-fill-color-light); border-radius: 999px; }
.bar-track span { display: block; height: 100%; background: #2563eb; border-radius: inherit; }
.bar-track--teal span { background: #14b8a6; }
@media (max-width: 1200px) { .metric-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 900px) { .metric-grid, .ranking-grid, .trend-grid { grid-template-columns: 1fr; } }
</style>
