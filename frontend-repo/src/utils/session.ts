import pinia from '@/store'
import { useUserStore } from '@/store/modules/user'

export interface SessionUser {
  username?: string
  displayName?: string
  roleCode?: string
  roleName?: string
  permissions?: string[]
  [key: string]: unknown
}

export interface AuthSessionState {
  token: string
  user: SessionUser | null
  updatedAt?: string
}

const SESSION_KEY = 'pmr-auth-session'
const LEGACY_TOKEN_KEY = 'token'

function getStore() {
  return useUserStore(pinia)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object'
}

function pickFirstString(...values: unknown[]): string {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
  }
  return ''
}

function normalizePermissions(...sources: unknown[]): string[] {
  const permissions: unknown[] = []

  for (const source of sources) {
    if (!source) continue

    if (Array.isArray(source)) {
      permissions.push(...source)
      continue
    }

    if (typeof source === 'string') {
      permissions.push(...source.split(','))
    }
  }

  return permissions
    .map((permission) => (typeof permission === 'string' ? permission.trim() : ''))
    .filter(Boolean)
}

function normalizeUser(user: unknown): SessionUser | null {
  if (!isRecord(user)) return null

  const role = isRecord(user.role) ? user.role : null
  const permissions = normalizePermissions(
    user.permissions,
    user.permissionsCsv,
    user.rolePermissions,
    role?.permissions,
    role?.permissionsCsv,
    role?.rolePermissions
  )

  return {
    ...user,
    roleCode: pickFirstString(user.roleCode, role?.code),
    roleName: pickFirstString(user.roleName, role?.name, role?.roleName),
    permissions
  }
}

function normalizeSession(session: unknown): AuthSessionState | null {
  if (!session) return null

  if (typeof session === 'string') {
    return { token: session, user: null }
  }

  if (!isRecord(session)) return null

  const token = pickFirstString(session.token, session.accessToken, session.jwt)
  const user = normalizeUser(session.user || session.profile || session.currentUser)

  if (!token) return null

  return {
    token,
    user,
    updatedAt: typeof session.updatedAt === 'string' ? session.updatedAt : new Date().toISOString()
  }
}

export function getSession(): AuthSessionState {
  if (typeof window === 'undefined') {
    return { token: '', user: null }
  }

  const store = getStore()
  if (store.token) {
    return { token: store.token, user: store.user as SessionUser }
  }

  try {
    const raw = window.localStorage.getItem(SESSION_KEY)
    if (raw) {
      const parsed = normalizeSession(JSON.parse(raw))
      if (parsed) {
        setSession(parsed)
        return parsed
      }
    }
  } catch (error) {
    // ignore
  }

  const legacyToken = window.localStorage.getItem(LEGACY_TOKEN_KEY)
  if (legacyToken) {
    const parsed = normalizeSession(legacyToken)
    if (parsed) {
      setSession(parsed)
      return parsed
    }
  }

  return { token: '', user: null }
}

export function setSession(session: unknown) {
  if (typeof window === 'undefined') return

  const normalized = normalizeSession(session)
  if (!normalized) {
    clearSession()
    return
  }

  const store = getStore()
  // store uses a general setSession action that takes the raw or normalized data
  store.setSession(normalized)
}

export const saveSession = setSession

export function clearSession() {
  if (typeof window === 'undefined') return
  getStore().clearSession()
}

export function getToken(): string {
  return getStore().token || getSession().token || ''
}

export function getCurrentUser(): SessionUser | null {
  return getStore().user as SessionUser | null || getSession().user || null
}

export function hasPermission(permission: string): boolean {
  if (!permission) return false
  return getStore().hasPermission(permission)
}

export function hasAnyPermission(permissions: string[] = []): boolean {
  return getStore().hasAnyPermission(permissions)
}

export function isAdminUser(): boolean {
  return getStore().isAdminUser
}

export function getUserDisplayName(): string {
  return getStore().userDisplayName
}

export function getUserRoleName(): string {
  return getStore().userRoleName
}
