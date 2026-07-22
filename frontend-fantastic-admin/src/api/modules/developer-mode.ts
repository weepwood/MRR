import axios from 'axios'

export interface DeveloperModeStatus {
  enabled: boolean
  accessMode: 'ARCHIVE_LEGACY' | 'DISABLED'
}

const DISABLED_STATUS: DeveloperModeStatus = {
  enabled: false,
  accessMode: 'DISABLED',
}

const developerModeProbeApi = axios.create({
  baseURL: import.meta.env.DEV ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL,
  timeout: 1200,
  responseType: 'json',
  withCredentials: true,
  validateStatus: () => true,
})

let cachedStatus: DeveloperModeStatus = DISABLED_STATUS
let cacheExpiresAt = 0
let pendingProbe: Promise<DeveloperModeStatus> | null = null

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

function unwrapStatus(payload: unknown): DeveloperModeStatus {
  if (!payload || typeof payload !== 'object') {
    return DISABLED_STATUS
  }
  const root = payload as Record<string, unknown>
  const source = root.data && typeof root.data === 'object'
    ? root.data as Record<string, unknown>
    : root
  const enabled = parseEnabled(source.enabled)
  const accessMode = source.accessMode === 'ARCHIVE_LEGACY' && enabled
    ? 'ARCHIVE_LEGACY'
    : 'DISABLED'
  return { enabled: accessMode === 'ARCHIVE_LEGACY', accessMode }
}

async function executeProbe(): Promise<DeveloperModeStatus> {
  try {
    const response = await developerModeProbeApi.get('/api/v1/public/status/developer-mode')
    if (response.status < 200 || response.status >= 300) {
      return DISABLED_STATUS
    }
    return unwrapStatus(response.data)
  }
  catch {
    return DISABLED_STATUS
  }
}

/**
 * 匿名路由跳转前读取最小化公共状态，仅用于判断旧版档案袋是否可用。
 */
export async function getRuntimeDeveloperModeStatus(force = false): Promise<DeveloperModeStatus> {
  const now = Date.now()
  if (!force && now < cacheExpiresAt) {
    return cachedStatus
  }
  if (pendingProbe) {
    return pendingProbe
  }

  pendingProbe = executeProbe()
    .then((status) => {
      cachedStatus = status
      cacheExpiresAt = Date.now() + (status.enabled ? 5000 : 2000)
      return status
    })
    .finally(() => {
      pendingProbe = null
    })
  return pendingProbe
}

export async function isRuntimeDeveloperModeEnabled(force = false): Promise<boolean> {
  const status = await getRuntimeDeveloperModeStatus(force)
  return status.enabled && status.accessMode === 'ARCHIVE_LEGACY'
}

export function clearDeveloperModeProbeCache(): void {
  cachedStatus = DISABLED_STATUS
  cacheExpiresAt = 0
  pendingProbe = null
}

if (typeof window !== 'undefined') {
  window.addEventListener('mrr:system-settings-updated', () => {
    clearDeveloperModeProbeCache()
  })
}
