export interface AuthProfile {
  id?: number
  username?: string
  displayName?: string
  roleCode?: string
  roleName?: string
  permissions?: string[]
  status?: string
  mustChangePassword?: boolean
  passwordVersion?: number
  temporaryPasswordExpiresAt?: string
  lastLoginAt?: string
  avatar?: string
  [key: string]: unknown
}

export interface AuthStorageSnapshot {
  token: string
  account: string
  avatar: string
  profile: AuthProfile
  permissions: string[]
}

export const AUTH_STORAGE_SCHEMA_VERSION = '1'

export const AUTH_STORAGE_KEYS = {
  schemaVersion: 'mrr:auth:schema-version',
  token: 'mrr:auth:token',
  account: 'mrr:auth:account',
  avatar: 'mrr:auth:avatar',
  profile: 'mrr:auth:profile',
  permissions: 'mrr:auth:permissions',
  rememberedAccount: 'mrr:login:remembered-account',
} as const

const LEGACY_AUTH_STORAGE_KEYS = {
  token: 'token',
  account: 'account',
  avatar: 'avatar',
  profile: 'profile',
  permissions: 'permissions',
  rememberedAccount: 'login_account',
} as const

const CURRENT_SESSION_KEYS = [
  AUTH_STORAGE_KEYS.token,
  AUTH_STORAGE_KEYS.account,
  AUTH_STORAGE_KEYS.avatar,
  AUTH_STORAGE_KEYS.profile,
  AUTH_STORAGE_KEYS.permissions,
] as const

const LEGACY_SESSION_KEYS = [
  LEGACY_AUTH_STORAGE_KEYS.token,
  LEGACY_AUTH_STORAGE_KEYS.account,
  LEGACY_AUTH_STORAGE_KEYS.avatar,
  LEGACY_AUTH_STORAGE_KEYS.profile,
  LEGACY_AUTH_STORAGE_KEYS.permissions,
] as const

function resolveStorage(storage?: Storage): Storage | undefined {
  if (storage) return storage
  try {
    return window.localStorage
  }
  catch {
    return undefined
  }
}

function safeGet(storage: Storage, key: string): string | null {
  try {
    return storage.getItem(key)
  }
  catch {
    return null
  }
}

function safeSet(storage: Storage, key: string, value: string): boolean {
  try {
    storage.setItem(key, value)
    return true
  }
  catch {
    return false
  }
}

function safeRemove(storage: Storage, key: string): void {
  try {
    storage.removeItem(key)
  }
  catch {
    // 浏览器禁用存储时保持内存会话可用。
  }
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function normalizePermissions(value: unknown): string[] | undefined {
  if (!Array.isArray(value)) return undefined
  return [...new Set(value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0).map(item => item.trim()))]
}

function normalizeProfile(value: unknown): AuthProfile | undefined {
  if (!isPlainObject(value)) return undefined
  const profile = { ...value } as AuthProfile
  if ('permissions' in profile) {
    const normalizedPermissions = normalizePermissions(profile.permissions)
    if (normalizedPermissions) profile.permissions = normalizedPermissions
    else delete profile.permissions
  }
  return profile
}

function readJson<T>(
  storage: Storage,
  key: string,
  normalize: (value: unknown) => T | undefined,
  fallback: T,
): T {
  const raw = safeGet(storage, key)
  if (!raw) return fallback
  try {
    const normalized = normalize(JSON.parse(raw))
    if (normalized !== undefined) return normalized
  }
  catch {
    // 无效 JSON 与类型不匹配采用同一自愈策略。
  }
  safeRemove(storage, key)
  return fallback
}

function migrateKey(storage: Storage, legacyKey: string, currentKey: string): void {
  const currentValue = safeGet(storage, currentKey)
  const legacyValue = safeGet(storage, legacyKey)
  if (currentValue === null && legacyValue !== null) {
    if (!safeSet(storage, currentKey, legacyValue)) return
  }
  safeRemove(storage, legacyKey)
}

function migrateRememberAccount(storage: Storage): void {
  migrateKey(storage, LEGACY_AUTH_STORAGE_KEYS.rememberedAccount, AUTH_STORAGE_KEYS.rememberedAccount)
}

export function clearAuthSessionStorage(storage?: Storage): void {
  const target = resolveStorage(storage)
  if (!target) return
  ;[...CURRENT_SESSION_KEYS, ...LEGACY_SESSION_KEYS].forEach(key => safeRemove(target, key))
  safeSet(target, AUTH_STORAGE_KEYS.schemaVersion, AUTH_STORAGE_SCHEMA_VERSION)
}

