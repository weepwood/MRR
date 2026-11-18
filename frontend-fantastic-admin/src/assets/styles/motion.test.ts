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
