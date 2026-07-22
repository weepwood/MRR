import type { AuthProfile } from '@/utils/auth-storage'
import axios from 'axios'

export interface DeveloperModeSession extends AuthProfile {
  username: string
  displayName: string
  roleCode: 'DEVELOPER_API'
  roleName: string
  status: 'active'
  mustChangePassword: false
  passwordVersion: number
  permissions: string[]
}

export interface DeveloperModeStatus {
  enabled: boolean
  accessMode: 'API_FULL' | 'ARCHIVE_LEGACY' | 'DISABLED'
  session?: DeveloperModeSession
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

function parseSession(value: unknown): DeveloperModeSession | undefined {
  if (!value || typeof value !== 'object') {
    return undefined
  }
  const source = value as Record<string, unknown>
  if (String(source.roleCode || '') !== 'DEVELOPER_API') {
    return undefined
  }
  return {
    username: String(source.username || 'developer-api'),
    displayName: String(source.displayName || 'Developer API'),
    roleCode: 'DEVELOPER_API',
    roleName: String(source.roleName || 'Developer Full API'),
    status: 'active',
    mustChangePassword: false,
    passwordVersion: Number(source.passwordVersion || 1),
    permissions: Array.isArray(source.permissions)
      ? source.permissions.map(item => String(item)).filter(Boolean)
      : [],
  }
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
  const accessMode = enabled && ['API_FULL', 'ARCHIVE_LEGACY'].includes(String(source.accessMode))
    ? String(source.accessMode) as 'API_FULL' | 'ARCHIVE_LEGACY'
    : 'DISABLED'
  const session = accessMode === 'API_FULL' ? parseSession(source.session) : undefined
  if (accessMode === 'API_FULL' && !session) {
    return DISABLED_STATUS
  }
  return {
    enabled: accessMode !== 'DISABLED',
    accessMode,
    ...(session ? { session } : {}),
  }
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

export function canUseArchiveLegacyRoute(routeName: unknown, status: DeveloperModeStatus): boolean {
  return routeName === 'archive'
    && status.enabled
    && status.accessMode === 'ARCHIVE_LEGACY'
}

export function canUseDeveloperApi(status: DeveloperModeStatus): boolean {
  return status.enabled
    && status.accessMode === 'API_FULL'
    && status.session?.roleCode === 'DEVELOPER_API'
}

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
  return status.enabled && status.accessMode !== 'DISABLED'
}

export function clearDeveloperModeProbeCache(): void {
  cachedStatus = DISABLED_STATUS
  cacheExpiresAt = 0
  pendingProbe = null
}

if (typeof window !== 'undefined') {
  window.addEventListener('mrr:system-settings-updated', clearDeveloperModeProbeCache)
}
