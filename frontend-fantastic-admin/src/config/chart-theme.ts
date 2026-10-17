import type { ECOption } from '@/plugins/echarts'
import type { MrrChartThemeTokens } from '@/types/chart'

/*
 * 图表色板的唯一事实源。
 *
 * ECharts 的 canvas / svg 渲染器都无法解析 CSS 变量，必须在运行时读取计算值，
 * 因此这里的每个色相都「令牌 + 静态回退」成对声明：
 * - 令牌保证图表跟随主题色与语义色（用户改主题色时图表同步变化）；
 * - 静态回退保证 SSR、单测和令牌缺失时仍有确定颜色，同时充当色板文档。
 *
 * 分类色板只使用 MRR 令牌里的九个色相，不再引入 Tailwind 的亮色（#14b8a6、
 * #f59e0b、#ef4444 等）——那是本应用此前出现「图表比界面鲜艳」的根因。
 */
const PALETTE_TOKENS: ReadonlyArray<readonly [token: string, fallback: string]> = [
  ['--color-primary', '#2563eb'],
  ['--mrr-tone-teal', '#0f766e'],
  ['--mrr-tone-violet', '#7c3aed'],
  ['--mrr-tone-warning', '#b45f06'],
  ['--mrr-tone-danger', '#d13c3c'],
  ['--mrr-tone-neutral', '#64748b'],
]

function cssVariable(name: string, fallback: string) {
  if (typeof window === 'undefined') {
    return fallback
  }

  const value = window.getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

/** 静态回退色板，同时作为「图表可以使用哪些色相」的清单。 */
export const MRR_CHART_PALETTE: readonly string[] = PALETTE_TOKENS.map(([, fallback]) => fallback)

/** 运行时色板：按令牌实时解析，跟随主题色。 */
export function getMrrChartPalette(): string[] {
  return PALETTE_TOKENS.map(([token, fallback]) => cssVariable(token, fallback))
}

/** 单个语义色，供单序列图表显式指定颜色。 */
export function mrrChartColor(name: 'primary' | 'neutral' | 'danger' | 'warning' | 'teal' | 'violet'): string {
  const index = { primary: 0, teal: 1, violet: 2, warning: 3, danger: 4, neutral: 5 }[name]
  const [token, fallback] = PALETTE_TOKENS[index]
  return cssVariable(token, fallback)
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
    palette: getMrrChartPalette(),
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
