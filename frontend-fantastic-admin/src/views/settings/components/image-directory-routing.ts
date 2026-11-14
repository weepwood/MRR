export const DIRECTORY_ROUTING_KEY = 'imageDirectoryRoutingRules'
export const routingNodes = [
  { value: 'fallback', label: '通用节点' },
  { value: 'default', label: '默认节点' },
  { value: 'ba01', label: 'BA01' },
  { value: 'ba02', label: 'BA02' },
  { value: 'ba03', label: 'BA03' },
] as const

export interface DirectoryRule {
  type: 'exact' | 'month'
  value: string
  node: string
}

export function validateDirectoryRules(rules: DirectoryRule[]): string {
  if (rules.length > 200 || JSON.stringify(rules).length > 32768) { return '最多配置 200 条规则，内容不超过 32768 个字符。' }
  const seen = new Set<string>()
  for (const [index, rule] of rules.entries()) {
    const prefix = `第 ${index + 1} 条规则：`
    if (!['exact', 'month'].includes(rule.type) || !routingNodes.some(node => node.value === rule.node)) { return `${prefix}匹配方式或目标节点无效。` }
    const pattern = rule.type === 'exact' ? /^(?:\d{2}|\d{4})\.\d{2}\.\d{2}$/ : /^(?:\d{2}|\d{4})\.\d{2}$/
    if (!pattern.test(rule.value)) { return `${prefix}请填写点分隔的日期或年月，保留原目录的两位或四位年份。` }
    const parts = rule.value.split('.').map(Number)
    const year = parts[0] + (rule.value.split('.')[0].length === 2 ? 2000 : 0)
    const day = parts[2] ?? 1
    const date = new Date(0)
    date.setUTCFullYear(year, parts[1] - 1, day)
    if (year === 0 || date.getUTCFullYear() !== year || date.getUTCMonth() !== parts[1] - 1 || date.getUTCDate() !== day) { return `${prefix}日期或年月不存在。` }
    const condition = `${rule.type}:${rule.value}`
    if (seen.has(condition)) { return `${prefix}匹配条件重复。` }
    seen.add(condition)
  }
  return ''
}

export function parseDirectoryRules(raw: string): DirectoryRule[] {
  if (raw.length > 32768) { throw new Error('规则内容过长') }
  const parsed: unknown = JSON.parse(raw)
  if (!Array.isArray(parsed) || parsed.some(rule => !rule || typeof rule !== 'object'
    || Object.keys(rule).sort().join(',') !== 'node,type,value'
    || typeof rule.type !== 'string' || typeof rule.value !== 'string' || typeof rule.node !== 'string')) {
    throw new Error('规则格式无效')
  }
  const error = validateDirectoryRules(parsed)
  if (error) { throw new Error(error) }
  return parsed
}

export function previewDirectoryRule(folder: string, rules: DirectoryRule[]): string {
  if (!folder.trim()) { return '无目录记录使用默认节点，不参与日期规则匹配。' }
  if (validateDirectoryRules(rules)) { return '请先修正规则，再查看匹配结果。' }
  const month = folder.split('.').slice(0, 2).join('.')
  const rule = rules.find(rule => rule.type === 'exact' && rule.value === folder)
    ?? rules.find(rule => rule.type === 'month' && rule.value === month)
  const name = routingNodes.find(node => node.value === (rule?.node ?? 'fallback'))!.label
  return rule ? `命中${rule.type === 'exact' ? '精确目录' : '年月'} ${rule.value} → ${name}` : `未命中规则 → ${name}`
}
