import { afterEach, describe, expect, it } from 'vitest'
import { applyMrrChartTheme } from './chart-theme'

afterEach(() => {
  delete document.documentElement.dataset.motion
  document.documentElement.style.removeProperty('--mrr-chart-axis-font-size')
  document.documentElement.style.removeProperty('--mrr-chart-legend-font-size')
})

describe('图表视觉契约', () => {
  it('关闭动效时覆盖系列图表的动画开关，保留真实数据', () => {
    document.documentElement.dataset.motion = 'off'
    const series = [{ type: 'line' as const, data: [0, 3, 8] }]
    const themed = applyMrrChartTheme({ animation: true, series })
    expect(themed.animation).toBe(false)
    expect(themed.series).toBe(series)
  })

  it('坐标与图例读取公共字号，保持调用方明确的轴和数据范围', () => {
    document.documentElement.style.setProperty('--mrr-chart-axis-font-size', '13px')
    document.documentElement.style.setProperty('--mrr-chart-legend-font-size', '14px')
    const result = applyMrrChartTheme({ xAxis: { type: 'value', min: 0, max: 100 }, legend: {}, tooltip: {} })
    expect(result.xAxis).toMatchObject({ min: 0, max: 100, axisLabel: { fontSize: 13 } })
    expect(result.legend).toMatchObject({ textStyle: { fontSize: 14 } })
    expect(result.tooltip).toMatchObject({ confine: true })
  })
})
