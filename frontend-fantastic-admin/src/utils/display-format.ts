/** 系统管理页面统一日期显示；缺失或非法值不回显原文。 */
export function formatDateTime(value: unknown): string {
  if (!value) { return '—' }
  const date = new Date(String(value))
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('zh-CN', { hour12: false })
}

/**
 * 后端整体脱敏占位符。
 *
 * 旧版本对白名单之外的日志正文一律替换为占位符，原始内容从未落盘、无法恢复；
 * 页面需要把这种情况与「渲染失败」区分开，否则会被误判为前端缺陷。
 */
const REDACTED_LOG_TEXTS = new Set([
  '[MESSAGE OMITTED]',
  '[LEGACY MESSAGE OMITTED]',
  '[ERROR MESSAGE OMITTED]',
])

export function isRedactedLogText(value: unknown): boolean {
  return typeof value === 'string' && REDACTED_LOG_TEXTS.has(value.trim())
}
