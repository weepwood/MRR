import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { darkTheme, lightTheme } from '../../../themes'

const styleDir = path.dirname(fileURLToPath(import.meta.url))
const tokensCss = fs.readFileSync(path.join(styleDir, 'tokens.css'), 'utf8')

type Theme = Record<string, string>

/** 读取 `210 40% 98%` 形式的 HSL 三元组。 */
function hsl(theme: Theme, token: string) {
  const raw = theme[token]
  expect(raw, `主题缺少 ${token}`).toBeTruthy()
  const matched = raw!.match(/^(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)%\s+(\d+(?:\.\d+)?)%$/)
  expect(matched, `${token} 不是 "h s% l%" 形式：${raw}`).toBeTruthy()
  return { h: Number(matched![1]), s: Number(matched![2]), l: Number(matched![3]) }
}

function luminance({ h, s, l }: { h: number, s: number, l: number }) {
  const saturation = s / 100
  const lightness = l / 100
  const chroma = (1 - Math.abs(2 * lightness - 1)) * saturation
  const hp = h / 60
  const second = chroma * (1 - Math.abs((hp % 2) - 1))
  const table: number[][] = [
    [chroma, second, 0],
    [second, chroma, 0],
    [0, chroma, second],
    [0, second, chroma],
    [second, 0, chroma],
    [chroma, 0, second],
  ]
  const offset = lightness - chroma / 2
  const [r, g, b] = table[Math.floor(hp) % 6].map(value => value + offset)
  const linear = (value: number) => (value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4)
  return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
}

/**
 * 工作区底色。卡片不是坐在 `--background` 上，而是坐在主区域上：
 * 亮色主区域比 `--background` 再深 2 个百分点（`--background` 是导航/顶栏的底），
 * 暗色两者相同。跨主题比较必须跟随这层间接引用，否则会拿错基准。
 */
function workspace(theme: Theme) {
  const raw = theme['--g-main-area-bg'] ?? ''
  const reference = raw.match(/^hsl\(var\((--[\w-]+)\)\)$/)
  if (reference) {
    return hsl(theme, reference[1])
  }
  const literal = raw.match(/^hsl\((\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)%\s+(\d+(?:\.\d+)?)%\)$/)
  expect(literal, `--g-main-area-bg 无法解析：${raw}`).toBeTruthy()
  return { h: Number(literal![1]), s: Number(literal![2]), l: Number(literal![3]) }
}

const THEMES: Array<[string, Theme]> = [
  ['亮色', lightTheme as Theme],
  ['暗色', darkTheme as Theme],
]

describe('表面层级契约', () => {
  it.each(THEMES)('%s：背景 ≤ 卡面 ≤ 浮层，亮度只能递增', (_name, theme) => {
    // 唯一的硬约束是「相邻层不得倒挂」。历史上暗色卡面一度比工作区更暗，
    // 卡片看起来像陷进底里，图底关系整个翻转。
    const background = hsl(theme, '--background').l
    const card = hsl(theme, '--card').l
    const popover = hsl(theme, '--popover').l
    expect(card).toBeGreaterThanOrEqual(background)
    expect(popover).toBeGreaterThanOrEqual(card)
  })

  it.each(THEMES)('%s：卡面比工作区至少高 2 个亮度百分点', (_name, theme) => {
    // 低于这个步差，卡片边界只能靠 1px 描边撑，46px 大表与指标卡会糊成一片。
    const step = hsl(theme, '--card').l - workspace(theme).l
    expect(step).toBeGreaterThanOrEqual(2)
  })

  it.each(THEMES)('%s：卡面与工作区的对比度不低于 1.05:1', (_name, theme) => {
    const card = luminance(hsl(theme, '--card'))
    const area = luminance(workspace(theme))
    expect((card + 0.05) / (area + 0.05)).toBeGreaterThanOrEqual(1.05)
  })

  it('tokens.css 到 shadcn 变量是 1:1 桥接，不做二次取色', () => {
    // 桥接层一旦自己算色，改主题就不再生效，且会在暗色里造出第二份来源。
    expect(tokensCss).toContain('--mrr-background: hsl(var(--background));')
    expect(tokensCss).toContain('--mrr-card: hsl(var(--card));')
    expect(tokensCss).toContain('--mrr-popover: hsl(var(--popover));')
  })

  it('壳层与工作区底跟随主题，不写死色值', () => {
    // 这两条原本是 #f3f5f8 / #10151f 的硬编码，和主题层的 hsl(210 40% 96%) 差 0.5%，
    // 看着一样但改主题时工作区不动 —— 是典型的「两处各说各话」。
    expect(tokensCss).toMatch(/--mrr-app-shell-bg:\s*var\(--g-main-area-bg\);/)
    expect(tokensCss).toMatch(/--mrr-workspace-bg:\s*var\(--g-main-area-bg\);/)
    expect(tokensCss).not.toMatch(/--mrr-app-shell-bg:\s*#/)
    expect(tokensCss).not.toMatch(/--mrr-workspace-bg:\s*#/)
  })
})
