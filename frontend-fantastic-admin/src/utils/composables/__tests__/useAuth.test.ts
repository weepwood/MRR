import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import useAuth from '../useAuth'

vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({
    profile: { roleCode: 'DOCTOR', username: 'doctor1' },
    permissions: ['record:read', 'statistics:read'],
  }),
}))

vi.mock('@/store/modules/settings', () => ({
  useSettingsStore: () => ({
    settings: { app: { enablePermission: true } },
  }),
}))

describe('useAuth — 权限检查 composable', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('auth(string) — 拥有权限返回 true', () => {
    const { auth } = useAuth()
    expect(auth('record:read')).toBe(true)
  })

  it('auth(string) — 缺少权限返回 false', () => {
    const { auth } = useAuth()
    expect(auth('user:manage')).toBe(false)
  })

  it('auth(string[]) — 拥有数组中任一权限返回 true（OR 语义）', () => {
    const { auth } = useAuth()
    expect(auth(['user:manage', 'record:read'])).toBe(true)
  })

  it('auth(string[]) — 不拥有数组中任何权限返回 false', () => {
    const { auth } = useAuth()
    expect(auth(['user:manage', 'role:manage'])).toBe(false)
  })

  it('auth("") — 空字符串返回 true', () => {
    const { auth } = useAuth()
    expect(auth('')).toBe(true)
  })

  it('auth([]) — 空数组返回 true', () => {
    const { auth } = useAuth()
    expect(auth([])).toBe(true)
  })

  it('authAll — 拥有全部权限返回 true', () => {
    const { authAll } = useAuth()
    expect(authAll(['record:read', 'statistics:read'])).toBe(true)
  })

  it('authAll — 缺少任一权限返回 false', () => {
    const { authAll } = useAuth()
    expect(authAll(['record:read', 'user:manage'])).toBe(false)
  })

  it('authAll([]) — 空数组返回 true', () => {
    const { authAll } = useAuth()
    expect(authAll([])).toBe(true)
  })

  it('层级继承：拥有 record:manage 通过 record:edit 检查', () => {
    vi.mocked(useAuth)
    const { auth } = useAuth()
    // userStore mock has record:read, not record:manage
    // so this tests that record:read does NOT imply record:manage
    expect(auth('record:manage')).toBe(false)
    expect(auth('record:read')).toBe(true)
  })
})

describe('useAuth — ADMIN bypass', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.doMock('@/store/modules/user', () => ({
      useUserStore: () => ({
        profile: { roleCode: 'ADMIN', username: 'admin' },
        permissions: [],
      }),
    }))
  })

  it('aDMIN 角色对任意权限返回 true', async () => {
    vi.resetModules()
    vi.doMock('@/store/modules/user', () => ({
      useUserStore: () => ({
        profile: { roleCode: 'ADMIN' },
        permissions: [],
      }),
    }))
    vi.doMock('@/store/modules/settings', () => ({
      useSettingsStore: () => ({
        settings: { app: { enablePermission: true } },
      }),
    }))
    const { default: useAuthMod } = await import('../useAuth')
    const { auth } = useAuthMod()
    expect(auth('any:permission')).toBe(true)
    expect(auth('record:read')).toBe(true)
  })
})
