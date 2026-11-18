import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const styleDir = path.dirname(fileURLToPath(import.meta.url))
const motionCss = fs.readFileSync(path.join(styleDir, 'motion.css'), 'utf8')
const tokensCss = fs.readFileSync(path.join(styleDir, 'tokens.css'), 'utf8')

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

  it('关闭动效不清除用旋转表达状态的图标', () => {
    // 「字段已切换」靠 rotate(180deg) 表达，是状态本身而不是位移动画。
    // 一旦被 transform: none 抹平，按下瞬间图标会翻回「未切换」方向。
    expect(motionCss).toContain('.field-switch-icon.is-switched')
    const closingRules = motionCss
      .split('}')
      .filter(block => block.includes('field-switch-icon') && block.includes('transform: none'))
    for (const rule of closingRules) {
      expect(rule).toContain(':not(.is-switched)')
    }
  })

  it('关闭动效保留菜单箭头的旋转', () => {
    expect(motionCss).toContain('rotate(-45deg)')
    expect(motionCss).toContain('translateY(-1px) rotate(45deg)')
  })
})
