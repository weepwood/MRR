import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/*
 * 字号阶梯契约（DESIGN.md §2.4）：--mrr-font-size-xs（12px）是正文最小档。
 *
 * 只锁定已经收口的文件，避免一次性对全站做机械替换。仍有意保留小字的位置
 * （登录页环境浮层、导航窄栏、徽标计数、缩略图遮罩、公共状态页分钟刻度等）
 * 列在 DESIGN.md §2.4，不在此清单中；收口一处就把文件加进来。
 */
const srcDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')

const GUARDED_FILES = [
  'components/ScanQueryDiagnostics/index.vue',
  'components/charts/MrrRequestHeatmap.vue',
  'views/statistics-detail/ArchiveDetailContent.vue',
  'assets/styles/pages/statistics-detail-typography.css',
  'views/settings/components/AppConfigPanel.vue',
]

function styleOf(file: string) {
  const content = fs.readFileSync(path.join(srcDir, file), 'utf8')
  if (!file.endsWith('.vue')) {
    return content
  }
  return [...content.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)].map(match => match[1]).join('\n')
}

describe('字号阶梯契约', () => {
  it.each(GUARDED_FILES)('%s 不出现小于 12px 的字号', (file) => {
    const tooSmall = [...styleOf(file).matchAll(/font-size:\s*(\d+(?:\.\d+)?)px/g)]
      .map(match => Number(match[1]))
      .filter(size => size < 12)

    expect(tooSmall).toEqual([])
  })

  it('档案卡辅助标签字号引用正文最小档令牌', () => {
    expect(styleOf('assets/styles/pages/statistics-detail-typography.css'))
      .toMatch(/--folder-meta-font-size:\s*var\(--mrr-font-size-xs\)/)
  })

  it('热力图浮层投影跟随主题阴影令牌', () => {
    const css = styleOf('components/charts/MrrRequestHeatmap.vue')
    expect(css).toMatch(/\.heatmap-tooltip\s*\{[^}]*box-shadow:\s*var\(--mrr-shadow-md\)/)
    expect(css).not.toMatch(/box-shadow:[^;]*rgb\(/)
  })
})
