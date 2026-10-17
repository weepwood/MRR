import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useSettingsStore } from '../settings'

/**
 * 全站界面配置同步此前完全没有测试覆盖，而它承载两个极易被改坏的语义：
 * 白名单只推指定键、服务端值优先的合并方向。
 *
 * 这里实例化真实 store，只替换认证存储、请求层与提示组件；
 * 推送路径统一走 `saveAppSettings()`（本机持久化 + 防抖推送），
 * 以便同时覆盖 `scheduleUiSettingsPush` 的防抖行为。
 */
const mocks = vi.hoisted(() => ({
  readAuthStorage: vi.fn(),
  saveSystemSettings: vi.fn(),
  getUiSettings: vi.fn(),
  toastError: vi.fn(),
}))

vi.mock('@/utils/auth-storage', () => ({
  readAuthStorage: mocks.readAuthStorage,
}))

vi.mock('@/api/modules/settings', () => ({
  saveSystemSettings: mocks.saveSystemSettings,
  getUiSettings: mocks.getUiSettings,
}))

vi.mock('vue-sonner', () => ({
  toast: {
    error: mocks.toastError,
    success: vi.fn(),
    info: vi.fn(),
  },
}))

const SERVER_KEY = 'adminAppSettings'
const PUSH_DELAY_MS = 800

function session(roleCode: string, permissions: string[] = [], token = 'session-token') {
  return { token, account: 'tester', avatar: '', profile: { roleCode }, permissions }
}

/** 触发一次保存并推进防抖计时器，让推送真正发生。 */
async function saveAndFlush(store: ReturnType<typeof useSettingsStore>) {
  store.saveAppSettings()
  await vi.advanceTimersByTimeAsync(PUSH_DELAY_MS)
}

/** 读取最近一次推送的服务端载荷。 */
function lastPushedPayload(): Record<string, unknown> {
  const calls = mocks.saveSystemSettings.mock.calls
  expect(calls.length).toBeGreaterThan(0)
  const body = calls[calls.length - 1]?.[0] as Record<string, string>
  return JSON.parse(body[SERVER_KEY]) as Record<string, unknown>
}

