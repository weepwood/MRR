import type { HealthInfo, StatisticsSummary } from '@/api/types'
import { getStatisticsSummary } from '@/api/modules/statistics'
import { getSystemHealth } from '@/api/modules/system'

export type DashboardSource = 'statistics' | 'health'
export interface DashboardMetric { key: string, label: string, value: string, tone?: 'success' | 'warning' | 'danger', progress?: number }
export interface DashboardSnapshot { metrics: DashboardMetric[], bars?: { label: string, value: number }[] }
export const sourceMetrics: Record<DashboardSource, { key: string, label: string }[]> = {
  statistics: [{ key: 'records', label: '统计记录' }, { key: 'pages', label: '统计总页数' }, { key: 'bah', label: '不同病案号数' }],
  health: [{ key: 'database', label: '数据库状态' }, { key: 'heap', label: '堆内存使用' }],
}

function numberValue(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0 ? value.toLocaleString('zh-CN') : '—'
}
export function statisticsSnapshot(data: StatisticsSummary): DashboardSnapshot {
  return {
    metrics: [
      { ...sourceMetrics.statistics[0], value: numberValue(data.total?.totalRecords) },
      { ...sourceMetrics.statistics[1], value: numberValue(data.total?.totalPages) },
      { ...sourceMetrics.statistics[2], value: numberValue(data.uniqueBAHCount) },
    ],
    bars: (data.byType ?? []).filter(item => typeof item.recordCount === 'number' && Number.isFinite(item.recordCount) && item.recordCount >= 0).map(item => ({ label: item.type || '未分类', value: item.recordCount! })).sort((a, b) => b.value - a.value).slice(0, 5),
  }
}
export function healthSnapshot(data: HealthInfo): DashboardSnapshot {
  const db = String(data.components?.database?.status ?? '').toUpperCase()
  const raw = (data.components?.memory as { usagePercent?: string } | undefined)?.usagePercent
  const percent = typeof raw === 'string' && /^\d+(?:\.\d+)?%$/.test(raw) ? Number(raw.slice(0, -1)) : undefined
  return { metrics: [
    { ...sourceMetrics.health[0], value: db === 'UP' ? '可连接' : db === 'DOWN' ? '连接异常' : '未知', tone: db === 'UP' ? 'success' : db === 'DOWN' ? 'danger' : 'warning' },
    { ...sourceMetrics.health[1], value: percent !== undefined && percent <= 100 ? `${percent}%` : '—', progress: percent !== undefined && percent <= 100 ? percent : undefined, tone: percent !== undefined && percent >= 90 ? 'warning' : undefined },
  ] }
}
export async function loadDashboardSource(source: DashboardSource, signal?: AbortSignal): Promise<DashboardSnapshot> {
  if (source === 'statistics') {
    const result = await getStatisticsSummary(signal)
    if (!result.data) { throw new Error('missing statistics') }
    return statisticsSnapshot(result.data)
  }
  const result = await getSystemHealth(signal)
  if (!result.data) { throw new Error('missing health') }
  return healthSnapshot(result.data)
}
