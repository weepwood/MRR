import { describe, expect, it } from 'vitest'
import { contrastRatio } from './theme-color'
import {
  applyThemePalette,
  GR8R_COLORS,
  gr8rCssVariables,
  gr8rPrimary,
  normalizeThemePalette,
  themePalettePresets,
} from './theme-palette'

describe('暖色扁平风格配色方案', () => {
  it('在设置界面使用新名称并保留旧配置标识', () => {
    expect(themePalettePresets.find(palette => palette.id === 'gr8r')?.name).toBe('暖色扁平风格')
    expect(normalizeThemePalette('gr8r')).toBe('gr8r')
  })

  it('忠实映射独立组件库 v0.2.0 的明暗核心色', () => {
    expect(GR8R_COLORS.light).toMatchObject({
      canvas: '#FAF9F7',
      sidebar: '#F3F2EF',
      surface: '#FFFFFF',
      ink: '#242320',
      accent: '#5362CE',
      success: '#226C4F',
      danger: '#B63E38',
    })
    expect(GR8R_COLORS.dark).toMatchObject({
      canvas: '#1B1B1A',
      sidebar: '#171716',
      surface: '#222220',
      ink: '#EAE8E3',
      accent: '#9DA8F7',
      success: '#76C3A0',
      danger: '#EC928C',
    })
    expect(gr8rPrimary('light')).toBe('#5362CE')
    expect(gr8rPrimary('dark')).toBe('#9DA8F7')
  })

  it('给 shadcn、MRR 导航、Element Plus 状态色提供一致的实色令牌', () => {
    const light = gr8rCssVariables('light')
    const dark = gr8rCssVariables('dark')
    expect(Object.keys(dark).sort()).toEqual(Object.keys(light).sort())
    expect(light['--g-main-area-bg']).toBe('#FAF9F7')
    expect(light['--mrr-navigation-bg-solid']).toBe('#F3F2EF')
    expect(light['--mrr-tone-success']).toBe('#226C4F')
    expect(light['--mrr-tone-danger']).toBe('#B63E38')
    expect(light['--mrr-control-border']).toBe('#918B83')
    expect(dark['--mrr-navigation-bg-solid']).toBe('#171716')
    expect(dark['--mrr-tone-success']).toBe('#76C3A0')
    expect(dark['--mrr-primary-foreground']).toBe('#1D2449')

    // 某些部署仍使用 Chrome 86，关键色不能依赖 color-mix() 或 CSS 相对颜色语法。
    for (const value of [...Object.values(light), ...Object.values(dark)]) {
      expect(value).not.toContain('color-mix(')
      expect(value).not.toContain('oklch(')
    }
    expect(light['--background']).toMatch(/^\d+ \d+% \d+%$/)
  })

  it('文字和状态色在主要内容表面上保持至少 4.5:1 对比度', () => {
    for (const c of [GR8R_COLORS.light, GR8R_COLORS.dark]) {
      for (const name of ['ink', 'secondaryInk', 'mutedInk', 'accent', 'success', 'warning', 'danger', 'info'] as const) {
        expect(contrastRatio(c[name], c.surface), `${name} on ${c.surface}`).toBeGreaterThanOrEqual(4.5)
      }
    }
  })

  it('导航选中态使用表面色描边药丸，而不是主色浅填充', () => {
    // 扁平风格把颜色留给主按钮与状态色：导航一旦用主色填充，与主按钮抢注意力。
    for (const scheme of ['light', 'dark'] as const) {
      const variables = gr8rCssVariables(scheme)
      expect(variables['--mrr-navigation-active']).not.toBe(GR8R_COLORS[scheme].accent)
      expect(variables['--mrr-navigation-active-foreground']).toBe(GR8R_COLORS[scheme].ink)
      expect(variables['--g-main-sidebar-menu-active-color']).toBe(GR8R_COLORS[scheme].ink)
      // 焦点环必须是实色：Chrome 86 上 color-mix() 会让整条 box-shadow 失效。
      expect(variables['--mrr-focus-ring']).toMatch(/^0 0 0 3px #[0-9A-F]{6}$/)
    }
    expect(gr8rCssVariables('light')['--mrr-navigation-active']).toBe(GR8R_COLORS.light.surface)
    expect(gr8rCssVariables('dark')['--mrr-navigation-active']).toBe(GR8R_COLORS.dark.raisedSurface)
  })

  it('控件改为描边白底，避免在暖色卡片上出现灰填充块', () => {
    const light = gr8rCssVariables('light')
    const dark = gr8rCssVariables('dark')
    expect(light['--mrr-control-bg']).toBe(GR8R_COLORS.light.surface)
    expect(light['--mrr-control-bg-hover']).toBe(GR8R_COLORS.light.mutedSurface)
    expect(light['--mrr-control-border']).toBe(GR8R_COLORS.light.controlLine)
    expect(dark['--mrr-control-border']).toBe(GR8R_COLORS.dark.controlLine)
    expect(light['--mrr-control-border-hover']).toBe(GR8R_COLORS.light.controlLine)
    // 按钮保留暖灰描边，输入控件使用对比度更高的边线。
    expect(light['--mrr-border-strong']).toBe(GR8R_COLORS.light.strongLine)
    expect(dark['--mrr-border-strong']).toBe(GR8R_COLORS.dark.strongLine)
  })

  it('切换方案后移除全部内联覆盖，并保留 MRR 原本的主色控制权', () => {
    const root = document.createElement('div')
    applyThemePalette(root, 'gr8r', 'light')
    expect(root.dataset.mrrPalette).toBe('gr8r')
    expect(root.style.getPropertyValue('--mrr-control-bg')).toBe('#FFFFFF')
    expect(root.style.getPropertyValue('--g-header-bg')).toBe('#FFFFFF')

    applyThemePalette(root, 'gr8r', 'dark')
    expect(root.style.getPropertyValue('--mrr-control-bg')).toBe('#222220')
    expect(root.style.getPropertyValue('--g-header-bg')).toBe('#222220')

    applyThemePalette(root, 'mrr', 'light')
    expect(root.style.getPropertyValue('--mrr-control-bg')).toBe('')
    expect(root.style.getPropertyValue('--g-header-bg')).toBe('')
    expect(root.style.getPropertyValue('--background')).toBe('')
    expect(root.style.getPropertyValue('--color-primary')).toBe('')
    expect(root.dataset.mrrPalette).toBe('mrr')
  })

  it('未知方案向后兼容回退为 MRR 原配色', () => {
    expect(normalizeThemePalette(undefined)).toBe('mrr')
    expect(normalizeThemePalette('unexpected')).toBe('mrr')
    expect(normalizeThemePalette('gr8r')).toBe('gr8r')
  })
})
