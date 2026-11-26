import { afterEach, describe, expect, it } from 'vitest'
import { applyMrrChartTheme, getMrrChartThemeTokens, mrrChartColorToken } from './chart-theme'

afterEach(() => {
  document.documentElement.classList.remove('dark')
  document.documentElement.style.removeProperty('--mrr-tone-success-ink')
  document.documentElement.style.removeProperty('--mrr-tone-primary-ink')
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

  it('在主题重绘时解析语义色，保留显式颜色及原数据', () => {
    const option = { series: [{ type: 'line' as const, data: [0, 2], lineStyle: { color: mrrChartColorToken('primary') }, itemStyle: { color: '#112233' } }] }
    const tokens = getMrrChartThemeTokens()
    const first = applyMrrChartTheme(option, { ...tokens, palette: ['#123456', ...tokens.palette.slice(1)] })
    const second = applyMrrChartTheme(option, { ...tokens, palette: ['#654321', ...tokens.palette.slice(1)] })
    expect(first.series).toMatchObject([{ data: [0, 2], lineStyle: { color: '#123456' }, itemStyle: { color: '#112233' } }])
    expect(second.series).toMatchObject([{ data: [0, 2], lineStyle: { color: '#654321' } }])
    expect(option.series[0].lineStyle.color).toBe('var(--color-primary)')
  })

  it('暗色图表使用语义前景而非按钮填充色', () => {
    document.documentElement.classList.add('dark')
    document.documentElement.style.setProperty('--mrr-tone-primary-ink', '#adc6ff')
    expect(getMrrChartThemeTokens().palette[0]).toBe('#adc6ff')
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
