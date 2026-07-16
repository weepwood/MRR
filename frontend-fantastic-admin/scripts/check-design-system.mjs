import { readFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

const enforcedPages = [
  'src/views/users/index.vue',
  'src/views/records/index.vue',
  'src/views/statistics/index.vue',
]

const forbiddenPatterns = [
  { pattern: /class=["'][^"']*\bpage-shell\b/, message: '不得继续使用页面私有 page-shell' },
  { pattern: /class=["'][^"']*\bpage-header\b/, message: '不得继续使用页面私有 page-header' },
  { pattern: /\.summary-grid\s*\{/, message: '不得重新实现摘要卡片网格' },
  { pattern: /\.list-card\s*\{/, message: '不得重新实现数据面板卡片' },
  { pattern: /\.pager\s*\{/, message: '分页应放入 MrrDataTablePanel 的 pagination 插槽' },
]

const failures = []

for (const relativePath of enforcedPages) {
  const filePath = path.join(projectRoot, relativePath)
  const source = await readFile(filePath, 'utf8')

  if (!source.includes('<MrrPageShell')) {
    failures.push(`${relativePath}: 缺少 MrrPageShell`)
  }
  if (!source.includes('<MrrPageHeader')) {
    failures.push(`${relativePath}: 缺少 MrrPageHeader`)
  }
  if (source.includes('<el-table') && !source.includes('<MrrDataTablePanel')) {
    failures.push(`${relativePath}: 数据表格必须放入 MrrDataTablePanel`)
  }
  if (source.includes('<el-pagination') && !source.includes('<template #pagination>')) {
    failures.push(`${relativePath}: 分页必须使用 MrrDataTablePanel 的 pagination 插槽`)
  }

  for (const rule of forbiddenPatterns) {
    if (rule.pattern.test(source)) {
      failures.push(`${relativePath}: ${rule.message}`)
    }
  }
}

for (const relativePath of ['src/views/records/index.vue', 'src/views/statistics/index.vue']) {
  const source = await readFile(path.join(projectRoot, relativePath), 'utf8')
  if (!source.includes('variant="embedded"')) {
    failures.push(`${relativePath}: 数据面板内筛选栏必须使用 embedded 模式`)
  }
}

if (failures.length) {
  console.error('MRR 设计系统检查失败：')
  for (const failure of failures) {
    console.error(`- ${failure}`)
  }
  process.exitCode = 1
}
else {
  console.log(`MRR 设计系统检查通过（${enforcedPages.length} 个样板页面）`)
}
