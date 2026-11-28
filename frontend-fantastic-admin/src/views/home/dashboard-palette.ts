export const widgetPalette = [
  { value: 'default', label: '默认', accent: 'var(--mrr-primary)', surface: 'var(--mrr-card)' },
  { value: 'blue', label: '蓝色', accent: 'var(--el-color-primary)', surface: 'var(--mrr-tone-primary-soft)' },
  { value: 'green', label: '绿色', accent: 'var(--el-color-success)', surface: 'var(--mrr-tone-success-soft)' },
  { value: 'orange', label: '橙色', accent: 'var(--el-color-warning)', surface: 'var(--mrr-tone-warning-soft)' },
  { value: 'red', label: '红色', accent: 'var(--el-color-danger)', surface: 'var(--mrr-tone-danger-soft)' },
  { value: 'purple', label: '紫色', accent: 'var(--widget-violet-accent)', surface: 'var(--mrr-tone-violet-soft)' },
  { value: 'cyan', label: '湖蓝', accent: 'var(--widget-cyan)', surface: 'var(--widget-cyan-soft)' },
  { value: 'teal', label: '青绿', accent: 'var(--widget-teal)', surface: 'var(--widget-teal-soft)' },
  { value: 'pink', label: '玫粉', accent: 'var(--widget-pink)', surface: 'var(--widget-pink-soft)' },
  { value: 'amber', label: '琥珀', accent: 'var(--widget-amber)', surface: 'var(--widget-amber-soft)' },
  { value: 'indigo', label: '靛蓝', accent: 'var(--widget-indigo)', surface: 'var(--widget-indigo-soft)' },
  { value: 'slate', label: '岩灰', accent: 'var(--widget-slate)', surface: 'var(--widget-slate-soft)' },
] as const

export type DashboardWidgetColor = typeof widgetPalette[number]['value']
export function isWidgetColor(value: unknown): value is DashboardWidgetColor {
  return widgetPalette.some(item => item.value === value)
}
export function paletteStyle(color: DashboardWidgetColor, backgroundColor: DashboardWidgetColor) {
  const icon = widgetPalette.find(item => item.value === color) ?? widgetPalette[0]
  const background = widgetPalette.find(item => item.value === backgroundColor) ?? widgetPalette[0]
  return { '--widget-accent': icon.accent, '--widget-icon-surface': color === 'default' ? 'var(--mrr-tone-primary-soft)' : icon.surface, '--widget-chosen-surface': background.surface }
}

export const appearanceModes = [
  { value: 'neutral', label: '经典中性' },
  { value: 'tint', label: '柔和色彩' },
  { value: 'focus', label: '业务聚焦' },
  { value: 'custom', label: '完全自定义' },
] as const
export type DashboardAppearanceMode = typeof appearanceModes[number]['value']
export interface DashboardAppearance { mode: DashboardAppearanceMode, strength: number }
export function normalizeAppearance(input: unknown): DashboardAppearance {
  const value = input && typeof input === 'object' ? input as Partial<DashboardAppearance> : {}
  return {
    mode: appearanceModes.some(item => item.value === value.mode) ? value.mode! : 'focus',
    strength: typeof value.strength === 'number' && Number.isFinite(value.strength) ? Math.min(10, Math.max(0, Math.round(value.strength))) : 4,
  }
}
export function widgetAppearanceStyle(color: DashboardWidgetColor, background: DashboardWidgetColor, id: string, appearance: DashboardAppearance) {
  const palette = paletteStyle(color, background)
  const strength = appearance.mode === 'tint' || (appearance.mode === 'focus' && id === 'archive') ? appearance.strength / 100 : 0
  const tint = appearance.mode === 'focus' ? 'blue' : color
  return {
    ...palette,
    '--widget-surface': appearance.mode === 'custom' ? palette['--widget-chosen-surface'] : 'var(--mrr-card)',
    '--widget-tint-rgb': `var(--widget-rgb-${tint})`,
    '--widget-tint-alpha': String(strength),
  }
}
