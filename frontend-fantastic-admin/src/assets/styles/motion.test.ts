import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const styleDir = path.dirname(fileURLToPath(import.meta.url))
const srcRoot = path.resolve(styleDir, '../..')
const motionCss = fs.readFileSync(path.join(styleDir, 'motion.css'), 'utf8')
const tokensCss = fs.readFileSync(path.join(styleDir, 'tokens.css'), 'utf8')

/**
 * 关闭动效时允许保留 transform 的白名单。
 *
 * 这些都是「状态或形态」本身，不是位移动效：把它们清零用户读到的是错误状态。
 * 新增条目必须写清理由 —— 清单只该收状态，不该收装饰。
 */
const STATE_TRANSFORMS = new Set([
  'transform: none;', // 显式还原（禁用态、未配置卡片悬停）
  'transform: rotate(180deg);', // 「字段已切换」的状态本体
  'transform: rotate(-45deg);', // 展开箭头靠 rotate 成形，不是动效
  'transform: translateY(-1px) rotate(45deg);', // 同上，已展开方向
  'transform: scale(0.82) !important;', // 箭头与图标的重合对位
  'transform: scale(0.82) translateX(-3px) !important;', // 同上，含 -3px 对位偏移
  'transform: scale(0.9) rotate(180deg);', // 按下反馈 + 已切换状态共用一个 transform
  'transform: translateY(0);', // 悬停提示归位
  'transform: scaleX(1);', // 选中/悬停下划线长度，属强调而非位移
  'transform: translate(-2px, -2px) rotate(-2deg);', // 选中态的叠层错位
  'transform: translate(2px, -1px) rotate(1.2deg);', // 同上
])

const RULE = /[^{}]+\{[^{}]*\}/g
const INTERACTION = /:hover|:active|aria-pressed|is-selected/

function scanInteractionTransforms() {
  const violations: string[] = []
  const walk = (dir: string) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      if (entry.name === 'node_modules' || entry.name.startsWith('.')) {
        continue
      }
      const full = path.join(dir, entry.name)
      if (entry.isDirectory()) {
        walk(full)
        continue
      }
      if (!/\.(?:vue|css)$/.test(entry.name)) {
        continue
      }
      const text = fs.readFileSync(full, 'utf8')
      const rel = path.relative(srcRoot, full).replace(/\\/g, '/')
      for (const match of text.matchAll(RULE)) {
        const block = match[0]
        const selector = block.slice(0, block.indexOf('{')).replace(/\s+/g, ' ').trim()
        const body = block.slice(block.indexOf('{') + 1, block.lastIndexOf('}'))
        if (!INTERACTION.test(selector)) {
          continue
        }
        for (const raw of body.split('\n')) {
          const line = raw.trim()
          if (!line.startsWith('transform:')) {
            continue
          }
          if (line.includes('var(--mrr-motion-decor')) {
            continue
          }
          if (STATE_TRANSFORMS.has(line)) {
            continue
          }
          violations.push(`${rel} · ${selector} · ${line}`)
        }
      }
    }
  }
  walk(srcRoot)
  return violations
}

function walkSources(extensions: RegExp, visit: (rel: string, text: string) => void) {
  const walk = (dir: string) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      if (entry.name === 'node_modules' || entry.name.startsWith('.') || entry.name === '__tests__') {
        continue
      }
      const full = path.join(dir, entry.name)
      if (entry.isDirectory()) {
        walk(full)
        continue
      }
      if (!extensions.test(entry.name) || /\.(?:test|d)\.ts$/.test(entry.name)) {
        continue
      }
      visit(path.relative(srcRoot, full).replace(/\\/g, '/'), fs.readFileSync(full, 'utf8'))
    }
  }
  walk(srcRoot)
}

