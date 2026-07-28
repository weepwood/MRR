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
