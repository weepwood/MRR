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

/** Canvas 色彩解析只接受确定的 sRGB 色值，避免直接传入 color-mix 或现代 HSL 语法。 */
function resolvedCssColor(name: string, fallback: string) {
  const value = cssVariable(name, fallback)
  if (/^#[0-9a-f]{6}$/i.test(value)) { return value }
  if (typeof document === 'undefined' || !document.body) { return fallback }
  const probe = document.createElement('span')
  probe.style.cssText = 'position:absolute;visibility:hidden;pointer-events:none'
  probe.style.color = value
  if (!probe.style.color) { return fallback }
  document.body.appendChild(probe)
  const computed = window.getComputedStyle(probe).color
  probe.remove()
  if (/^rgba?\(/.test(computed)) { return computed }
  const srgb = computed.match(/^color\(srgb\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)\)$/)
  if (srgb) {
    return `rgb(${srgb.slice(1).map(channel => Math.round(Math.min(1, Math.max(0, Number(channel))) * 255)).join(', ')})`
  }
  return fallback
}

const DARK_INK_TOKENS = ['primary', 'teal', 'violet', 'warning', 'danger', 'neutral'] as const
const DARK_INK_FALLBACKS = ['#adc6ff', '#70d2c3', '#c2a5ff', '#f2bd68', '#ff9797', '#a6b0bf']

function paletteColor(index: number) {
  const [token, fallback] = PALETTE_TOKENS[index]
  if (typeof document !== 'undefined' && document.documentElement.classList.contains('dark')) {
    return resolvedCssColor(`--mrr-tone-${DARK_INK_TOKENS[index]}-ink`, DARK_INK_FALLBACKS[index])
  }
  return resolvedCssColor(token, fallback)
}

/** 静态回退色板，同时作为「图表可以使用哪些色相」的清单。 */
export const MRR_CHART_PALETTE: readonly string[] = PALETTE_TOKENS.map(([, fallback]) => fallback)

/** 运行时色板：按令牌实时解析，跟随主题色。 */
export function getMrrChartPalette(): string[] {
  return PALETTE_TOKENS.map((_, index) => paletteColor(index))
}

/** 单个语义色，供单序列图表显式指定颜色。 */
export function mrrChartColor(name: 'primary' | 'neutral' | 'danger' | 'warning' | 'teal' | 'violet'): string {
  const index = { primary: 0, teal: 1, violet: 2, warning: 3, danger: 4, neutral: 5 }[name]
  return paletteColor(index)
}

/** Deferred semantic color for chart options; resolve at every theme render. */
export function mrrChartColorToken(name: Parameters<typeof mrrChartColor>[0]): string {
  const index = { primary: 0, teal: 1, violet: 2, warning: 3, danger: 4, neutral: 5 }[name]
  return `var(${PALETTE_TOKENS[index][0]})`
}

function resolveSeriesColors(value: unknown, palette: string[]): unknown {
  if (Array.isArray(value)) {
    const next = value.map(item => resolveSeriesColors(item, palette))
    return next.some((item, index) => item !== value[index]) ? next : value
  }
  if (!value || typeof value !== 'object' || Object.getPrototypeOf(value) !== Object.prototype) { return value }
  let result: Record<string, unknown> | undefined
  for (const [key, item] of Object.entries(value)) {
    const index = key === 'color' && typeof item === 'string'
      ? PALETTE_TOKENS.findIndex(([token]) => item === `var(${token})`)
      : -1
    const resolved = index >= 0 ? palette[index] : resolveSeriesColors(item, palette)
    if (resolved !== item) { (result ??= { ...value })[key] = resolved }
  }
  return result ?? value
}

export function getMrrChartThemeTokens(): MrrChartThemeTokens {
  const dark = typeof document !== 'undefined' && document.documentElement.classList.contains('dark')

  return {
    dark,
    background: resolvedCssColor('--el-bg-color', dark ? '#141414' : '#ffffff'),
    textPrimary: resolvedCssColor('--el-text-color-primary', dark ? '#e5e7eb' : '#1f2937'),
    textSecondary: resolvedCssColor('--el-text-color-secondary', dark ? '#a3a6ad' : '#64748b'),
    border: resolvedCssColor('--el-border-color', dark ? '#4c4d4f' : '#dcdfe6'),
    splitLine: resolvedCssColor('--el-border-color-lighter', dark ? '#363637' : '#ebeef5'),
    tooltipBackground: resolvedCssColor('--mrr-popover', dark ? '#1c2330' : '#ffffff'),
    tooltipBorder: resolvedCssColor('--mrr-border', dark ? '#343d4b' : '#e2e8f0'),
    palette: getMrrChartPalette(),
  }
}

function chartFontSize(token: string) {
  const value = Number.parseFloat(cssVariable(token, '12px'))
  return Number.isFinite(value) && value > 0 ? value : 12
}

export function themeChartAnimationEnabled() {
  return typeof document === 'undefined' || (
    document.documentElement.dataset.motion !== 'off'
    && !(typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches)
  )
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
      fontSize: chartFontSize('--mrr-chart-axis-font-size'),
      ...(source.axisLabel as Record<string, unknown> | undefined),
    },
    nameTextStyle: {
      color: tokens.textSecondary,
      fontSize: chartFontSize('--mrr-chart-axis-font-size'),
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
    series: resolveSeriesColors(option.series, tokens.palette) as ECOption['series'],
    backgroundColor: 'transparent',
    animation: themeChartAnimationEnabled() ? option.animation : false,
    animationDuration: option.animationDuration ?? 420,
    animationDurationUpdate: option.animationDurationUpdate ?? 260,
    animationEasing: option.animationEasing ?? 'cubicOut',
    textStyle: {
      color: tokens.textSecondary,
      fontFamily: cssVariable('--mrr-font-sans', 'Inter, "HarmonyOS Sans SC", "Microsoft YaHei", sans-serif'),
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
          confine: true,
          extraCssText: `border-radius:${cssVariable('--mrr-radius-md', '8px')};box-shadow:${cssVariable('--mrr-shadow-md', '0 8px 24px rgba(15,23,42,.12)')};`,
          ...option.tooltip,
        }
      : undefined,
    legend: option.legend
      ? {
          itemWidth: 12,
          itemHeight: 8,
          itemGap: 12,
          textStyle: {
            color: tokens.textSecondary,
            fontSize: chartFontSize('--mrr-chart-legend-font-size'),
          },
          ...option.legend,
        }
      : undefined,
    xAxis: themeAxes(option.xAxis, tokens) as ECOption['xAxis'],
    yAxis: themeAxes(option.yAxis, tokens) as ECOption['yAxis'],
  }
}
