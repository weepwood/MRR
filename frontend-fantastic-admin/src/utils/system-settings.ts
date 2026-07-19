export const SYSTEM_SETTINGS_STORAGE_KEY = 'MRR-ADMIN:system-settings'
export const SYSTEM_SETTINGS_UPDATED_EVENT = 'mrr:system-settings-updated'

export type ArchiveDefaultView = 'thumb' | 'list'
export type ArchivePreviewMode = 'single' | 'scroll'
export type ImageSource = 'local' | 'oss'
export type SettingsSource = 'server' | 'local' | 'default'

export interface EffectiveSystemSettings {
  systemName: string
  systemShortName: string
  systemEnglishName: string
  organizationName: string
  systemDescription: string
  loginEnvironmentLabel: string
  loginFormDescription: string
  loginHelpText: string
  loginFooterText: string
  loginFeatureEnabled: boolean
  loginFeature1Title: string
  loginFeature1Description: string
  loginFeature2Title: string
  loginFeature2Description: string
  loginFeature3Title: string
  loginFeature3Description: string
  systemAdminContactEnabled: boolean
  systemAdminPublicVisible: boolean
  systemAdminDisplayName: string
  systemAdminDepartment: string
  systemAdminPhone: string
  systemAdminExtension: string
  systemAdminEmail: string
  systemAdminServiceHours: string
  systemAdminDescription: string
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
    systemName: 'MRR 病案文件管理系统',
    systemShortName: 'MRR',
    systemEnglishName: 'Medical Record Repository',
    organizationName: '',
    systemDescription: '面向病案影像、档案记录与运行审计的一体化工作平台。',
    loginEnvironmentLabel: '医院内网系统',
    loginFormDescription: '使用管理员分配的账号进入系统工作区。',
    loginHelpText: '账号创建、角色调整或密码问题请联系系统管理员。',
    loginFooterText: '医院内网部署 · 数据由本地服务管理',
    loginFeatureEnabled: true,
    loginFeature1Title: '统一档案管理',
    loginFeature1Description: '集中检索病案、影像和装箱记录。',
    loginFeature2Title: '运行数据可视化',
    loginFeature2Description: '查看扫描、访问和服务状态。',
    loginFeature3Title: '权限与审计',
    loginFeature3Description: '按角色控制功能并保留访问记录。',
    systemAdminContactEnabled: false,
    systemAdminPublicVisible: false,
    systemAdminDisplayName: '系统管理员',
    systemAdminDepartment: '信息科',
    systemAdminPhone: '',
    systemAdminExtension: '',
    systemAdminEmail: '',
    systemAdminServiceHours: '',
    systemAdminDescription: '',
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
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value !== 0
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()
    if (['true', '1', 'yes', 'on', 'enabled'].includes(normalized)) return true
    if (['false', '0', 'no', 'off', 'disabled'].includes(normalized)) return false
  }
  return fallback
}

function parseNumber(value: unknown, fallback: number, min: number, max: number): number {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return fallback
  return Math.min(max, Math.max(min, parsed))
}

function parseText(value: unknown, fallback = ''): string {
  const text = String(value ?? '').trim()
  return text || fallback
}

export function parseSystemSettings(values?: Record<string, unknown> | null): EffectiveSystemSettings {
  const defaults = createDefaultSystemSettings()
  const source = values || {}
  const imageSource = String(source.imageSource ?? '').trim().toLowerCase() === 'oss' ? 'oss' : 'local'
  const archiveDefaultView = source.archiveDefaultView === 'list' ? 'list' : 'thumb'
  const archivePreviewMode = source.archivePreviewMode === 'scroll' ? 'scroll' : 'single'

  return {
    systemName: parseText(source.systemName, defaults.systemName),
    systemShortName: parseText(source.systemShortName, defaults.systemShortName),
    systemEnglishName: parseText(source.systemEnglishName, defaults.systemEnglishName),
    organizationName: parseText(source.organizationName),
    systemDescription: parseText(source.systemDescription, defaults.systemDescription),
    loginEnvironmentLabel: parseText(source.loginEnvironmentLabel, defaults.loginEnvironmentLabel),
    loginFormDescription: parseText(source.loginFormDescription, defaults.loginFormDescription),
    loginHelpText: parseText(source.loginHelpText, defaults.loginHelpText),
    loginFooterText: parseText(source.loginFooterText, defaults.loginFooterText),
    loginFeatureEnabled: parseBoolean(source.loginFeatureEnabled, defaults.loginFeatureEnabled),
    loginFeature1Title: parseText(source.loginFeature1Title, defaults.loginFeature1Title),
    loginFeature1Description: parseText(source.loginFeature1Description, defaults.loginFeature1Description),
    loginFeature2Title: parseText(source.loginFeature2Title, defaults.loginFeature2Title),
    loginFeature2Description: parseText(source.loginFeature2Description, defaults.loginFeature2Description),
    loginFeature3Title: parseText(source.loginFeature3Title, defaults.loginFeature3Title),
    loginFeature3Description: parseText(source.loginFeature3Description, defaults.loginFeature3Description),
    systemAdminContactEnabled: parseBoolean(source.systemAdminContactEnabled, defaults.systemAdminContactEnabled),
    systemAdminPublicVisible: parseBoolean(source.systemAdminPublicVisible, defaults.systemAdminPublicVisible),
    systemAdminDisplayName: parseText(source.systemAdminDisplayName, defaults.systemAdminDisplayName),
    systemAdminDepartment: parseText(source.systemAdminDepartment, defaults.systemAdminDepartment),
    systemAdminPhone: parseText(source.systemAdminPhone),
    systemAdminExtension: parseText(source.systemAdminExtension),
    systemAdminEmail: parseText(source.systemAdminEmail),
    systemAdminServiceHours: parseText(source.systemAdminServiceHours),
    systemAdminDescription: parseText(source.systemAdminDescription),
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
    Object.entries(settings).map(([key, value]) => [key, String(value ?? '').trim()]),
  )
}

export function readLocalSystemSettings(): EffectiveSystemSettings | null {
  try {
    const raw = localStorage.getItem(SYSTEM_SETTINGS_STORAGE_KEY)
    if (!raw) return null
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

  if (localSettings) return { settings: localSettings, source: 'local' }
  return { settings: createDefaultSystemSettings(), source: 'default' }
}

function applyRuntimeSettings(settings: EffectiveSystemSettings): void {
  document.title = settings.systemName
}

export function installSystemSettingsRuntime(): void {
  void loadEffectiveSystemSettings().then(({ settings }) => applyRuntimeSettings(settings))

  window.addEventListener(SYSTEM_SETTINGS_UPDATED_EVENT, (event) => {
    const settings = (event as CustomEvent<EffectiveSystemSettings>).detail
    if (settings) applyRuntimeSettings(settings)
  })

  window.addEventListener('storage', (event) => {
    if (event.key !== SYSTEM_SETTINGS_STORAGE_KEY) return
    const settings = readLocalSystemSettings()
    if (settings) applyRuntimeSettings(settings)
  })
}
