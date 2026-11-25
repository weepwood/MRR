import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useUserStore } from '../user'

const api = vi.hoisted(() => ({ login: vi.fn(), permission: vi.fn(), logout: vi.fn() }))
const push = vi.hoisted(() => vi.fn().mockResolvedValue(undefined))
const storage = vi.hoisted(() => ({ clear: vi.fn(), write: vi.fn(), profile: vi.fn() }))
vi.mock('@/api/modules/user', () => ({ default: api }))
vi.mock('@/router', () => ({ default: { currentRoute: { value: { fullPath: '/logs', name: 'logs' } }, push } }))
vi.mock('@/utils/auth-storage', () => ({
  readAuthStorage: () => ({ account: '', token: '', avatar: '', permissions: [], profile: {} }),
  clearAuthSessionStorage: storage.clear,
  writeAuthSession: storage.write,
  writeAuthProfile: storage.profile,
  writeDemoLoggedOut: vi.fn(),
}))
vi.mock('../settings', () => ({ useSettingsStore: () => ({ settings: { home: { fullPath: '/' } }, updateSettings: vi.fn() }) }))
vi.mock('../route', () => ({ useRouteStore: () => ({ removeRoutes: vi.fn() }) }))
vi.mock('../menu', () => ({ useMenuStore: () => ({ setActived: vi.fn() }) }))
vi.mock('../tabbar', () => ({ useTabbarStore: () => ({ clean: vi.fn() }) }))

function pending<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}
const user = (username: string) => ({ username, roleCode: 'OPERATOR', permissions: ['log:read'] })

beforeEach(() => {
  vi.clearAllMocks()
  push.mockResolvedValue(undefined)
  setActivePinia(createPinia())
  api.logout.mockResolvedValue(undefined)
})

describe('会话代次与注销', () => {
  it('切换会话后旧资料响应不覆盖当前用户', async () => {
    const store = useUserStore()
    store.setSession({ token: 'a', user: user('alpha') })
    const old = pending<{ data: ReturnType<typeof user> }>()
    api.permission.mockReturnValueOnce(old.promise)
    const request = store.getPermissions()
    expect(api.permission).toHaveBeenLastCalledWith('a')
    store.setSession({ token: 'b', user: user('beta') })
    old.resolve({ data: user('alpha') })
    await request
    expect(store.profile.username).toBe('beta')
    expect(store.token).toBe('b')
    expect(storage.profile).not.toHaveBeenCalled()
  })

  it('同一会话的迟到资料不能覆盖更新权限', async () => {
    const store = useUserStore()
    store.setSession({ token: 'a', user: user('alpha') })
    const old = pending<{ data: ReturnType<typeof user> }>()
    api.permission.mockReturnValueOnce(old.promise).mockResolvedValueOnce({ data: { ...user('alpha'), permissions: [] } })
    const request = store.getPermissions()
    await store.getPermissions()
    old.resolve({ data: user('alpha') })
    await request
    expect(store.permissions).toEqual([])
  })

  it('本地注销同步完成，旧撤销请求不清除新会话', async () => {
    const store = useUserStore()
    store.setSession({ token: 'a', user: user('alpha') })
    const revoke = pending<void>()
    api.logout.mockReturnValueOnce(revoke.promise)
    const epoch = store.sessionEpoch
    const logout = store.logout()
    expect(store.sessionEpoch).toBeGreaterThan(epoch)
    expect(store.token).toBe('')
    expect(store.profile).toEqual({})
    expect(store.permissions).toEqual([])
    if (!import.meta.env.VITE_APP_DEMO_MODE) { expect(api.logout).toHaveBeenCalledWith('a') }
    store.setSession({ token: 'b', user: user('beta') })
    revoke.resolve()
    await logout
    expect(store.token).toBe('b')
    expect(store.profile.username).toBe('beta')
  })

  it('注销期间迟到的登录响应不能恢复会话', async () => {
    const store = useUserStore()
    const old = pending<{ data: { token: string, user: ReturnType<typeof user> } }>()
    api.login.mockReturnValueOnce(old.promise)
    const login = store.login({ account: 'alpha', password: 'synthetic' })
    store.clearSession()
    old.resolve({ data: { token: 'a', user: user('alpha') } })
    await expect(login).rejects.toThrow('已失效')
    expect(store.isLogin).toBe(false)
  })
})
