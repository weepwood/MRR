import { getRequest, putRequest } from '../index'

/** GET /api/v1/settings — 获取全部系统设置 */
export function getSystemSettings() {
  return getRequest<Record<string, string>>('/api/v1/settings')
}

/** GET /api/v1/settings/{key} — 获取单个设置值 */
export function getSetting(key: string) {
  return getRequest<string>(`/api/v1/settings/${encodeURIComponent(key)}`)
}

/** PUT /api/v1/settings — 批量保存系统设置 */
export function saveSystemSettings(settings: Record<string, string>) {
  return putRequest<void>('/api/v1/settings', settings)
}

/** PUT /api/v1/settings/{key} — 保存单个设置值 */
export function setSetting(key: string, value: string) {
  return putRequest<void>(`/api/v1/settings/${encodeURIComponent(key)}`, { value })
}
