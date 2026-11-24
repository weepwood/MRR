import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { darkTheme, lightTheme } from '../../../themes'

/*
 * 对比度基线锁定测试（DESIGN.md §2.2、§5.3、§10）。
 *
 * 背景：可访问首帧上检测出的低对比组合——占位符（muted 叠 0.78 透明度，
 * 实际 1.8:1）、顶栏「搜索」提示（60% 透明 2.4:1）、课程面板置顶徽标
 * （主色 on 主色淡底 4.49:1）、登录品牌区文字（4.1–4.46:1）、浏览器兼容
 * 拦截浮层按钮（3.56:1）——全部低于 4.5:1 的正文基线。医院终端多为
 * 低色深旧显示器，DESIGN.md §2.2 的对比度约束必须覆盖这些组合。
 *
 * 本文件用两种方式锁定：
 * 1. 静态色值对比度计算（十六进制 / 主题 HSL 令牌）；
 * 2. 样式与模板规则断言（占位符不得叠透明度、图标按钮必须有可访问名等），
 *    与 type-scale / motion 锁定测试同一模式。
 */

const styleDir = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(styleDir, '../../..')

const read = (relative: string) => fs.readFileSync(path.join(projectRoot, relative), 'utf8')

const elementPlusOverrides = read('src/assets/styles/element-plus-overrides.css')
const formControls = read('src/assets/styles/components/form-controls.css')
const browserCompatibility = read('src/assets/styles/browser-compatibility.css')
const navSearch = read('src/layouts/components/Topbar/Toolbar/NavSearch/index.vue')
const toolbarDir = path.join(projectRoot, 'src/layouts/components/Topbar/Toolbar')
const loginView = read('src/views/login.vue')
const dashboard = read('src/views/home/DashboardWorkspace.vue')
const browserUpgradeCss = read('public/browser_upgrade/index.css')

/** 提取 `selector { body }` 的 body；selector 必须精确出现。 */
function ruleBody(css: string, selector: string, options: { last?: boolean } = {}) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const re = new RegExp(`${escaped}\\s*\\{([^}]*)\\}`)
  if (options.last) {
    const matches = css.match(new RegExp(re, 'g'))
    if (!matches?.length) {
      return ''
    }
    const body = matches[matches.length - 1].match(/\{([^}]*)\}/)
    return body ? body[1] : ''
  }
  const matched = css.match(re)
  return matched ? matched[1] : ''
}

/** #rrggbb → 相对亮度与对比度。 */
function hexLuminance(hex: string) {
  const value = hex.replace('#', '')
  if (value.length !== 6) {
    return NaN
  }
  const rgb = [0, 2, 4].map(i => Number.parseInt(value.slice(i, i + 2), 16) / 255)
  const linear = rgb.map((v) => {
    if (v <= 0.03928) {
      return v / 12.92
    }
    const normalized = (v + 0.055) / 1.055
    return normalized ** 2.4
  })
  return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]
}

function contrast(foreground: number, background: number) {
  const [lighter, darker] = [foreground, background].sort((a, b) => b - a)
  return (lighter + 0.05) / (darker + 0.05)
}