/** 只取样式：.css 全文，.vue 取 <style> 之后；去掉注释，避免注释里的示例被当成违规。 */
function styleText(rel: string, text: string) {
  const source = rel.endsWith('.vue') ? text.slice(Math.max(0, text.indexOf('<style'))) : text
  return rel.endsWith('.vue') && !text.includes('<style') ? '' : source.replace(/\/\*[\s\S]*?\*\//g, '')
}

const MOTION_DECLARATION = /(?<![\w-])(transition|animation)(-timing-function|-duration)?\s*:([^;{}]+);/g
/** 去掉 var(...)（含一层嵌套括号），剩下的才是写死的字面量。 */
const stripVars = (value: string) => value.replace(/var\([^()]*(?:\([^()]*\)[^()]*)*\)/g, '')

/**
 * 允许写死时长的条目，必须写明理由。
 * 关闭信号（0s / 0ms / ≤1ms）不在此列，由规则直接放行。
 */
const LITERAL_DURATION_ALLOWLIST = new Set([
  // 刷新图标的一次性整圈旋转：按 loop（1s）显得拖沓，按 overlay（220ms）看不清旋转方向。
  'assets/styles/motion.css · animation: mrr-icon-spin 700ms both;',
])

function scanMotionLiterals() {
  const easing: string[] = []
  const durations: string[] = []
  walkSources(/\.(?:vue|css)$/, (rel, text) => {
    if (rel === 'assets/styles/tokens.css') {
      return
    }
    for (const match of styleText(rel, text).matchAll(MOTION_DECLARATION)) {
      const [declaration, property] = match
      const literal = stripVars(match[3])
      const normalized = declaration.replace(/\s+/g, ' ').trim()
      // 无限循环（呼吸灯、加载点）允许对称的 ease-in-out；linear 永远允许。
      const loop = property === 'animation' && /\binfinite\b/.test(literal)
      if (!loop && /(?<![\w-])(?:ease|ease-in|ease-out|ease-in-out)(?![\w-])|cubic-bezier\(/.test(literal)) {
        easing.push(`${rel} · ${normalized}`)
      }
      for (const [, amount, unit] of literal.matchAll(/(?<![\w.(-])(\d+(?:\.\d+)?|\.\d+)(ms|s)(?![\w-])/g)) {
        const ms = Number(amount) * (unit === 's' ? 1000 : 1)
        const key = `${rel} · ${stripVars(normalized).replace(/\s+/g, ' ').replace(/ ;/, ';')}`
        if (ms > 1 && !LITERAL_DURATION_ALLOWLIST.has(key)) {
          durations.push(key)
        }
      }
    }
  })
  return { easing, durations }
}

/** Vue <Transition> 的类名对象（enterActiveClass 等）与模板 class 里的 UnoCSS 过渡类。 */
function scanUnoTransitionClasses() {
  const violations: string[] = []
  walkSources(/\.(?:vue|ts)$/, (rel, text) => {
    const script = rel.endsWith('.vue') ? text.slice(0, Math.max(0, text.indexOf('<style')) || text.length) : text
    for (const match of script.matchAll(/(?:enter|leave)ActiveClass:\s*'([^']*)'/g)) {
      for (const cls of match[1].split(/\s+/).filter(Boolean)) {
        if (/^duration-\d+$/.test(cls) || /^ease-(?:in|out|in-out|linear)$/.test(cls)) {
          violations.push(`${rel} · ${cls}`)
        }
      }
    }
    for (const match of script.matchAll(/(?<![\w-])transition-all(?![\w-])/g)) {
      violations.push(`${rel} · ${match[0]}`)
    }
  })
  return violations
}

describe('动效字面量只引用令牌', () => {
  it('过渡与动画的曲线只用 --mrr-ease-*（linear 与无限循环除外）', () => {
    expect(scanMotionLiterals().easing).toEqual([])
  })

  it('过渡与动画的时长只用 --mrr-motion-*（0 与 ≤1ms 的关闭信号除外）', () => {
    expect(scanMotionLiterals().durations).toEqual([])
  })

  it('uno 过渡类用 duration-mrr-* / ease-mrr-* 令牌别名，不用 transition-all', () => {
    expect(scanUnoTransitionClasses()).toEqual([])
  })

  it('uno 令牌别名指向 tokens.css 的变量', () => {
    const uno = fs.readFileSync(path.resolve(srcRoot, '../uno.config.ts'), 'utf8')
    for (const [alias, token] of [
      ['mrr-press', '--mrr-motion-press'],
      ['mrr-feedback', '--mrr-motion-feedback'],
      ['mrr-move', '--mrr-motion-move'],
      ['mrr-overlay', '--mrr-motion-overlay'],
      ['mrr-out', '--mrr-ease-out'],
      ['mrr-in', '--mrr-ease-in'],
    ]) {
      expect(uno).toContain(`'${alias}': 'var(${token})'`)
    }
  })
})

describe('动效令牌与关闭策略', () => {
  it('令牌只在 tokens.css 定义一份', () => {
    for (const token of [
      '--mrr-motion-press',
      '--mrr-motion-feedback',
      '--mrr-motion-move',
      '--mrr-motion-overlay',
      '--mrr-motion-loop',
      '--mrr-motion-loop-soft',
      '--mrr-ease-out',
    ]) {
      expect(tokensCss).toContain(`${token}:`)
      // motion.css 只消费令牌，重新定义会造出第二份来源，历史上正是因此出现 15 种时长。
      expect(motionCss).not.toContain(`${token}:`)
    }
  })

  it('旧令牌名没有残留', () => {
    expect(motionCss).not.toContain('--mrr-motion-fast')
    expect(motionCss).not.toContain('--mrr-motion-normal')
  })

  it('关闭动效不再维护全局 transform 清除清单', () => {
    // 清单式写法失败方向是反的：漏一条装饰只是多一次位移，漏一条状态就是状态误读。
    expect(motionCss).not.toContain('transform: none !important')
    expect(motionCss).not.toMatch(/^:root\[data-motion="off"\] \.mrr-icon-interactive/m)
  })

  it('降级开关在两条通道各定义一次，取值只有 none', () => {
    expect(motionCss.match(/--mrr-motion-decor:/g) ?? []).toHaveLength(2)
    expect(motionCss.match(/--mrr-motion-decor: none;/g) ?? []).toHaveLength(2)
  })

  it('交互态上的位移与缩放必须走降级开关', () => {
    // 覆盖全仓（不只 motion.css）：新增装饰动效忘记套变量时会在这里失败。
    expect(scanInteractionTransforms()).toEqual([])
  })

  it('承载状态的变换没有被降级开关套住', () => {
    // 「字段已切换」的 180° 与箭头形态必须永不清零。
    expect(motionCss).toContain('.field-switch-icon.is-switched')
    expect(motionCss).toContain('transform: rotate(180deg)')

    const menuItem = fs.readFileSync(path.join(srcRoot, 'layouts/components/Menu/item.vue'), 'utf8')
    const arrowRules = menuItem
      .split('}')
      .filter(block => block.includes('menu-leading-control-arrow') && block.includes('transform:'))
    expect(arrowRules.length).toBeGreaterThan(0)
    for (const rule of arrowRules) {
      expect(rule).not.toContain('var(--mrr-motion-decor')
    }
  })
})
