import type { ECOption } from '@/plugins/echarts'

// plugins/echarts.ts 只注册了 CanvasRenderer，这里同步收窄可选值，
// 避免类型层面仍允许传 'svg' 而运行时因渲染器未注册而失败。
export type MrrChartRenderer = 'canvas'

export interface MrrChartSeries {
  name: string
  data: number[]
  color?: string
}

export interface MrrLineSeries extends MrrChartSeries {
  area?: boolean
  smooth?: boolean
  dashed?: boolean
  symbol?: boolean
}

export interface MrrBarSeries extends MrrChartSeries {
  stack?: string
}

export interface MrrChartCountItem {
  label: string
  count: number
  color?: string
}

export interface MrrRequestHeatmapItem {
  date: string
  total: number
  error?: number
  avgClientDurationMs?: number
  avgServerDurationMs?: number
}

export interface MrrChartThemeTokens {
  dark: boolean
  background: string
  textPrimary: string
  textSecondary: string
  border: string
  splitLine: string
  tooltipBackground: string
  tooltipBorder: string
  palette: string[]
}

export interface UseMrrChartOptions {
  renderer?: MrrChartRenderer
  autoresize?: boolean
  loadingText?: string
}

export type MrrChartOption = ECOption
