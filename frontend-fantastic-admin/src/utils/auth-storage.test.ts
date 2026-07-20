import { beforeEach, describe, expect, it } from 'vitest'
import {
  AUTH_STORAGE_KEYS,
  AUTH_STORAGE_SCHEMA_VERSION,
  clearAuthSessionStorage,
  readAuthStorage,
  readRememberedAccount,
  writeRememberedAccount,
} from './auth-storage'

describe('auth storage migration and self-healing', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('migrates valid legacy session keys into the MRR namespace', () => {
    localStorage.setItem('token', 'legacy-token')
    localStorage.setItem('account', '旧用户')
    localStorage.setItem('avatar', '/legacy-avatar.png')
    localStorage.setItem('profile', JSON.stringify({ username: 'doctor', permissions: ['record:read'] }))
    localStorage.setItem('permissions', JSON.stringify(['record:read', 'record:read']))
    localStorage.setItem('login_account', 'doctor')

    const snapshot = readAuthStorage()

    expect(snapshot).toEqual({
      token: 'legacy-token',
      account: '旧用户',
      avatar: '/legacy-avatar.png',
      profile: { username: 'doctor', permissions: ['record:read'] },
      permissions: ['record:read'],
    })
    expect(localStorage.getItem(AUTH_STORAGE_KEYS.token)).toBe('legacy-token')
    expect(localStorage.getItem(AUTH_STORAGE_KEYS.schemaVersion)).toBe(AUTH_STORAGE_SCHEMA_VERSION)
    expect(localStorage.getItem('token')).toBeNull()
    expect(readRememberedAccount()).toBe('doctor')
    expect(localStorage.getItem('login_account')).toBeNull()
  })

  it('repairs malformed or wrongly typed JSON without dropping the token', () => {
    localStorage.setItem(AUTH_STORAGE_KEYS.schemaVersion, AUTH_STORAGE_SCHEMA_VERSION)
    localStorage.setItem(AUTH_STORAGE_KEYS.token, 'candidate-token')
    localStorage.setItem(AUTH_STORAGE_KEYS.profile, '{broken-json')
    localStorage.setItem(AUTH_STORAGE_KEYS.permissions, JSON.stringify({ admin: true }))
    localStorage.setItem('mrr:archive:preference', 'keep-me')

    const snapshot = readAuthStorage()

    expect(snapshot.token).toBe('candidate-token')
    expect(snapshot.profile).toEqual({})
    expect(snapshot.permissions).toEqual([])
    expect(localStorage.getItem(AUTH_STORAGE_KEYS.profile)).toBeNull()
    expect(localStorage.getItem(AUTH_STORAGE_KEYS.permissions)).toBeNull()
    expect(localStorage.getItem('mrr:archive:preference')).toBe('keep-me')
  })

  it.each([
    ['profile array', AUTH_STORAGE_KEYS.profile, JSON.stringify([])],
    ['profile string', AUTH_STORAGE_KEYS.profile, JSON.stringify('invalid')],
    ['profile null', AUTH_STORAGE_KEYS.profile, JSON.stringify(null)],
    ['permissions string', AUTH_STORAGE_KEYS.permissions, JSON.stringify('admin')],
    ['permissions object', AUTH_STORAGE_KEYS.permissions, JSON.stringify({ admin: true })],
    ['permissions null', AUTH_STORAGE_KEYS.permissions, JSON.stringify(null)],
  ])('falls back safely for %s', (_name, key, value) => {
    localStorage.setItem(AUTH_STORAGE_KEYS.schemaVersion, AUTH_STORAGE_SCHEMA_VERSION)
    localStorage.setItem(key, value)

    const snapshot = readAuthStorage()

    expect(snapshot.profile).toEqual({})
    expect(snapshot.permissions).toEqual([])
    expect(localStorage.getItem(key)).toBeNull()
  })

  it('clears incompatible auth schema without clearing business preferences or remembered account', () => {
    localStorage.setItem(AUTH_STORAGE_KEYS.schemaVersion, '999')
    localStorage.setItem(AUTH_STORAGE_KEYS.token, 'future-token')
    localStorage.setItem(AUTH_STORAGE_KEYS.profile, JSON.stringify({ username: 'future' }))
    localStorage.setItem(AUTH_STORAGE_KEYS.rememberedAccount, 'doctor')
    localStorage.setItem('mrr:archive:preference', 'keep-me')

    const snapshot = readAuthStorage()

    expect(snapshot.token).toBe('')
    expect(snapshot.profile).toEqual({})
    expect(localStorage.getItem(AUTH_STORAGE_KEYS.token)).toBeNull()
    expect(localStorage.getItem(AUTH_STORAGE_KEYS.schemaVersion)).toBe(AUTH_STORAGE_SCHEMA_VERSION)
    expect(readRememberedAccount()).toBe('doctor')
    expect(localStorage.getItem('mrr:archive:preference')).toBe('keep-me')
  })

  it('clears current and legacy auth keys while preserving remembered account and unrelated data', () => {
    localStorage.setItem(AUTH_STORAGE_KEYS.token, 'token')
    localStorage.setItem(AUTH_STORAGE_KEYS.profile, '{}')
    localStorage.setItem('token', 'legacy-token')
    writeRememberedAccount('doctor')
    localStorage.setItem('mrr:archive:preference', 'keep-me')

    clearAuthSessionStorage()

    expect(localStorage.getItem(AUTH_STORAGE_KEYS.token)).toBeNull()
    expect(localStorage.getItem(AUTH_STORAGE_KEYS.profile)).toBeNull()
    expect(localStorage.getItem('token')).toBeNull()
    expect(readRememberedAccount()).toBe('doctor')
    expect(localStorage.getItem('mrr:archive:preference')).toBe('keep-me')
  })

  it('removes remembered account only when the user disables the option', () => {
    writeRememberedAccount(' doctor ')
    expect(readRememberedAccount()).toBe('doctor')

    writeRememberedAccount('')
    expect(readRememberedAccount()).toBe('')
  })
})
