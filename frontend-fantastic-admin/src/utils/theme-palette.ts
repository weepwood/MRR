/**
 * MRR 的可选完整调色板。
 *
 * Gr8r 色值来自 weepwood/gr8r-studio-ui/src/tokens.css (v0.2.0)，
 * 仅迁移颜色角色，不改变 MRR 的布局、圆角或组件行为。
 * CSS 变量由运行时写入 <html>；退出调色板时必须全部移除，避免污染默认主题。
 */
export type ThemePaletteId = 'mrr' | 'gr8r'
export type ThemePaletteScheme = 'light' | 'dark'

export interface Gr8rColors {
  canvas: string
  sidebar: string
  surface: string
  mutedSurface: string
  raisedSurface: string
  line: string
  strongLine: string
  controlLine: string
  ink: string
  secondaryInk: string
  mutedInk: string
  accent: string
  accentContrast: string
  success: string
  warning: string
  danger: string
  info: string
  violet: string
  teal: string
  rose: string
}

export const GR8R_COLORS: Record<ThemePaletteScheme, Readonly<Gr8rColors>> = {
  light: {
    canvas: '#FAF9F7',
    sidebar: '#F3F2EF',
    surface: '#FFFFFF',
    mutedSurface: '#F7F6F3',
    raisedSurface: '#F0EFEB',
    line: '#E7E5DF',
    strongLine: '#D9D6CE',
    controlLine: '#918B83',
    ink: '#242320',
    secondaryInk: '#65625C',
    mutedInk: '#6E6A63',
    accent: '#5362CE',
    accentContrast: '#FFFFFF',
    success: '#226C4F',
    warning: '#886111',
    danger: '#B63E38',
    info: '#286F9E',
    violet: '#6E4EAA',
    teal: '#18776E',
    rose: '#A83A64',
  },
  dark: {
    canvas: '#1B1B1A',
    sidebar: '#171716',
    surface: '#222220',
    mutedSurface: '#282826',
    raisedSurface: '#32312E',
    line: '#32312E',
    strongLine: '#45443F',
    controlLine: '#929087',
    ink: '#EAE8E3',
    secondaryInk: '#B5B1A9',
    mutedInk: '#A09C93',
    accent: '#9DA8F7',
    accentContrast: '#1D2449',
    success: '#76C3A0',
    warning: '#E6B65B',
    danger: '#EC928C',
    info: '#88BBDF',
    violet: '#B99BDF',
    teal: '#78D3C9',
    rose: '#EF9AB9',
  },
}

export const themePalettePresets = [
  {
    id: 'mrr',
    name: 'MRR 经典',
    description: '原有冷灰与蓝色体系，可自定义主色',
    preview: {
      canvas: '#F4F7FB',
      sidebar: '#FFFFFF',
      surface: '#FFFFFF',
      ink: '#1E293B',
      accent: '#2563EB',
      line: '#D9E2EC',
    },
  },
  {
    id: 'gr8r',
    name: '暖色扁平风格',
    description: '暖灰中性色、描边控件与紧凑密度，含明暗两套',
    preview: {
      canvas: GR8R_COLORS.light.canvas,
      sidebar: GR8R_COLORS.light.sidebar,
      surface: GR8R_COLORS.light.surface,
      ink: GR8R_COLORS.light.ink,
      accent: GR8R_COLORS.light.accent,
      line: GR8R_COLORS.light.line,
    },
  },
] as const satisfies ReadonlyArray<{ id: ThemePaletteId, name: string, description: string, preview: Record<'canvas' | 'sidebar' | 'surface' | 'ink' | 'accent' | 'line', string> }>

export function normalizeThemePalette(value: unknown): ThemePaletteId {
  return value === 'gr8r' ? 'gr8r' : 'mrr'
}

export function gr8rPrimary(scheme: ThemePaletteScheme): string {
  return GR8R_COLORS[scheme].accent
}

function parseColor(hex: string): [number, number, number] {
  const value = hex.replace('#', '')
  return [0, 2, 4].map(offset => Number.parseInt(value.slice(offset, offset + 2), 16)) as [number, number, number]
}

function mixHex(color: string, surface: string, strength: number): string {
  const a = parseColor(color)
  const b = parseColor(surface)
  return `#${a.map((channel, index) => Math.round(channel * strength + b[index] * (1 - strength)).toString(16).padStart(2, '0')).join('').toUpperCase()}`
}

/** shadcn 的自定义属性约定使用原始 H S% L% 三元组，而不是 #RRGGBB。 */
function hslTuple(hex: string): string {
  const [r, g, b] = parseColor(hex).map(channel => channel / 255)
  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  const delta = max - min
  const lightness = (max + min) / 2
  const saturation = delta === 0 ? 0 : delta / (1 - Math.abs(2 * lightness - 1))
  let hue = 0
  if (delta > 0) {
    if (max === r) {
      hue = ((g - b) / delta) % 6
    }
    else if (max === g) {
      hue = (b - r) / delta + 2
    }
    else {
      hue = (r - g) / delta + 4
    }
    hue = ((hue * 60) + 360) % 360
  }
  return `${Math.round(hue)} ${Math.round(saturation * 100)}% ${Math.round(lightness * 100)}%`
}

