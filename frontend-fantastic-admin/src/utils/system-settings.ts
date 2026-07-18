export const SYSTEM_SETTINGS_STORAGE_KEY = 'MRR-ADMIN:system-settings'
export const SYSTEM_SETTINGS_UPDATED_EVENT = 'mrr:system-settings-updated'

export type ArchiveDefaultView = 'thumb' | 'list'
export type ArchivePreviewMode = 'single' | 'scroll'
export type ImageSource = 'local' | 'oss'
export type SettingsSource = 'server' | 'local' | 'default'

export interface EffectiveSystemSettings {
  systemName: string
  imageSource: ImageSource
  archiveDefaultView: ArchiveDefaultView
  archivePreviewMode: ArchivePreviewMode
  archiveThumbnailSize: number
  archivePreloadCount: number
  archiveAutoFit: boolean
  archiveRememberSelection: boolean
  archiveWatermarkEnabled: boolean
  archiveWatermarkOpacity: number
  archiveIpMaxChanges: number
  patientIdCardRevealEnabled: boolean
  patientIdCardCopyEnabled: boolean
  developerModeEnabled: boolean
}

export function createDefaultSystemSettings(): EffectiveSystemSettings {
  return {
    systemName: 'MRR 后台管理中心',
    imageSource: 'local',
    archiveDefaultView: 'thumb',
    archivePreviewMode: 'single',
    archiveThumbnailSize: 200,
    archivePreloadCount: 20,
    archiveAutoFit: true,
    archiveRememberSelection: true,
    archiveWatermarkEnabled: true,
    archiveWatermarkOpacity: 14,
    archiveIpMaxChanges: 3,
    patientIdCardRevealEnabled: false,
    patientIdCardCopyEnabled: false,
    developerModeEnabled: false,
  }
}

function parseBoolean(value: unknown, fallback: boolean): boolean {
  if (typeof value === 'boolean') {
    return value
  }
  if (typeof value === 'number') {
    return value !== 0
  }
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()
    if (['true', '1', 'yes', 'on', 'enabled'].includes(normalized)) {
      return true
    }
    if (['false', '0', 'no', 'off', 'disabled'].includes(normalized)) {
      return false
    }
  }
  return fallback
}

function parseNumber(value: unknown, fallback: number, min: number, max: number): number {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return fallback
  }
  return Math.min(max, Math.max(min, parsed))
}

export function parseSystemSettings(values?: Record<string, unknown> | null): EffectiveSystemSettings {
  const defaults = createDefaultSystemSettings()
  const source = values || {}
  const imageSource = String(source.imageSource ?? '').trim().toLowerCase() === 'oss' ? 'oss' : 'local'
  const archiveDefaultView = source.archiveDefaultView === 'list' ? 'list' : 'thumb'
  const archivePreviewMode = source.archivePreviewMode === 'scroll' ? 'scroll' : 'single'

  return {
    systemName: String(source.systemName ?? defaults.systemName).trim() || defaults.systemName,
    imageSource,
    archiveDefaultView,
    archivePreviewMode,
    archiveThumbnailSize: parseNumber(source.archiveThumbnailSize, defaults.archiveThumbnailSize, 160, 320),
    archivePreloadCount: parseNumber(source.archivePreloadCount, defaults.archivePreloadCount, 10, 100),
    archiveAutoFit: parseBoolean(source.archiveAutoFit, defaults.archiveAutoFit),
    archiveRememberSelection: parseBoolean(source.archiveRememberSelection, defaults.archiveRememberSelection),
    archiveWatermarkEnabled: parseBoolean(source.archiveWatermarkEnabled, defaults.archiveWatermarkEnabled),
    archiveWatermarkOpacity: parseNumber(source.archiveWatermarkOpacity, defaults.archiveWatermarkOpacity, 5, 35),
    archiveIpMaxChanges: Math.round(parseNumber(source.archiveIpMaxChanges, defaults.archiveIpMaxChanges, 0, 20)),
    patientIdCardRevealEnabled: parseBoolean(source.patientIdCardRevealEnabled, defaults.patientIdCardRevealEnabled),
    patientIdCardCopyEnabled: parseBoolean(source.patientIdCardCopyEnabled, defaults.patientIdCardCopyEnabled),
    developerModeEnabled: parseBoolean(source.developerModeEnabled, defaults.developerModeEnabled),
  }
}

export function serializeSystemSettings(settings: EffectiveSystemSettings): Record<string, string> {
  return Object.fromEntries(
    Object.entries(settings).map(([key, value]) => [key, String(value ?? '')]),
  )
}

export function readLocalSystemSettings(): EffectiveSystemSettings | null {
  try {
    const raw = localStorage.getItem(SYSTEM_SETTINGS_STORAGE_KEY)
    if (!raw) {
      return null
    }
    return parseSystemSettings(JSON.parse(raw) as Record<string, unknown>)
  }
  catch {
    return null
  }
}

export function writeLocalSystemSettings(settings: EffectiveSystemSettings): void {
  try {
    localStorage.setItem(SYSTEM_SETTINGS_STORAGE_KEY, JSON.stringify(settings))
  }
  catch {
    // 浏览器禁用本地存储时仍允许服务端配置继续工作。
  }

  window.dispatchEvent(new CustomEvent<EffectiveSystemSettings>(SYSTEM_SETTINGS_UPDATED_EVENT, {
    detail: { ...settings },
  }))
}

export async function loadEffectiveSystemSettings(): Promise<{
  settings: EffectiveSystemSettings
  source: SettingsSource
}> {
  const localSettings = readLocalSystemSettings()

  try {
    // API 模块会继续依赖应用路由，因此仅在真正加载远端配置时按需导入。
    // 这样纯解析函数可在 Vitest 等无完整 Vite 插件环境中独立测试。
    const { getSystemSettings } = await import('@/api/modules/settings')
    const response = await getSystemSettings()
    const serverSettings = response.data as Record<string, unknown> | undefined
    if (serverSettings && Object.keys(serverSettings).length > 0) {
      const settings = parseSystemSettings(serverSettings)
      writeLocalSystemSettings(settings)
      return { settings, source: 'server' }
    }
  }
  catch {
    // 服务端不可用时回退到本地配置。
  }

  if (localSettings) {
    return { settings: localSettings, source: 'local' }
  }

  return { settings: createDefaultSystemSettings(), source: 'default' }
}

function applyRuntimeSettings(settings: EffectiveSystemSettings): void {
  document.title = settings.systemName
}

export function installSystemSettingsRuntime(): void {
  void loadEffectiveSystemSettings().then(({ settings }) => applyRuntimeSettings(settings))

  window.addEventListener(SYSTEM_SETTINGS_UPDATED_EVENT, (event) => {
    const settings = (event as CustomEvent<EffectiveSystemSettings>).detail
    if (settings) {
      applyRuntimeSettings(settings)
    }
  })

  window.addEventListener('storage', (event) => {
    if (event.key !== SYSTEM_SETTINGS_STORAGE_KEY) {
      return
    }
    const settings = readLocalSystemSettings()
    if (settings) {
      applyRuntimeSettings(settings)
    }
  })
}
