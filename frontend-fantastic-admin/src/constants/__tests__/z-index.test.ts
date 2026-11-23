import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { Z_INDEX } from '../z-index'

/*
 * 层级契约：Z_INDEX 是 tokens.css 中 --mrr-z-* 的 JS 副本。
 * 两边不一致时，同一层在 CSS 里和在 Element Plus prop 里会落在不同高度，
 * 档案袋全屏看图时就会出现「看得见遮罩、点不到弹窗」这类问题。
 */
const tokensFile = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../assets/styles/tokens.css')
const tokens = fs.readFileSync(tokensFile, 'utf8')

function cssName(key: string) {
  return `--mrr-z-${key.replace(/[A-Z]/g, letter => `-${letter.toLowerCase()}`)}`
}

function cssTokens() {
  return new Map(Array.from(tokens.matchAll(/(--mrr-z-[a-z-]+)\s*:\s*(\d+)\s*;/g), match => [match[1], Number(match[2])]))
}

describe('z-index 层级令牌', () => {
  it('jS 常量与 tokens.css 逐项一致', () => {
    const defined = cssTokens()
    for (const [key, value] of Object.entries(Z_INDEX)) {
      expect(defined.get(cssName(key)), cssName(key)).toBe(value)
    }
  })

  it('tokens.css 里的每个层级都有 JS 副本，不会只改一边', () => {
    const keys = new Set(Object.keys(Z_INDEX).map(cssName))
    for (const name of cssTokens().keys()) {
      expect(keys.has(name), name).toBe(true)
    }
  })

  it('阻断遮罩低于弹层基准，沉浸层顺序不被打乱', () => {
    expect(Z_INDEX.blocking).toBeLessThan(Z_INDEX.header)
    expect(Z_INDEX.viewer).toBeLessThan(Z_INDEX.ocrDrawer)
    expect(Z_INDEX.ocrDrawer).toBeLessThan(Z_INDEX.actionDialog)
    expect(Z_INDEX.actionDialog).toBeLessThan(Z_INDEX.message)
    expect(Z_INDEX.message).toBeLessThan(Z_INDEX.watermark)
  })
})
