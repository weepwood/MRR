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
const ADMIN_PERMISSION_CANDIDATES = ['user:manage', 'role:manage', 'auth:user:manage', 'auth:role:manage']

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
    if (!source) {
      continue
    }

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
  if (!isRecord(user)) {
    return null
  }

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
  if (!session) {
    return null
  }

  if (typeof session === 'string') {
    return {
      token: session,
      user: null
    }
  }

  if (!isRecord(session)) {
    return null
  }

  const token = pickFirstString(session.token, session.accessToken, session.jwt)
  const user = normalizeUser(session.user || session.profile || session.currentUser)

  if (!token) {
    return null
  }

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

  try {
    const raw = window.localStorage.getItem(SESSION_KEY)
    if (raw) {
      const parsed = normalizeSession(JSON.parse(raw))
      if (parsed) {
        return parsed
      }
    }
  } catch (error) {
    console.warn('Failed to parse auth session', error)
  }

  const legacyToken = window.localStorage.getItem(LEGACY_TOKEN_KEY)
  if (legacyToken) {
    const parsed = normalizeSession(legacyToken)
    if (parsed) {
      return parsed
    }
  }

  return { token: '', user: null }
}

export function setSession(session: unknown) {
  if (typeof window === 'undefined') {
    return
  }

  const normalized = normalizeSession(session)
  if (!normalized) {
    clearSession()
    return
  }

  window.localStorage.setItem(SESSION_KEY, JSON.stringify(normalized))
  window.localStorage.setItem(LEGACY_TOKEN_KEY, normalized.token)
}

export const saveSession = setSession

export function clearSession() {
  if (typeof window === 'undefined') {
    return
  }

  window.localStorage.removeItem(SESSION_KEY)
  window.localStorage.removeItem(LEGACY_TOKEN_KEY)
}

export function getToken(): string {
  return getSession()?.token || ''
}

export function getCurrentUser(): SessionUser | null {
  return getSession()?.user || null
}

export function hasPermission(permission: string): boolean {
  if (!permission) {
    return false
  }
  const permissions = getCurrentUser()?.permissions || []
  return permissions.includes(permission)
}

export function hasAnyPermission(permissions: string[] = []): boolean {
  return permissions.some((permission) => hasPermission(permission))
}

export function isAdminUser(): boolean {
  const user = getCurrentUser()
  if (!user) {
    return false
  }
  const roleCode = (user.roleCode || '').toUpperCase()
  return roleCode === 'ADMIN' || hasAnyPermission(ADMIN_PERMISSION_CANDIDATES)
}

export function getUserDisplayName(): string {
  const user = getCurrentUser()
  return user?.displayName || user?.username || ''
}

export function getUserRoleName(): string {
  const user = getCurrentUser()
  return user?.roleName || user?.roleCode || ''
}
