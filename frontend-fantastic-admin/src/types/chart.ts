import type { ECOption } from '@/plugins/echarts'

export type MrrChartRenderer = 'canvas' | 'svg'

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