/** 生成包含布局表面、文本、导航、表单和状态语义的完整 MRR 变量映射。 */
export function gr8rCssVariables(scheme: ThemePaletteScheme): Record<string, string> {
  const c = GR8R_COLORS[scheme]
  const dark = scheme === 'dark'
  const onDanger = dark ? '#222220' : '#FFFFFF'
  /*
   * 导航选中项：白底药丸 + 发丝描边，不用主色浅填充。
   * 扁平风格把「颜色」留给主按钮与状态色，导航靠表面差与字重表达选中；
   * 深色下用 raisedSurface，否则白底会变成一块刺眼的亮斑。
   */
  const navActive = dark ? c.raisedSurface : c.surface
  const navActiveBorder = dark ? c.strongLine : c.line
  const focusRing = mixHex(c.accent, c.surface, dark ? 0.42 : 0.3)

  const css: Record<string, string> = {
    'color-scheme': scheme,

    // shadcn / UnoCSS 所使用的原始 HSL 令牌
    '--background': hslTuple(c.canvas),
    '--foreground': hslTuple(c.ink),
    '--card': hslTuple(c.surface),
    '--card-foreground': hslTuple(c.ink),
    '--popover': hslTuple(c.surface),
    '--popover-foreground': hslTuple(c.ink),
    '--primary-foreground': hslTuple(c.accentContrast),
    '--secondary': hslTuple(c.raisedSurface),
    '--secondary-foreground': hslTuple(c.ink),
    '--muted': hslTuple(c.mutedSurface),
    '--muted-foreground': hslTuple(c.secondaryInk),
    '--accent': hslTuple(c.raisedSurface),
    '--accent-foreground': hslTuple(c.ink),
    '--destructive': hslTuple(c.danger),
    '--destructive-foreground': hslTuple(onDanger),
    '--border': hslTuple(c.line),
    '--input': hslTuple(c.controlLine),

    // 真实布局容器
    '--g-main-area-bg': c.canvas,
    '--g-header-bg': c.surface,
    '--g-header-color': c.ink,
    '--g-header-menu-color': c.secondaryInk,
    '--g-header-menu-hover-bg': c.raisedSurface,
    '--g-header-menu-hover-color': c.ink,
    '--g-header-menu-active-bg': navActive,
    '--g-header-menu-active-color': c.ink,
    '--g-main-sidebar-bg': c.sidebar,
    '--g-main-sidebar-menu-color': c.secondaryInk,
    '--g-main-sidebar-menu-hover-bg': c.raisedSurface,
    '--g-main-sidebar-menu-hover-color': c.ink,
    '--g-main-sidebar-menu-active-bg': navActive,
    '--g-main-sidebar-menu-active-color': c.ink,
    '--g-sub-sidebar-bg': c.sidebar,
    '--g-sub-sidebar-menu-color': c.secondaryInk,
    '--g-sub-sidebar-menu-hover-bg': c.raisedSurface,
    '--g-sub-sidebar-menu-hover-color': c.ink,
    '--g-sub-sidebar-menu-active-bg': navActive,
    '--g-sub-sidebar-menu-active-color': c.ink,
    '--g-tabbar-bg': c.canvas,
    '--g-tabbar-dividers-bg': c.strongLine,
    '--g-tabbar-tab-color': c.secondaryInk,
    '--g-tabbar-tab-hover-bg': c.raisedSurface,
    '--g-tabbar-tab-hover-color': c.ink,
    '--g-tabbar-tab-active-bg': c.surface,
    '--g-tabbar-tab-active-color': c.ink,
    '--g-toolbar-bg': c.surface,

    // MRR 语义层及旧组件别名，避免局部残留原冷灰配色
    '--mrr-navigation-bg-solid': c.sidebar,
    '--mrr-navigation-border': c.line,
    '--mrr-navigation-hover': c.raisedSurface,
    // 主导航窄栏：底色沿用 raisedSurface，悬停再加深一档，保证栏内悬停可见。
    '--mrr-navigation-rail-bg': c.raisedSurface,
    '--mrr-navigation-rail-hover': mixHex(c.ink, c.raisedSurface, dark ? 0.08 : 0.06),
    '--mrr-navigation-active': navActive,
    '--mrr-navigation-active-foreground': c.ink,
    '--mrr-navigation-active-border': navActiveBorder,
    '--mrr-shell-divider': c.line,
    '--mrr-input': c.controlLine,
    // 控件底色改为表面白：暖色主题用「描边控件」而不是「填充控件」。
    '--mrr-control-bg': c.surface,
    '--mrr-control-bg-hover': c.mutedSurface,
    '--mrr-control-bg-disabled': c.mutedSurface,
    '--mrr-control-border': c.controlLine,
    '--mrr-control-border-hover': c.controlLine,
    // 按钮/指标卡 hover 的描边：比控件描边再柔和一档，避免在暖白卡面上读成「墨色框」。
    '--mrr-border-strong': c.strongLine,
    '--mrr-primary-foreground': c.accentContrast,
    '--mrr-destructive-foreground': onDanger,
    '--mrr-destructive-muted': mixHex(c.danger, c.surface, dark ? 0.2 : 0.1),
    '--mrr-ring': c.accent,
    // 焦点环提供实色版本：Chrome 86 上 color-mix() 会让整条 box-shadow 失效。
    '--mrr-focus-ring': `0 0 0 3px ${focusRing}`,
    '--mrr-accent-tone': c.violet,
    // 扁平层级：投影只做方向性提示，分层交给 1px 描边与表面亮度差。
    '--mrr-shell-shadow': dark ? '0 1px 2px rgb(0 0 0 / 30%)' : '0 1px 2px rgb(28 25 20 / 4%)',
    '--mrr-shadow-xs': dark ? '0 1px 2px rgb(0 0 0 / 30%)' : '0 1px 2px rgb(28 25 20 / 5%)',
    '--mrr-shadow-sm': dark ? '0 0 0 1px rgb(255 255 255 / 4%), 0 6px 16px -8px rgb(0 0 0 / 45%)' : '0 1px 2px rgb(28 25 20 / 4%), 0 4px 12px -6px rgb(28 25 20 / 10%)',
    '--mrr-shadow-md': dark ? '0 16px 40px -10px rgb(0 0 0 / 60%), 0 0 0 1px rgb(255 255 255 / 6%)' : '0 12px 32px -8px rgb(28 25 20 / 16%), 0 2px 6px -2px rgb(28 25 20 / 6%)',
    '--mrr-shadow-lg': dark ? '0 18px 44px -10px rgb(0 0 0 / 70%), 0 0 0 1px rgb(255 255 255 / 7%)' : '0 16px 40px -10px rgb(28 25 20 / 22%), 0 2px 6px -2px rgb(28 25 20 / 8%)',

    '--color-success': c.success,
    '--color-warning': c.warning,
    '--color-danger': c.danger,
    '--color-info': c.info,
    '--color-text': c.ink,
    '--color-text-muted': c.secondaryInk,
    '--color-text-placeholder': c.mutedInk,
    '--color-bg': c.surface,
    '--color-bg-warm': c.mutedSurface,
    '--color-bg-dark': c.canvas,
    '--color-border': c.line,
    '--color-border-strong': c.strongLine,

    '--mrr-tone-success': c.success,
    '--mrr-tone-warning': c.warning,
    '--mrr-tone-danger': c.danger,
    '--mrr-tone-info': c.info,
    '--mrr-tone-neutral': c.secondaryInk,
    '--mrr-tone-violet': c.violet,
    '--mrr-tone-teal': c.teal,
    '--mrr-tone-rose': c.rose,
  }

  // 同时提供 Chrome 86 可计算的静态色值，避免 color-mix() 失效时状态标签变透明。
  const tones: Record<string, string> = {
    success: c.success,
    danger: c.danger,
    warning: c.warning,
    info: c.info,
    neutral: c.secondaryInk,
    primary: c.accent,
    teal: c.teal,
    violet: c.violet,
    rose: c.rose,
  }
  for (const [name, color] of Object.entries(tones)) {
    css[`--mrr-tone-${name}-soft`] = mixHex(color, c.surface, 0.1)
  }
  for (const name of ['success', 'danger', 'warning', 'info', 'neutral', 'primary']) {
    css[`--mrr-tone-${name}-border`] = mixHex(tones[name], c.line, 0.22)
  }
  for (const name of ['success', 'danger', 'warning', 'info']) {
    const color = tones[name]
    css[`--el-color-${name}`] = color
    for (const [step, strength] of [['3', 0.7], ['5', 0.5], ['7', 0.3], ['8', 0.2], ['9', 0.1]] as const) {
      css[`--el-color-${name}-light-${step}`] = mixHex(color, c.surface, strength)
    }
    css[`--el-color-${name}-dark-2`] = mixHex(color, '#000000', 0.8)
  }

  return css
}

const managedNames = Object.keys(gr8rCssVariables('light'))

/**
 * 只触及 Gr8r 管理的变量；切回 MRR 时删除内联覆盖，让原 CSS 主题重新接管。
 * 主色的 --color-primary / --primary / Element Plus 派生色仍由 settings.ts 的唯一写入点维护。
 */
export function applyThemePalette(root: HTMLElement, selected: unknown, scheme: ThemePaletteScheme): void {
  const id = normalizeThemePalette(selected)
  root.dataset.mrrPalette = id
  if (id === 'mrr') {
    for (const name of managedNames) {
      root.style.removeProperty(name)
    }
    return
  }
  const variables = gr8rCssVariables(scheme)
  for (const [name, value] of Object.entries(variables)) {
    root.style.setProperty(name, value)
  }
}
