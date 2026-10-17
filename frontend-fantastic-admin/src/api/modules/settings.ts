import { deleteRequest, getRequest, putRequest } from '../index'

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

/** DELETE /api/v1/settings/{key} — 清除数据库覆盖值，回落到文件与环境变量的默认配置 */
export function deleteSetting(key: string) {
  return deleteRequest<void>(`/api/v1/settings/${encodeURIComponent(key)}`)
}

/** 可配置项的值类型，与服务端 SettingType 对应 */
export type SettingValueType = 'boolean' | 'integer' | 'decimal' | 'text' | 'enum'

/** 可配置项的生效方式，与服务端 SettingEffect 对应 */
export type SettingEffect = 'IMMEDIATE' | 'RESTART_REQUIRED'

/** 单个可配置项的元数据 */
export interface SettingSchemaItem {
  key: string
  group: string
  type: SettingValueType
  label: string
  description: string
  unit: string
  min: number | null
  max: number | null
  allowedValues: string[]
  effect: SettingEffect
  sensitive: boolean
  /** 文件与环境变量提供的默认值 */
  defaultValue: string
  /** 数据库中的显式覆盖值，未配置时为 null 或空串 */
  overrideValue: string | null
  /** 当前生效值 */
  effectiveValue: string
}

export interface SettingSchemaGroup {
  key: string
  label: string
}

export interface SettingSchema {
  groups: SettingSchemaGroup[]
  items: SettingSchemaItem[]
}

/** GET /api/v1/settings/schema — 获取可配置项元数据，供前端动态渲染 */
export function getSettingsSchema() {
  return getRequest<SettingSchema>('/api/v1/settings/schema')
}

/** GET /api/v1/settings/ui — 获取全局界面外观与导航外链（仅需登录，不要求 system:read） */
export function getUiSettings() {
  return getRequest<Record<string, string>>('/api/v1/settings/ui')
}
