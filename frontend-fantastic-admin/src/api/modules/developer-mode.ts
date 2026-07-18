import axios from 'axios'

const developerModeProbeApi = axios.create({
  baseURL: import.meta.env.DEV ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL,
  timeout: 1200,
  responseType: 'json',
  withCredentials: true,
  validateStatus: () => true,
})

let cachedEnabled = false
let cacheExpiresAt = 0
let pendingProbe: Promise<boolean> | null = null

function parseEnabled(value: unknown): boolean {
  if (typeof value === 'boolean') {
    return value
  }
  if (typeof value === 'number') {
    return value !== 0
  }
  if (typeof value === 'string') {
    return ['true', '1', 'yes', 'on', 'enabled'].includes(value.trim().toLowerCase())
  }
  return false
}

function unwrapSettingValue(payload: unknown): unknown {
  if (!payload || typeof payload !== 'object') {
    return undefined
  }
  const root = payload as Record<string, unknown>
  if ('data' in root) {
    return root.data
  }
  return root.value
}

async function executeProbe(): Promise<boolean> {
  try {
    const response = await developerModeProbeApi.get('/api/v1/settings/developerModeEnabled', {
      headers: {
        'X-MRR-Developer-Mode-Probe': '1',
      },
    })
    return response.status >= 200
      && response.status < 300
      && parseEnabled(unwrapSettingValue(response.data))
  }
  catch {
    return false
  }
}

/**
 * 在匿名路由跳转前探测服务端开发者模式。
 * 关闭状态会得到 401 并按 false 处理；开启状态由后端虚拟管理员会话返回设置值。
 */
export async function isRuntimeDeveloperModeEnabled(force = false): Promise<boolean> {
  const now = Date.now()
  if (!force && now < cacheExpiresAt) {
    return cachedEnabled
  }
  if (pendingProbe) {
    return pendingProbe
  }

  pendingProbe = executeProbe()
    .then((enabled) => {
      cachedEnabled = enabled
      cacheExpiresAt = Date.now() + (enabled ? 5000 : 2000)
      return enabled
    })
    .finally(() => {
      pendingProbe = null
    })
  return pendingProbe
}

export function clearDeveloperModeProbeCache(): void {
  cachedEnabled = false
  cacheExpiresAt = 0
  pendingProbe = null
}
