// @vitest-environment node
import fs from 'node:fs'
import { JSDOM } from 'jsdom'
import { describe, expect, it } from 'vitest'
import { browserBuildTargets, browserPolyfillTargets, renderBrowserBaseline } from '../../../vite/browser-targets'

const html = renderBrowserBaseline(fs.readFileSync(new URL('../../../index.html', import.meta.url), 'utf8'))

function bootstrap(userAgent: string, missing: string[] = []) {
  const dom = new JSDOM(html, { runScripts: 'outside-only', url: 'http://localhost/' })
  const { window } = dom
  Object.defineProperty(window.navigator, 'userAgent', { value: userAgent })
  window.fetch = globalThis.fetch
  window.TextEncoder = globalThis.TextEncoder
  Object.defineProperty(window.HTMLScriptElement.prototype, 'noModule', { value: false, configurable: true })
  for (const feature of missing) {
    if (feature === 'at' || feature === 'findLast') {
      Object.defineProperty(window.Array.prototype, feature, { value: undefined, configurable: true })
    }
    else if (feature === 'modules') {
      Reflect.deleteProperty(window.HTMLScriptElement.prototype, 'noModule')
    }
    else {
      Object.defineProperty(window, feature, { value: undefined, configurable: true })
    }
  }
  window.eval(window.document.querySelector('script:not([src])')!.textContent!)
  const result = {
    blocked: Boolean((window as unknown as { __MRR_APP_STARTUP_BLOCKED__?: boolean }).__MRR_APP_STARTUP_BLOCKED__),
    message: window.document.getElementById('browser-compat-message')!.textContent,
  }
  window.close()
  return result
}

describe('生产入口浏览器策略（不是旧内核实测）', () => {
  it('入口、JS polyfill 和 CSS 目标共同使用声明的基线', () => {
    expect(html).not.toContain('%MRR_BROWSER_')
    expect(html).toContain('Edge 109')
    expect(browserPolyfillTargets).toContain('Edge >= 109')
    expect(browserBuildTargets).toContain('edge109')
  })
  it('放行 Edge 109，拒绝 Edge 108', () => {
    expect(bootstrap('Chrome/109.0.0.0 Edg/109.0.0.0').blocked).toBe(false)
    const old = bootstrap('Chrome/108.0.0.0 Edg/108.0.0.0')
    expect(old.blocked).toBe(true)
    expect(old.message).toContain('最低支持版本 Edge 109')
  })

  it('chrome 86 基线保持，IE 仍拒绝', () => {
    expect(bootstrap('Chrome/86.0.0.0').blocked).toBe(false)
    expect(bootstrap('Chrome/85.0.0.0').blocked).toBe(true)
    expect(bootstrap('Trident/7.0; rv:11.0').blocked).toBe(true)
  })

  it('未知浏览器不因补丁加载前缺少 at/findLast 被误拒绝', () => {
    expect(bootstrap('UnknownBrowser', ['at', 'findLast']).blocked).toBe(false)
  })

  it.each(['Promise', 'fetch', 'URL', 'TextEncoder', 'Proxy', 'modules'])('未知浏览器缺少基础能力 %s 时仍拒绝', (feature) => {
    expect(bootstrap('UnknownBrowser', [feature]).blocked).toBe(true)
  })
})