describe('settings store — 全站界面配置同步', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.useFakeTimers()
    mocks.readAuthStorage.mockReset()
    mocks.saveSystemSettings.mockReset()
    mocks.getUiSettings.mockReset()
    mocks.toastError.mockReset()
    mocks.readAuthStorage.mockReturnValue(session('ADMIN'))
    mocks.saveSystemSettings.mockResolvedValue(undefined)
    mocks.getUiSettings.mockResolvedValue({ data: {} })
    // 真实 store 的 setup 会调用 matchMedia，而 jsdom 未实现。
    const matchMedia = vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })
    vi.stubGlobal('matchMedia', matchMedia)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('推送（saveAppSettings → scheduleUiSettingsPush → pushUiSettings）', () => {
    it('无登录令牌时不发起请求', async () => {
      mocks.readAuthStorage.mockReturnValue(session('ADMIN', [], ''))
      const store = useSettingsStore()

      await saveAndFlush(store)

      expect(mocks.saveSystemSettings).not.toHaveBeenCalled()
      expect(store.uiSettingsSyncState).toBe('idle')
    })

    it('缺少 system:manage 权限时降级为仅本机生效，且不发起必然失败的请求', async () => {
      mocks.readAuthStorage.mockReturnValue(session('DOCTOR', ['record:read']))
      const store = useSettingsStore()

      await saveAndFlush(store)

      expect(mocks.saveSystemSettings).not.toHaveBeenCalled()
      expect(store.uiSettingsSyncState).toBe('local-only')
      expect(mocks.toastError).not.toHaveBeenCalled()
    })

    it('持有 system:manage 权限时推送并标记已同步', async () => {
      mocks.readAuthStorage.mockReturnValue(session('USER', ['system:manage']))
      const store = useSettingsStore()

      await saveAndFlush(store)

      expect(mocks.saveSystemSettings).toHaveBeenCalledTimes(1)
      expect(store.uiSettingsSyncState).toBe('synced')
    })

    it('aDMIN 角色无需显式权限项即可推送', async () => {
      const store = useSettingsStore()

      await saveAndFlush(store)

      expect(mocks.saveSystemSettings).toHaveBeenCalledTimes(1)
      expect(store.uiSettingsSyncState).toBe('synced')
    })

    it('只推送白名单内的键，且排除本机折叠状态等个人偏好', async () => {
      const store = useSettingsStore()
      store.settings.menu.subMenuCollapse = true
      store.settings.menu.mode = 'head'
      store.settings.app.themeColor = '#16803C'

      await saveAndFlush(store)

      const payload = lastPushedPayload()
      const menu = payload.menu as Record<string, unknown>
      const app = payload.app as Record<string, unknown>

      expect(app.themeColor).toBe('#16803C')
      expect(menu.mode).toBe('head')
      expect(menu).not.toHaveProperty('subMenuCollapse')
      expect(app).not.toHaveProperty('enablePermission')
    })

    it('连续调整只推送一次（防抖合并）', async () => {
      const store = useSettingsStore()

      store.saveAppSettings()
      await vi.advanceTimersByTimeAsync(200)
      store.saveAppSettings()
      await vi.advanceTimersByTimeAsync(200)
      store.saveAppSettings()
      await vi.advanceTimersByTimeAsync(PUSH_DELAY_MS)

      expect(mocks.saveSystemSettings).toHaveBeenCalledTimes(1)
    })

    it('推送同时把配置写入本机存储', async () => {
      const store = useSettingsStore()
      store.settings.menu.mode = 'head'

      await saveAndFlush(store)

      const saved = localStorage.getItem('MRR-ADMIN:app-settings')
      expect(saved).toBeTruthy()
      expect(JSON.parse(String(saved)).menu.mode).toBe('head')
    })

    it('服务端返回 403 时降级为仅本机生效，不弹出网络错误提示', async () => {
      mocks.readAuthStorage.mockReturnValue(session('USER', ['system:manage']))
      mocks.saveSystemSettings.mockRejectedValue(Object.assign(new Error('forbidden'), { code: 403 }))
      const store = useSettingsStore()

      await saveAndFlush(store)

      expect(store.uiSettingsSyncState).toBe('local-only')
      expect(mocks.toastError).not.toHaveBeenCalled()
    })

    it('网络失败时保留本机配置并显式提示，不再静默吞掉错误', async () => {
      mocks.readAuthStorage.mockReturnValue(session('USER', ['system:manage']))
      mocks.saveSystemSettings.mockRejectedValue(new Error('network down'))
      const store = useSettingsStore()

      await saveAndFlush(store)

      expect(store.uiSettingsSyncState).toBe('failed')
      expect(mocks.toastError).toHaveBeenCalledTimes(1)
      expect(String(mocks.toastError.mock.calls[0]?.[0])).toContain('同步到全站失败')
    })
  })

  describe('拉取（loadUiSettings）', () => {
    it('无登录令牌时不发起请求，返回 false 以便后续重试', async () => {
      mocks.readAuthStorage.mockReturnValue(session('ADMIN', [], ''))

      const result = await useSettingsStore().loadUiSettings()

      expect(result).toBe(false)
      expect(mocks.getUiSettings).not.toHaveBeenCalled()
    })

    it('缺少 system:manage 权限时不拉取服务端值，避免本机调整被静默覆盖', async () => {
      mocks.readAuthStorage.mockReturnValue(session('DOCTOR', ['record:read']))
      const store = useSettingsStore()
      store.settings.app.themeColor = '#7C3AED'

      const result = await store.loadUiSettings()

      expect(result).toBe(true)
      expect(mocks.getUiSettings).not.toHaveBeenCalled()
      expect(store.settings.app.themeColor).toBe('#7C3AED')
      expect(store.uiSettingsSyncState).toBe('local-only')
    })

    it('服务端值与本地冲突时服务端优先', async () => {
      mocks.getUiSettings.mockResolvedValue({
        data: {
          [SERVER_KEY]: JSON.stringify({
            app: { themeColor: '#16803C' },
            menu: { mode: 'head' },
          }),
        },
      })
      const store = useSettingsStore()
      store.settings.app.themeColor = '#DC2626'
      store.settings.menu.mode = 'side'

      expect(await store.loadUiSettings()).toBe(true)
      expect(store.settings.app.themeColor).toBe('#16803C')
      expect(store.settings.menu.mode).toBe('head')
    })

    it('服务端无数据时视为已完成，避免重复请求', async () => {
      mocks.getUiSettings.mockResolvedValue({ data: {} })

      expect(await useSettingsStore().loadUiSettings()).toBe(true)
    })

    it('服务端返回非法 JSON 时不破坏本机配置', async () => {
      mocks.getUiSettings.mockResolvedValue({ data: { [SERVER_KEY]: 'not-json' } })
      const store = useSettingsStore()
      store.settings.app.themeColor = '#7C3AED'

      expect(await store.loadUiSettings()).toBe(false)
      expect(store.settings.app.themeColor).toBe('#7C3AED')
    })
  })
})
