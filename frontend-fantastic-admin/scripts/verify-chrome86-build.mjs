import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const distDir = path.resolve(projectRoot, process.argv[2] || 'dist')
const failures = []

function check(condition, message) {
  if (!condition) {
    failures.push(message)
  }
}

function readFiles(directory, extension) {
  if (!fs.existsSync(directory)) {
    return []
  }
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      return readFiles(target, extension)
    }
    return entry.name.endsWith(extension) ? [target] : []
  })
}

const indexPath = path.join(distDir, 'index.html')
check(fs.existsSync(indexPath), `未找到生产入口：${indexPath}`)

if (fs.existsSync(indexPath)) {
  const html = fs.readFileSync(indexPath, 'utf8')
  const polyfillScript = html.match(/<script type="module"[^>]+src="([^"]*polyfills[^"]*\.js)"/)
  const appScript = html.match(/<script type="module"[^>]+src="([^"]*index-[^"]*\.js)"/)

  check(html.includes('Chrome 86'), '生产入口未声明 Chrome 86 最低版本')
  check(!html.includes('Chrome 109'), '生产入口仍包含 Chrome 109 旧基线')
  check(Boolean(polyfillScript), '生产入口未加载现代浏览器 polyfill')
  check(Boolean(appScript), '生产入口未加载应用主模块')

  if (polyfillScript && appScript) {
    check(
      html.indexOf(polyfillScript[0]) < html.indexOf(appScript[0]),
      'polyfill 必须在应用主模块之前加载',
    )
    check(
      fs.existsSync(path.join(distDir, polyfillScript[1].replace(/^\//, ''))),
      `polyfill 资源不存在：${polyfillScript[1]}`,
    )

    const polyfillPath = path.join(distDir, polyfillScript[1].replace(/^\//, ''))
    if (fs.existsSync(polyfillPath)) {
      const polyfillText = fs.readFileSync(polyfillPath, 'utf8')
      for (const feature of ['findLast', 'hasOwn', 'structuredClone', 'toReversed']) {
        check(
          polyfillText.includes(feature),
          `生产 polyfill 未包含当前代码使用的 ${feature}`,
        )
      }
    }
  }
}

const cssFiles = readFiles(distDir, '.css')
const cssText = cssFiles.map(file => fs.readFileSync(file, 'utf8')).join('\n')

check(cssFiles.length > 0, '生产构建未生成 CSS 资源')
check(
  !/@media\s*\(\s*(?:width|height)\s*(?:<=|>=|<|>)/.test(cssText),
  '生产 CSS 仍包含 Chrome 86 不支持的媒体查询范围语法',
)
check(
  !/(?:^|[;{])inset:/.test(cssText),
  '生产 CSS 仍包含 Chrome 86 不支持的 inset 简写',
)

/*
 * 选择器能力校验。
 *
 * Chrome 86 不支持 Selectors Level 4 的 :is() / :where()（Chrome 88+）与
 * :has()（Chrome 105+），也不支持 :not() 的列表形态（Chrome 88+）。
 * 这些选择器一旦出现，整条规则会被解析器丢弃，且无法通过变量或属性回退补救。
 *
 * 校验分两层：
 * 1. 源码层（阻断）：src/ 下的 CSS 与 <style> 块必须干净。这是本项目可控的部分。
 *    唯一允许的用法是收在 `@supports selector(...)` 内——该分支在 Chrome 86 上
 *    判定为 false 并被跳过，属于渐进增强。
 * 2. 产物层（分层处理）：构建产物中的 :has() 同样阻断；而 :is() / :where() /
 *    :not() 列表形态主要由工具链生成（Lightning CSS 对逻辑属性的 RTL 降级、
 *    UnoCSS/Tailwind reset、Element Plus 自身样式），不阻断但输出清单，
 *    便于评估是否需要单独收紧 CSS 目标。
 */
function stripCssComments(css) {
  return css.replace(/\/\*[\s\S]*?\*\//g, '')
}

function stripAtSupportsBlocks(css) {
  let result = ''
  let index = 0
  while (index < css.length) {
    const at = css.indexOf('@supports', index)
    if (at === -1) {
      result += css.slice(index)
      break
    }
    result += css.slice(index, at)
    const braceStart = css.indexOf('{', at)
    if (braceStart === -1) {
      break
    }
    let depth = 0
    let cursor = braceStart
    for (; cursor < css.length; cursor++) {
      if (css[cursor] === '{') {
        depth += 1
      }
      else if (css[cursor] === '}') {
        depth -= 1
        if (depth === 0) {
          break
        }
      }
    }
    index = cursor + 1
  }
  return result
}

const sourceChecks = [
  [/:has\(/, ':has()', 'Chrome 105+'],
  [/:is\(/, ':is()', 'Chrome 88+'],
  [/:where\(/, ':where()', 'Chrome 88+'],
  [/:not\([^)]*,/, ':not() 列表形态', 'Chrome 88+'],
]

function collectSourceStyleBlocks() {
  const blocks = []
  const walk = (directory) => {
    if (!fs.existsSync(directory)) {
      return
    }
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const target = path.join(directory, entry.name)
      if (entry.isDirectory()) {
        walk(target)
        continue
      }
      if (entry.name.endsWith('.css')) {
        blocks.push({ file: target, css: fs.readFileSync(target, 'utf8') })
        continue
      }
      if (!entry.name.endsWith('.vue')) {
        continue
      }
      const text = fs.readFileSync(target, 'utf8')
      for (const match of text.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)) {
        blocks.push({ file: target, css: match[1] })
      }
    }
  }
  walk(path.join(projectRoot, 'src'))
  return blocks
}

const sourceBlocks = collectSourceStyleBlocks()
check(sourceBlocks.length > 0, '未扫描到前端样式源码')

for (const block of sourceBlocks) {
  const scannable = stripAtSupportsBlocks(stripCssComments(block.css))
  for (const [pattern, feature, baseline] of sourceChecks) {
    const matched = scannable.match(pattern)
    if (!matched) {
      continue
    }
    const line = scannable.slice(0, matched.index).split('\n').length
    check(
      false,
      `源码使用了 Chrome 86 不支持的 ${feature}（需要 ${baseline}）：`
      + `${path.relative(projectRoot, block.file)} 附近第 ${line} 行。`
      + '请改为显式类选择器或逗号选择器列表；若确为渐进增强，请包在 @supports selector() 内。',
    )
  }
}

const ungatedCss = stripAtSupportsBlocks(stripCssComments(cssText))

/*
 * 区间语法 (width <= 760px) 需要 Chrome 104+，@container 需要 Chrome 105+。
 * 这类写法在 Chrome 86 上不会报错——查询恒不匹配，整块规则静默失效，
 * 曾因此在响应式折叠上埋了 77 处而无人察觉。源码与产物都要拦。
 */
const RANGE_AT_RULE = /@(media|container)([^{]*)\{/g
/* 两种形态：(width <= 760px) 与 (400px <= width)。刻意不写 [^)]*，避免回溯开销。 */
const RANGE_NOTATION = new RegExp(
  String.raw`\(\s*(?:width|height|inline-size|block-size|aspect-ratio)\s*[<>]=?`
  + String.raw`|\(\s*[^()<>=\s]+\s*[<>]=?\s*(?:width|height|inline-size|block-size|aspect-ratio)\s*\)`,
)

function findRangeNotation(css) {
  const hits = []
  for (const atRule of css.matchAll(RANGE_AT_RULE)) {
    const matched = atRule[2].match(RANGE_NOTATION)
    if (matched) {
      hits.push({
        rule: `@${atRule[1]}`,
        text: matched[0].trim(),
        line: css.slice(0, atRule.index).split('\n').length,
      })
    }
  }
  return hits
}

for (const block of sourceBlocks) {
  for (const hit of findRangeNotation(stripCssComments(block.css))) {
    check(
      false,
      `源码的 ${hit.rule} 使用了区间语法「${hit.text}」（需要 Chrome 104+），`
      + `在 Chrome 86 上该查询恒不匹配，整块规则失效：`
      + `${path.relative(projectRoot, block.file)} 第 ${hit.line} 行。`
      + '请改用 (max-width: Npx) / (min-width: Npx)。',
    )
  }
}

/*
 * 产物层：@media 的区间语法必须阻断（这是我们自己的样式，改了就能修好）；
 * @container 的区间语法只提示——@container 本身需要 Chrome 105+，整块在 86 上
 * 无论写法如何都不会生效，且这类内容来自 UnoCSS / Element Plus 等依赖产物。
 */
const productRangeHits = findRangeNotation(ungatedCss)
for (const hit of productRangeHits.filter(item => item.rule === '@media')) {
  check(
    false,
    `生产 CSS 的 @media 使用了区间语法「${hit.text}」，在 Chrome 86 上不生效；`
    + '请检查构建目标的浏览器配置（Lightning CSS targets）是否覆盖了该语法。',
  )
}
const containerRangeHits = productRangeHits.filter(item => item.rule === '@container')
if (containerRangeHits.length > 0) {
  console.log(
    `提示：生产 CSS 中存在 ${containerRangeHits.length} 处 @container 区间语法（如「${containerRangeHits[0].text}」），`
    + '来自依赖产物；@container 本身需要 Chrome 105+，在基线浏览器上整块不生效。',
  )
}

check(
  !/:has\(/.test(ungatedCss),
  '生产 CSS 使用了 Chrome 86 不支持的 :has()（需要 Chrome 105+），且未包在 @supports selector() 内',
)

/* 产物中由工具链生成的 Selectors Level 4 用法：不阻断，但保持可见。 */
const toolchainOccurrences = (ungatedCss.match(/:is\(|:where\(|:not\([^)]*,/g) || []).length
if (toolchainOccurrences > 0) {
  const affectedChunks = cssFiles
    .filter(file => /:is\(|:where\(|:not\([^)]*,/.test(
      stripAtSupportsBlocks(fs.readFileSync(file, 'utf8')),
    ))
    .map(file => path.relative(distDir, file))
  console.warn(
    `提示：生产 CSS 中存在 ${toolchainOccurrences} 处 Selectors Level 4 用法（:is() / :where() / `
    + `:not() 列表形态），主要来自 Lightning CSS 逻辑属性降级、UnoCSS/Tailwind reset 与 `
    + `Element Plus 自身样式。受影响分块：${affectedChunks.join('、') || '（无）'}`,
  )
}

/*
 * color-mix() 的既有兜底策略是「变量级回退」：自定义属性在
 * browser-compatibility.css 中重新赋值，直接声明在属性上的用法由该兼容层的
 * 同名属性覆盖。该策略对整体背景等已覆盖，但组件内部细节仍存在缺口，
 * 因此这里只统计并输出，不阻断构建，让遗留债务保持可见。
 */
const directColorMixCount = (ungatedCss.match(/(^|[;{])(?!\s*--)[\w-]+\s*:[^;{}]*color-mix\(/g) || []).length
if (directColorMixCount > 0) {
  console.warn(
    `提示：生产 CSS 中存在 ${directColorMixCount} 处直接使用 color-mix() 的属性声明，`
    + '在 Chrome 86 上会整条失效，请确认已由兼容层覆盖或属于纯装饰降级。',
  )
}

for (const ruleMatch of cssText.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
  const selector = ruleMatch[1].trim()
  const declarations = ruleMatch[2]
  for (const declaration of declarations.split(';')) {
    const separatorIndex = declaration.indexOf(':')
    if (separatorIndex < 1) {
      continue
    }
    const property = declaration.slice(0, separatorIndex)
    const dynamicValue = declaration.slice(separatorIndex + 1)
    if (!dynamicValue.includes('dvh')) {
      continue
    }
    const fallbackDeclaration = `${property}:${dynamicValue.replaceAll('dvh', 'vh')}`
    check(
      declarations.includes(fallbackDeclaration),
      `使用 dvh 的规则缺少同属性 vh 回退：${selector.slice(0, 120)}`,
    )
  }
}

const browserNoticeCssPath = path.join(distDir, 'browser_upgrade', 'index.css')
check(fs.existsSync(browserNoticeCssPath), '生产构建缺少浏览器兼容提示样式')
if (fs.existsSync(browserNoticeCssPath)) {
  const noticeCss = fs.readFileSync(browserNoticeCssPath, 'utf8')
  for (const property of ['top: 0', 'right: 0', 'bottom: 0', 'left: 0']) {
    check(noticeCss.includes(property), `浏览器兼容提示缺少 ${property} 回退`)
  }
}

if (failures.length) {
  console.error('Chrome 86 生产构建校验失败：')
  failures.forEach(message => console.error(`- ${message}`))
  process.exit(1)
}

console.log(`Chrome 86 生产构建校验通过：${distDir}`)
