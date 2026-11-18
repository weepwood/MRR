import { describe, expect, it } from 'vitest'
import {
  buildThemeColorScale,
  contrastRatio,
  describeThemeColor,
  MIN_CONTRAST_ON_PRIMARY,
  normalizeThemeColor,
  readableInkOn,
  relativeLuminance,
  themeColorDistance,
  themeColorPresets,
  toCssVariables,
} from './theme-color'

describe('主色规范化', () => {
  it('接受大小写与三位简写，非法值回落到默认主色', () => {
    expect(normalizeThemeColor('#2563eb')).toBe('#2563EB')
    expect(normalizeThemeColor('  #0a8642  ')).toBe('#0A8642')
    expect(normalizeThemeColor('#fff')).toBe('#FFFFFF')
    expect(normalizeThemeColor('blue')).toBe('#2563EB')
    expect(normalizeThemeColor(undefined)).toBe('#2563EB')
  })
})

describe('预设配色', () => {
  it('每个预设与白字对比度都达到 AA，按钮文字不会发灰', () => {
    for (const preset of themeColorPresets) {
      expect(contrastRatio(preset.color, '#FFFFFF'), preset.label).toBeGreaterThanOrEqual(MIN_CONTRAST_ON_PRIMARY)
    }
  })

  it('预设的感知亮度落在同一区间，色块之间不会一深一浅', () => {
    const lightness = themeColorPresets.map(preset => describeThemeColor(preset.color).l)
    expect(Math.max(...lightness) - Math.min(...lightness)).toBeLessThan(0.06)
    for (const value of lightness) {
      expect(value).toBeGreaterThan(0.52)
      expect(value).toBeLessThan(0.6)
    }
  })

  it('预设两两色差足够大，不会出现两个看起来一样的色块', () => {
    for (let index = 0; index < themeColorPresets.length; index++) {
      for (let other = index + 1; other < themeColorPresets.length; other++) {
        const [first, second] = [themeColorPresets[index], themeColorPresets[other]]
        expect(themeColorDistance(first.color, second.color), `${first.label} / ${second.label}`).toBeGreaterThanOrEqual(0.12)
      }
    }
  })
})

describe('明亮模式色阶', () => {
  const scale = buildThemeColorScale('#2563EB', 'light')

  it('主色原样保留，悬停与按下依次变深', () => {
    expect(scale.primary).toBe('#2563EB')
    expect(relativeLuminance(scale.hover)).toBeLessThan(relativeLuminance(scale.primary))
    expect(relativeLuminance(scale.active)).toBeLessThan(relativeLuminance(scale.hover))
    expect(relativeLuminance(scale.deep)).toBeLessThan(relativeLuminance(scale.active))
  })

  it('light-3 到 light-9 依次变浅，可作为浅底填充', () => {
    const tones = [scale.light3, scale.light5, scale.light7, scale.light8, scale.light9]
    for (let index = 1; index < tones.length; index++) {
      expect(relativeLuminance(tones[index])).toBeGreaterThan(relativeLuminance(tones[index - 1]))
    }
    expect(relativeLuminance(scale.light9)).toBeGreaterThan(0.85)
  })

  it('浅色档保留主色色相，不会混成灰色', () => {
    const hue = describeThemeColor(scale.primary).h
    for (const tone of [scale.light3, scale.light7, scale.light9]) {
      expect(Math.abs(describeThemeColor(tone).h - hue)).toBeLessThan(3)
    }
  })
})

describe('暗黑模式色阶', () => {
  const scale = buildThemeColorScale('#2563EB', 'dark')

  it('light-x 改为向暗底靠拢，不再是刺眼的近白色块', () => {
    expect(relativeLuminance(scale.light3)).toBeLessThan(relativeLuminance(scale.primary))
    expect(relativeLuminance(scale.light9)).toBeLessThan(0.05)
  })

  it('悬停比主色更亮，dark-2 取更亮的一档', () => {
    expect(relativeLuminance(scale.hover)).toBeGreaterThan(relativeLuminance(scale.primary))
    expect(relativeLuminance(scale.dark2)).toBeGreaterThan(relativeLuminance(scale.primary))
  })
})

describe('色块墨色与 CSS 变量', () => {
  it('深色底用白字，亮黄或近白底换成近黑字', () => {
    expect(readableInkOn('#2563EB')).toBe('#FFFFFF')
    expect(readableInkOn('#FFFF00')).toBe('#111827')
    expect(readableInkOn('#FFFFFF')).toBe('#111827')
  })

  it('展开的变量覆盖主色、品牌档与 Element Plus 浅色档', () => {
    const variables = toCssVariables(buildThemeColorScale('#0A8642', 'light'))
    expect(variables['--color-primary']).toBe('#0A8642')
    expect(variables['--el-color-primary']).toBe('#0A8642')
    expect(variables['--primary']).toBe('147 86% 28%')
    expect(Object.keys(variables)).toHaveLength(13)
  })
})
