import { describe, expect, it } from 'vitest'
import { healthSnapshot, statisticsSnapshot } from './dashboard-data'
import { mergeWidgetPreferences } from './dashboard-widgets'

describe('dashboard snapshot', () => {
  it('distinguishes missing values from a measured zero', () => {
    const summary = statisticsSnapshot({ total: { totalRecords: 0 }, byType: [{ type: 'a', recordCount: 0 }, { type: 'b' }, { type: 'c', recordCount: 4 }] })
    expect(summary.metrics.map(m => m.value)).toEqual(['0', '—', '—'])
    expect(summary.bars).toEqual([{ label: 'c', value: 4 }, { label: 'a', value: 0 }])
  })
  it('does not treat application UP as database UP or invent heap usage', () => {
    expect(healthSnapshot({ status: 'UP' }).metrics.map(m => m.value)).toEqual(['未知', '—'])
    const result = healthSnapshot({ status: 'UP', components: { database: { status: 'DOWN' }, memory: { usagePercent: '92.50%' } } })
    expect(result.metrics[0]).toMatchObject({ value: '连接异常', tone: 'danger' })
    expect(result.metrics[1]).toMatchObject({ value: '92.5%', progress: 92.5, tone: 'warning' })
  })
  it('validates persisted metric keys while retaining layout and custom title', () => {
    const defs = [{ id: 'statistics', title: '统计', description: '说明', icon: '', path: '/statistics', defaultSize: 'wide' as const, availableMetrics: ['records', 'pages'] }]
    const stored = [{ id: 'statistics', title: '我的统计', size: 'large', color: 'green', visible: true, pinned: true, order: 0, metricKeys: ['pages', 'secret'], showDescription: false, compact: true }]
    expect(mergeWidgetPreferences(defs, stored)[0]).toMatchObject({ title: '我的统计', size: 'large', color: 'green', pinned: true, metricKeys: ['pages'], showDescription: false, compact: true })
  })
})
