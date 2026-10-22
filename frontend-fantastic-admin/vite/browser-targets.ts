import pkg from '../package.json'
import { browserMinimumVersions, browserRequirements } from '../src/utils/browser-baseline'

export const browserPolyfillTargets = pkg.browserslist
export const browserBuildTargets = Object.entries(browserMinimumVersions)
  .map(([browser, version]) => `${browser.toLowerCase()}${version}`)

// 保留 HTML 内联的 ES5 启动脚本，使不支持应用模块的浏览器也能显示升级提示。
export function renderBrowserBaseline(html: string): string {
  return html
    .replaceAll('%MRR_BROWSER_VERSIONS%', JSON.stringify(browserMinimumVersions))
    .replaceAll('%MRR_BROWSER_REQUIREMENTS%', browserRequirements)
}