/** 提取规则体中 background 声明的十六进制色。 */
function backgroundHex(body: string) {
  const matched = body.match(/background(?:-color)?:\s*(#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6}))\b/)
  return matched ? matched[1] : ''
}

/**
 * 工具函数：从主题令牌表读取 HSL 文本（`215.4 19% 44%`），返回近似十六进制。
 * 与 surfaces.test.ts 同一套计算（HSL → RGB → 亮度）。
 */
function hslToLuminance(hsl: string) {
  const matched = hsl.match(/^(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)%\s+(\d+(?:\.\d+)?)%$/)
  expect(matched, `主题令牌不是 "h s% l%" 形式：${hsl}`).toBeTruthy()
  const h = Number(matched![1])
  const s = Number(matched![2]) / 100
  const l = Number(matched![3]) / 100
  const chroma = (1 - Math.abs(2 * l - 1)) * s
  const hp = h / 60
  const second = chroma * (1 - Math.abs((hp % 2) - 1))
  const table = [
    [chroma, second, 0],
    [second, chroma, 0],
    [0, chroma, second],
    [0, second, chroma],
    [second, 0, chroma],
    [chroma, 0, second],
  ]
  const offset = l - chroma / 2
  const [r, g, b] = table[Math.floor(hp) % 6].map(value => (value + offset) * 255)
  return hexLuminance(
    `#${[r, g, b].map(v => Math.round(v).toString(16).padStart(2, '0')).join('')}`,
  )
}

type Theme = Record<string, string>
const themeLuminance = (theme: Theme, token: string) => hslToLuminance(theme[token])

describe('对比度基线（颜色组合）', () => {
  it('次要文字在卡面、工作区、页面底上均 ≥ 4.5:1（亮/暗）', () => {
    for (const [name, theme] of [['亮色', lightTheme], ['暗色', darkTheme] as const]) {
      const muted = themeLuminance(theme, '--muted-foreground')
      for (const [surface, token] of [
        ['卡面', '--card'],
        ['页面底', '--background'],
      ] as const) {
        const ratio = contrast(muted, themeLuminance(theme, token))
        expect(ratio, `${name}主题 次要文字/${surface} = ${ratio.toFixed(2)}:1`).toBeGreaterThanOrEqual(4.5)
      }
    }
  })

  it('浏览器兼容拦截浮层的主按钮与图标：白字 ≥ 4.5:1（含 hover）', () => {
    const primaryBody = ruleBody(browserUpgradeCss, '.browser-compat-button-primary')
    const bg = backgroundHex(primaryBody)
    expect(bg).toBeTruthy()
    expect(contrast(hexLuminance('#ffffff'), hexLuminance(bg)), `主按钮底色 ${bg}`).toBeGreaterThanOrEqual(4.5)

    const iconBody = ruleBody(browserUpgradeCss, '.browser-compat-icon')
    const iconBg = backgroundHex(iconBody)
    expect(iconBg).toBeTruthy()
    expect(contrast(hexLuminance('#ffffff'), hexLuminance(iconBg)), `图标底色 ${iconBg}`).toBeGreaterThanOrEqual(4.5)

    const hoverBody = browserUpgradeCss.match(/\.browser-compat-button-primary:hover[^{]*\{([^}]*)\}/)
    expect(hoverBody, '缺少 hover 规则').toBeTruthy()
    const hoverBg = backgroundHex(hoverBody![1])
    expect(hoverBg).toBeTruthy()
    expect(contrast(hexLuminance('#ffffff'), hexLuminance(hoverBg)), `hover 底色 ${hoverBg}`).toBeGreaterThanOrEqual(4.5)
  })
})

describe('占位符与提示文字（规则锁定）', () => {
  it('占位符（Element Plus 桥接）不得在 muted 之上叠加透明度', () => {
    const body = ruleBody(elementPlusOverrides, 'body .el-select__placeholder')
    expect(body, 'element-plus-overrides.css 缺少占位符规则').toBeTruthy()
    expect(body).toContain('var(--mrr-muted-foreground)')
    expect(body, '叠加透明度会把 muted 压到 1.8:1').not.toMatch(/opacity/)
    expect(elementPlusOverrides, 'select 未选提示（is-transparent）需同组覆盖').toMatch(/body \.el-select \.el-select__selected-item\.is-transparent,/)
  })

  it('占位符（Fa 控件）不得在 muted 之上叠加透明度', () => {
    const matched = formControls.match(/\.fa-input-control::placeholder[\s\S]{0,80}?\{([^}]*)\}/)
    expect(matched, 'form-controls.css 缺少占位符规则').toBeTruthy()
    expect(matched![1]).not.toMatch(/opacity/)
  })

  it('顶栏搜索提示文字不得使用透明度弱化的 muted', () => {
    expect(navSearch, '/数字 透明度档会压到 2.4:1').not.toMatch(/muted-foreground\/\d+/)
    expect(navSearch).toMatch(/text-muted-foreground/)
    // group-hover 需要 .group 祖先，否则悬停变色是死代码
    expect(navSearch).toMatch(/mx-2 px-3 group/)
  })

  it('置顶徽标文字必须用前景色，避免主色调淡底组合低于 4.5:1', () => {
    const body = ruleBody(dashboard, '.pin-badge', { last: true })
    expect(body, 'DashboardWorkspace.vue 缺少 .pin-badge 规则').toBeTruthy()
    expect(body).toContain('var(--mrr-foreground)')
    expect(body).not.toMatch(/color:\s*var\(--widget-accent\)/)
  })

  it('置顶徽标底色在 Chrome 86 下必须有实色回退', () => {
    const supportsBlock = browserCompatibility.match(/@supports not \(color: color-mix[^{]*\{([\s\S]*)\}/)
    expect(supportsBlock, 'browser-compatibility.css 缺少 @supports not 块').toBeTruthy()
    expect(supportsBlock![1]).toMatch(/\.pin-badge\s*\{[^}]*background/)
  })

  it('字数计数与提示条说明文字必须走前景层级令牌', () => {
    expect(elementPlusOverrides, '缺少字数计数的提权规则').toMatch(/body \.el-textarea \.el-input__count,\s*body \.el-input__count-inner \{[^}]*var\(--mrr-muted-foreground\)/)

    // EP 按类型逐条设置 .el-alert--*.is-light .el-alert__description（0,3,0），
    // 覆盖规则必须逐型提权到 (0,4,1)，否则形改实不改（轻量覆盖恒败于 EP）。
    expect(elementPlusOverrides, 'info 型说明文字须逐型提权').toMatch(/body \.el-alert\.el-alert--info\.is-light \.el-alert__description,/)
    expect(elementPlusOverrides, 'error 型说明文字须逐型提权').toMatch(/body \.el-alert\.el-alert--error\.is-light \.el-alert__description\s*\{[^}]*var\(--mrr-muted-foreground\)/)

    const titleBody = ruleBody(elementPlusOverrides, 'body .el-alert .el-alert__title')
    expect(titleBody, '缺少 el-alert 标题兜底色规则').toContain('var(--mrr-foreground)')
  })

  it('登录页品牌区彩色文字必须向前景色混，保证明暗两套主题可读', () => {
    const kicker = ruleBody(loginView, '.brand-heading > span')
    expect(kicker, '缺少品牌 kicker 规则').toBeTruthy()
    expect(kicker).toContain('color-mix(in srgb, var(--mrr-primary) 62%, var(--mrr-foreground))')

    const subtitle = ruleBody(loginView, '.brand-heading small')
    expect(subtitle, '缺少品牌副标题规则').toBeTruthy()
    expect(subtitle).toContain('color-mix(in srgb, var(--mrr-primary) 62%, var(--mrr-foreground))')

    const fallback = browserCompatibility.match(/\.login-page \.brand-heading > span,/)
    expect(fallback, 'Chrome 86 缺少品牌文字静态回退').toBeTruthy()
  })
})

describe('顶栏图标按钮可访问名称（DESIGN.md §5.3）', () => {
  const iconButtons: Array<[string, string]> = [
    ['全屏', 'Fullscreen/index.vue'],
    ['页面刷新', 'PageReload/index.vue'],
    ['明暗切换', 'ColorScheme/index.vue'],
  ]

  for (const [name, file] of iconButtons) {
    it(`${name}按钮必须有 aria-label`, () => {
      const content = fs.readFileSync(path.join(toolbarDir, file), 'utf8')
      expect(content, `${file} 缺少 aria-label`).toMatch(/aria-label/)
    })
  }
})
