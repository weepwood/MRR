const SESSION_KEY = 'pmr-auth-session'
const LEGACY_TOKEN_KEY = 'token'
const ADMIN_PERMISSION_CANDIDATES = ['user:manage', 'role:manage', 'auth:user:manage', 'auth:role:manage']

function pickFirstString(...values) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
  }
  return ''
}

function normalizePermissions(...sources) {
  const permissions = []

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

function normalizeUser(user) {
  if (!user || typeof user !== 'object') {
    return null
  }

  const role = user.role && typeof user.role === 'object' ? user.role : null
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

function normalizeSession(session) {
  if (!session) {
    return null
  }

  if (typeof session === 'string') {
    return {
      token: session,
      user: null
    }
  }

  const token = pickFirstString(session.token, session.accessToken, session.jwt)
  const user = normalizeUser(session.user || session.profile || session.currentUser)

  if (!token) {
    return null
  }

  return {
    token,
    user,
    updatedAt: session.updatedAt || new Date().toISOString()
  }
}

export function getSession() {
  if (typeof window === 'undefined') {
    return { token: '', user: null }
  }

  try {
    const raw = window.localStorage.getItem(SESSION_KEY)
    if (raw) {
      return normalizeSession(JSON.parse(raw))
    }
  } catch (error) {
    console.warn('Failed to parse auth session', error)
  }

  const legacyToken = window.localStorage.getItem(LEGACY_TOKEN_KEY)
  if (legacyToken) {
    return normalizeSession(legacyToken)
  }

  return { token: '', user: null }
}

export function setSession(session) {
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

export function getToken() {
  return getSession()?.token || ''
}

export function getCurrentUser() {
  return getSession()?.user || null
}

export function hasPermission(permission) {
  if (!permission) {
    return false
  }
  const permissions = getCurrentUser()?.permissions || []
  return permissions.includes(permission)
}

export function hasAnyPermission(permissions = []) {
  return permissions.some((permission) => hasPermission(permission))
}

export function isAdminUser() {
  const user = getCurrentUser()
  if (!user) {
    return false
  }
  const roleCode = (user.roleCode || '').toUpperCase()
  return roleCode === 'ADMIN' || hasAnyPermission(ADMIN_PERMISSION_CANDIDATES)
}

export function getUserDisplayName() {
  const user = getCurrentUser()
  return user?.displayName || user?.username || ''
}

export function getUserRoleName() {
  const user = getCurrentUser()
  return user?.roleName || user?.roleCode || ''
}
