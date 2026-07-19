export const LOGIN_PAGE_SETTINGS_UPDATED_EVENT = 'mrr:login-page-settings-updated'

export interface LoginPageSettings {
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
  systemAdminContactVisible: boolean
  systemAdminDisplayName: string
  systemAdminDepartment: string
  systemAdminPhone: string
  systemAdminExtension: string
  systemAdminEmail: string
  systemAdminServiceHours: string
  systemAdminDescription: string
}

export const DEFAULT_LOGIN_PAGE_SETTINGS: LoginPageSettings = {
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
  systemAdminContactVisible: false,
  systemAdminDisplayName: '系统管理员',
  systemAdminDepartment: '信息科',
  systemAdminPhone: '',
  systemAdminExtension: '',
  systemAdminEmail: '',
  systemAdminServiceHours: '',
  systemAdminDescription: '',
}

function parseBoolean(value: unknown, fallback: boolean): boolean {
  if (typeof value === 'boolean') { return value }
  const normalized = String(value ?? '').trim().toLowerCase()
  if (['true', '1', 'yes', 'on', 'enabled'].includes(normalized)) { return true }
  if (['false', '0', 'no', 'off', 'disabled'].includes(normalized)) { return false }
  return fallback
}

function text(source: Record<string, unknown>, key: keyof LoginPageSettings, fallback = ''): string {
  const value = String(source[key] ?? '').trim()
  return value || fallback
}

export function normalizeLoginPageSettings(values?: Record<string, unknown> | null): LoginPageSettings {
  const source = values || {}
  const defaults = DEFAULT_LOGIN_PAGE_SETTINGS
  return {
    systemName: text(source, 'systemName', defaults.systemName),
    systemShortName: text(source, 'systemShortName', defaults.systemShortName),
    systemEnglishName: text(source, 'systemEnglishName', defaults.systemEnglishName),
    organizationName: text(source, 'organizationName'),
    systemDescription: text(source, 'systemDescription', defaults.systemDescription),
    loginEnvironmentLabel: text(source, 'loginEnvironmentLabel', defaults.loginEnvironmentLabel),
    loginFormDescription: text(source, 'loginFormDescription', defaults.loginFormDescription),
    loginHelpText: text(source, 'loginHelpText', defaults.loginHelpText),
    loginFooterText: text(source, 'loginFooterText', defaults.loginFooterText),
    loginFeatureEnabled: parseBoolean(source.loginFeatureEnabled, defaults.loginFeatureEnabled),
    loginFeature1Title: text(source, 'loginFeature1Title', defaults.loginFeature1Title),
    loginFeature1Description: text(source, 'loginFeature1Description', defaults.loginFeature1Description),
    loginFeature2Title: text(source, 'loginFeature2Title', defaults.loginFeature2Title),
    loginFeature2Description: text(source, 'loginFeature2Description', defaults.loginFeature2Description),
    loginFeature3Title: text(source, 'loginFeature3Title', defaults.loginFeature3Title),
    loginFeature3Description: text(source, 'loginFeature3Description', defaults.loginFeature3Description),
    systemAdminContactVisible: parseBoolean(source.systemAdminContactVisible, false),
    systemAdminDisplayName: text(source, 'systemAdminDisplayName', defaults.systemAdminDisplayName),
    systemAdminDepartment: text(source, 'systemAdminDepartment', defaults.systemAdminDepartment),
    systemAdminPhone: text(source, 'systemAdminPhone'),
    systemAdminExtension: text(source, 'systemAdminExtension'),
    systemAdminEmail: text(source, 'systemAdminEmail'),
    systemAdminServiceHours: text(source, 'systemAdminServiceHours'),
    systemAdminDescription: text(source, 'systemAdminDescription'),
  }
}

/** 未登录页面只读取后端严格白名单后的公开展示和支持信息。 */
export async function getPublicLoginPageSettings(): Promise<LoginPageSettings> {
  try {
    const { getRequest } = await import('../index')
    const response = await getRequest<Record<string, string>>('/api/v1/public/config/login-page', {
      skipGlobalError: true,
    } as any)
    return normalizeLoginPageSettings(response.data)
  }
  catch {
    return { ...DEFAULT_LOGIN_PAGE_SETTINGS }
  }
}
