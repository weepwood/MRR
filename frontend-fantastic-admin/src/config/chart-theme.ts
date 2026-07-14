import type { ECOption } from '@/plugins/echarts'
import type { MrrChartThemeTokens } from '@/types/chart'

export const MRR_CHART_PALETTE = [
  '#2563eb',
  '#14b8a6',
  '#8b5cf6',
  '#f59e0b',
  '#ef4444',
  '#64748b',
]

function cssVariable(name: string, fallback: string) {
  if (typeof window === 'undefined') {
    return fallback
  }

  const value = window.getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

export function getMrrChartThemeTokens(): MrrChartThemeTokens {
  const dark = typeof document !== 'undefined' && document.documentElement.classList.contains('dark')

  return {
    dark,
    background: cssVariable('--el-bg-color', dark ? '#141414' : '#ffffff'),
    textPrimary: cssVariable('--el-text-color-primary', dark ? '#e5e7eb' : '#1f2937'),
    textSecondary: cssVariable('--el-text-color-secondary', dark ? '#a3a6ad' : '#64748b'),
    border: cssVariable('--el-border-color', dark ? '#4c4d4f' : '#dcdfe6'),
    splitLine: cssVariable('--el-border-color-lighter', dark ? '#363637' : '#ebeef5'),
    tooltipBackground: dark ? 'rgba(24, 24, 27, 0.96)' : 'rgba(255, 255, 255, 0.98)',
    tooltipBorder: dark ? '#52525b' : '#e2e8f0',
    palette: MRR_CHART_PALETTE,
  }
}

function themeAxis(axis: unknown, tokens: MrrChartThemeTokens) {
  if (!axis || typeof axis !== 'object') {
    return axis
  }

  const source = axis as Record<string, unknown>
  return {
    ...source,
    axisLine: {
      show: true,
      ...(source.axisLine as Record<string, unknown> | undefined),
      lineStyle: {
        color: tokens.border,
        ...((source.axisLine as Record<string, unknown> | undefined)?.lineStyle as Record<string, unknown> | undefined),
      },
    },
    axisTick: {
      show: false,
      ...(source.axisTick as Record<string, unknown> | undefined),
    },
    axisLabel: {
      color: tokens.textSecondary,
      fontSize: 11,
      ...(source.axisLabel as Record<string, unknown> | undefined),
    },
    nameTextStyle: {
      color: tokens.textSecondary,
      fontSize: 11,
      ...(source.nameTextStyle as Record<string, unknown> | undefined),
    },
    splitLine: {
      show: true,
      ...(source.splitLine as Record<string, unknown> | undefined),
      lineStyle: {
        color: tokens.splitLine,
        type: 'dashed',
        ...((source.splitLine as Record<string, unknown> | undefined)?.lineStyle as Record<string, unknown> | undefined),
      },
    },
  }
}

function themeAxes(axis: unknown, tokens: MrrChartThemeTokens) {
  return Array.isArray(axis)
    ? axis.map(item => themeAxis(item, tokens))
    : themeAxis(axis, tokens)
}

export function applyMrrChartTheme(option: ECOption, tokens = getMrrChartThemeTokens()): ECOption {
  return {
    ...option,
    color: option.color ?? tokens.palette,
    backgroundColor: 'transparent',
    animationDuration: option.animationDuration ?? 420,
    animationDurationUpdate: option.animationDurationUpdate ?? 260,
    animationEasing: option.animationEasing ?? 'cubicOut',
    textStyle: {
      color: tokens.textSecondary,
      fontFamily: 'Inter, "PingFang SC", "Microsoft YaHei", sans-serif',
      ...(option.textStyle ?? {}),
    },
    tooltip: option.tooltip
      ? {
          backgroundColor: tokens.tooltipBackground,
          borderColor: tokens.tooltipBorder,
          borderWidth: 1,
          padding: [10, 12],
          textStyle: {
            color: tokens.textPrimary,
            fontSize: 12,
          },
          extraCssText: 'border-radius:10px;box-shadow:0 12px 32px rgba(15,23,42,.12);',
          ...option.tooltip,
        }
      : undefined,
    legend: option.legend
      ? {
          itemWidth: 12,
          itemHeight: 8,
          itemGap: 18,
          textStyle: {
            color: tokens.textSecondary,
            fontSize: 11,
          },
          ...option.legend,
        }
      : undefined,
    xAxis: themeAxes(option.xAxis, tokens) as ECOption['xAxis'],
    yAxis: themeAxes(option.yAxis, tokens) as ECOption['yAxis'],
  }
}