export function migrateAuthStorage(storage?: Storage): void {
  const target = resolveStorage(storage)
  if (!target) return

  migrateRememberAccount(target)
  const storedVersion = safeGet(target, AUTH_STORAGE_KEYS.schemaVersion)
  if (storedVersion && storedVersion !== AUTH_STORAGE_SCHEMA_VERSION) {
    clearAuthSessionStorage(target)
    return
  }

  // 即使版本号已经写入，也继续完成缺失字段的迁移，修复浏览器关闭、
  // 配额异常等原因导致的部分迁移状态。
  migrateKey(target, LEGACY_AUTH_STORAGE_KEYS.token, AUTH_STORAGE_KEYS.token)
  migrateKey(target, LEGACY_AUTH_STORAGE_KEYS.account, AUTH_STORAGE_KEYS.account)
  migrateKey(target, LEGACY_AUTH_STORAGE_KEYS.avatar, AUTH_STORAGE_KEYS.avatar)
  migrateKey(target, LEGACY_AUTH_STORAGE_KEYS.profile, AUTH_STORAGE_KEYS.profile)
  migrateKey(target, LEGACY_AUTH_STORAGE_KEYS.permissions, AUTH_STORAGE_KEYS.permissions)
  safeSet(target, AUTH_STORAGE_KEYS.schemaVersion, AUTH_STORAGE_SCHEMA_VERSION)
}

export function readAuthStorage(storage?: Storage): AuthStorageSnapshot {
  const target = resolveStorage(storage)
  if (!target) return { token: '', account: '', avatar: '', profile: {}, permissions: [] }

  migrateAuthStorage(target)
  return {
    token: safeGet(target, AUTH_STORAGE_KEYS.token) ?? '',
    account: safeGet(target, AUTH_STORAGE_KEYS.account) ?? '',
    avatar: safeGet(target, AUTH_STORAGE_KEYS.avatar) ?? '',
    profile: readJson(target, AUTH_STORAGE_KEYS.profile, normalizeProfile, {}),
    permissions: readJson(target, AUTH_STORAGE_KEYS.permissions, normalizePermissions, []),
  }
}

export function writeAuthSession(
  session: { token: string, account: string, avatar: string, profile: AuthProfile, permissions: string[] },
  storage?: Storage,
): void {
  const target = resolveStorage(storage)
  if (!target) return

  safeSet(target, AUTH_STORAGE_KEYS.schemaVersion, AUTH_STORAGE_SCHEMA_VERSION)
  safeSet(target, AUTH_STORAGE_KEYS.token, session.token)
  safeSet(target, AUTH_STORAGE_KEYS.account, session.account)
  safeSet(target, AUTH_STORAGE_KEYS.avatar, session.avatar)
  safeSet(target, AUTH_STORAGE_KEYS.profile, JSON.stringify(session.profile))
  safeSet(target, AUTH_STORAGE_KEYS.permissions, JSON.stringify(session.permissions))
  LEGACY_SESSION_KEYS.forEach(key => safeRemove(target, key))
}

export function writeAuthProfile(
  profile: AuthProfile,
  account: string,
  permissions: string[],
  storage?: Storage,
): void {
  const target = resolveStorage(storage)
  if (!target) return

  safeSet(target, AUTH_STORAGE_KEYS.schemaVersion, AUTH_STORAGE_SCHEMA_VERSION)
  safeSet(target, AUTH_STORAGE_KEYS.profile, JSON.stringify(profile))
  safeSet(target, AUTH_STORAGE_KEYS.permissions, JSON.stringify(permissions))
  safeSet(target, AUTH_STORAGE_KEYS.account, account)
  safeRemove(target, LEGACY_AUTH_STORAGE_KEYS.profile)
  safeRemove(target, LEGACY_AUTH_STORAGE_KEYS.permissions)
  safeRemove(target, LEGACY_AUTH_STORAGE_KEYS.account)
}

export function readRememberedAccount(storage?: Storage): string {
  const target = resolveStorage(storage)
  if (!target) return ''
  migrateRememberAccount(target)
  return safeGet(target, AUTH_STORAGE_KEYS.rememberedAccount) ?? ''
}

export function writeRememberedAccount(account: string, storage?: Storage): void {
  const target = resolveStorage(storage)
  if (!target) return
  const normalized = account.trim()
  if (normalized) safeSet(target, AUTH_STORAGE_KEYS.rememberedAccount, normalized)
  else safeRemove(target, AUTH_STORAGE_KEYS.rememberedAccount)
  safeRemove(target, LEGACY_AUTH_STORAGE_KEYS.rememberedAccount)
}
