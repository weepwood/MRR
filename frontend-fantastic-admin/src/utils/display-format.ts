/** 系统管理页面统一日期显示；缺失或非法值不回显原文。 */
export function formatDateTime(value: unknown): string {
  if (!value) return '—'
  const date = new Date(String(value))
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('zh-CN', { hour12: false })
}
