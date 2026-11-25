import type { SettingSchemaItem } from '@/api/modules/settings'
import { DIRECTORY_ROUTING_KEY, parseDirectoryRules } from '@/views/settings/components/image-directory-routing'

const definitions = [
  ['loginMaxAttempts', '登录失败上限', 5, 3, 20, '次', true],
  ['loginLockWindowMinutes', '登录锁定窗口', 15, 5, 60, '分钟', true],
  ['registerMaxAttemptsPerIp', '注册频率上限', 3, 1, 20, '次', true],
  ['registerWindowSeconds', '注册统计窗口', 60, 10, 600, '秒', true],
  ['apiRateLimitMaxRequests', '敏感接口限流上限', 30, 10, 600, '次', true],
  ['apiRateLimitWindowSeconds', '敏感接口限流窗口', 60, 10, 600, '秒', true],
  ['sessionExpireMinutes', '登录会话有效期', 1440, 60, 1440, '分钟', true],
  ['accessLogRetentionDays', '访问日志保留天数', 1095, 7, 3650, '天', true],
  ['accessLogCleanupBatchSize', '单批删除条数', 5000, 100, 50000, '条', false],
  ['accessLogCleanupMaxBatches', '单次最多批次数', 20, 1, 200, '批', false],
] as const
const items: SettingSchemaItem[] = definitions.map(([key, label, value, min, max, unit, sensitive]) => ({
  key,
  label,
  group: key.startsWith('accessLog') ? 'ops' : 'security',
  type: 'integer',
  defaultValue: String(value),
  overrideValue: null,
  effectiveValue: String(value),
  min,
  max,
  unit,
  sensitive,
  allowedValues: [],
  effect: 'IMMEDIATE',
  description: '仅模拟参数保存与恢复，不改变真实限流、认证或日志清理策略。',
}))
items.push({
  key: 'accessLogRetentionEnabled',
  group: 'ops',
  type: 'boolean',
  label: '访问日志自动清理',
  defaultValue: 'false',
  overrideValue: null,
  effectiveValue: 'false',
  min: null,
  max: null,
  unit: '',
  sensitive: true,
  allowedValues: [],
  effect: 'IMMEDIATE',
  description: '仅模拟配置状态，不实际删除日志。',
})

// Only synthetic routing rules: editing them previews selection, never contacts a node.
items.push({
  key: DIRECTORY_ROUTING_KEY,
  group: 'archive',
  type: 'text',
  label: '目录路由规则',
  defaultValue: JSON.stringify([{ type: 'exact', value: '26.07.13', node: 'default' }, { type: 'month', value: '26.07', node: 'ba01' }]),
  overrideValue: null,
  effectiveValue: '',
  min: null,
  max: 32768,
  unit: '',
  sensitive: true,
  allowedValues: [],
  effect: 'IMMEDIATE',
  valid: true,
  description: '仅合成目录规则与匹配预览，不连接任何真实存储节点。',
})

export function runtimeSchema(overrides: Record<string, string>) {
  return {
    groups: [{ key: 'security', label: '安全策略（演示）' }, { key: 'ops', label: '运维（演示）' }, { key: 'archive', label: '档案浏览（演示）' }],
    items: items.map(item => ({
      ...item,
      overrideValue: overrides[item.key] || null,
      effectiveValue: overrides[item.key] || item.defaultValue,
    })),
  }
}

export function validateRuntimeSetting(key: string, value: unknown) {
  if (typeof value !== 'string' || value.length > 65536 || ['__proto__', 'constructor', 'prototype'].includes(key)) { return false }
  const item = items.find(item => item.key === key)
  if (!item || value === '') { return true }
  if (key === DIRECTORY_ROUTING_KEY) {
    try { parseDirectoryRules(value); return true }
    catch { return false }
  }
  if (item.type === 'boolean') { return value === 'true' || value === 'false' }
  return /^\d+$/.test(value) && Number.isSafeInteger(Number(value)) && Number(value) >= item.min! && Number(value) <= item.max!
}
export function registeredRuntimeSetting(key: string) { return items.some(item => item.key === key) }
