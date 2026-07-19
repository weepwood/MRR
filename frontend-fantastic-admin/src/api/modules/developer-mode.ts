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

function unwrapEnabled(payload: unknown): unknown {
  if (!payload || typeof payload !== 'object') {
    return undefined
  }
  const root = payload as Record<string, unknown>
  const data = root.data
  if (data && typeof data === 'object' && 'enabled' in data) {
    return (data as Record<string, unknown>).enabled
  }
  return root.enabled
}

async function executeProbe(): Promise<boolean> {
  try {
    const response = await developerModeProbeApi.get('/api/v1/public/status/developer-mode')
    return response.status >= 200
      && response.status < 300
      && parseEnabled(unwrapEnabled(response.data))
  }
  catch {
    return false
  }
}

/**
 * 在匿名路由跳转前读取最小化公共状态，只返回开发者模式是否启用。
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

if (typeof window !== 'undefined') {
  window.addEventListener('mrr:system-settings-updated', () => {
    clearDeveloperModeProbeCache()
  })
}
