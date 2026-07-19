export const LOGIN_PAGE_SETTINGS_UPDATED_EVENT = 'mrr:login-page-settings-updated'

export interface LoginPageSettings {
  loginEnvironmentLabel: string
  loginBrandEyebrow: string
  loginBrandTitle: string
  loginBrandDescription: string
  loginFeature1Title: string
  loginFeature1Description: string
  loginFeature2Title: string
  loginFeature2Description: string
  loginFeature3Title: string
  loginFeature3Description: string
  loginFormEyebrow: string
  loginFormTitle: string
  loginFormDescription: string
  loginHelpText: string
  loginFooterText: string
}

export const DEFAULT_LOGIN_PAGE_SETTINGS: LoginPageSettings = {
  loginEnvironmentLabel: 'MRR Console',
  loginBrandEyebrow: 'Medical Record Repository',
  loginBrandTitle: '病案文件管理系统',
  loginBrandDescription: '面向病案影像、档案记录与运行审计的一体化工作平台。',
  loginFeature1Title: '统一档案管理',
  loginFeature1Description: '集中检索病案、影像和装箱记录。',
  loginFeature2Title: '运行数据可视化',
  loginFeature2Description: '查看扫描、访问和服务状态。',
  loginFeature3Title: '权限与审计',
  loginFeature3Description: '按角色控制功能并保留访问记录。',
  loginFormEyebrow: 'Secure sign in',
  loginFormTitle: '登录 MRR',
  loginFormDescription: '使用管理员分配的账号进入系统工作区。',
  loginHelpText: '系统不开放自助注册和在线重置密码。账号创建、角色调整或密码问题请联系系统管理员。',
  loginFooterText: '医院内网部署 · 数据由本地服务管理',
}

export function normalizeLoginPageSettings(values?: Record<string, unknown> | null): LoginPageSettings {
  const source = values || {}
  return Object.fromEntries(
    Object.entries(DEFAULT_LOGIN_PAGE_SETTINGS).map(([key, fallback]) => {
      const value = String(source[key] ?? '').trim()
      return [key, value || fallback]
    }),
  ) as unknown as LoginPageSettings
}

export function serializeLoginPageSettings(settings: LoginPageSettings): Record<string, string> {
  return Object.fromEntries(
    Object.entries(settings).map(([key, value]) => [key, String(value ?? '').trim()]),
  )
}

/** 未登录页面只读取后端严格白名单后的公开文案。 */
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

/** 管理页面读取完整系统设置，再只提取登录页文案字段。 */
export async function getManagedLoginPageSettings(): Promise<LoginPageSettings> {
  const { getSystemSettings } = await import('./settings')
  const response = await getSystemSettings()
  return normalizeLoginPageSettings(response.data)
}

export async function saveManagedLoginPageSettings(settings: LoginPageSettings): Promise<void> {
  const { saveSystemSettings } = await import('./settings')
  await saveSystemSettings(serializeLoginPageSettings(settings))
  window.dispatchEvent(new CustomEvent(LOGIN_PAGE_SETTINGS_UPDATED_EVENT, {
    detail: { ...settings },
  }))
}
