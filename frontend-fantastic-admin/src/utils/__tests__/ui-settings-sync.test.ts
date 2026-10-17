import type { Pinia } from 'pinia'
import type { Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { installUiSettingsSync } from '../ui-settings-sync'

/**
 * 全站界面配置同步的安装逻辑此前零测试。它有两个容易改坏的约定：
 * 没有登录令牌时不发请求，以及失败后按最小间隔限流重试、成功即停止。
 */
const mocks = vi.hoisted(() => ({
  readAuthStorage: vi.fn(),
  loadUiSettings: vi.fn(),
}))

vi.mock('@/utils/auth-storage', () => ({
  readAuthStorage: mocks.readAuthStorage,
}))

vi.mock('@/store/modules/settings', () => ({
  useSettingsStore: () => ({ loadUiSettings: mocks.loadUiSettings }),
}))

/** 与实现中的最小重试间隔保持一致。 */
const RETRY_INTERVAL_MS = 5000

interface RouterStub {
  afterEach: (hook: () => void) => void
  navigate: () => void
}

function createRouterStub(): RouterStub {
  const hooks: Array<() => void> = []
  return {
    afterEach(hook: () => void) {
      hooks.push(hook)
    },
    navigate() {
      hooks.forEach(hook => hook())
    },
  }
}

function install(router: RouterStub) {
  installUiSettingsSync(router as unknown as Router, {} as Pinia)
}

/** 排空 promise 微任务队列，让 loadUiSettings 的 then 回调执行。 */
async function flush() {
  await Promise.resolve()
  await Promise.resolve()
  await Promise.resolve()
}

describe('ui-settings-sync — 全站界面配置同步安装', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mocks.readAuthStorage.mockReset()
    mocks.loadUiSettings.mockReset()
    mocks.readAuthStorage.mockReturnValue({ token: 'session-token' })
    mocks.loadUiSettings.mockResolvedValue(true)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('无登录令牌时不发起拉取', async () => {
    mocks.readAuthStorage.mockReturnValue({ token: '' })

    install(createRouterStub())
    await flush()

    expect(mocks.loadUiSettings).not.toHaveBeenCalled()
  })

  it('安装时立即尝试一次拉取', async () => {
    install(createRouterStub())
    await flush()

    expect(mocks.loadUiSettings).toHaveBeenCalledTimes(1)
  })

  it('成功一次后不再重复拉取', async () => {
    const router = createRouterStub()
    install(router)
    await flush()

    await vi.advanceTimersByTimeAsync(RETRY_INTERVAL_MS)
    router.navigate()
    await flush()

    expect(mocks.loadUiSettings).toHaveBeenCalledTimes(1)
  })

  it('失败后在最小重试间隔内不重试，间隔结束后才重试', async () => {
    mocks.loadUiSettings.mockResolvedValue(false)
    const router = createRouterStub()
    install(router)
    await flush()
    expect(mocks.loadUiSettings).toHaveBeenCalledTimes(1)

    router.navigate()
    await flush()
    expect(mocks.loadUiSettings).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(RETRY_INTERVAL_MS)
    router.navigate()
    await flush()
    expect(mocks.loadUiSettings).toHaveBeenCalledTimes(2)
  })

  it('重试成功一次后停止继续重试', async () => {
    mocks.loadUiSettings.mockResolvedValueOnce(false)
    const router = createRouterStub()
    install(router)
    await flush()
    expect(mocks.loadUiSettings).toHaveBeenCalledTimes(1)

    mocks.loadUiSettings.mockResolvedValue(true)
    await vi.advanceTimersByTimeAsync(RETRY_INTERVAL_MS)
    router.navigate()
    await flush()
    expect(mocks.loadUiSettings).toHaveBeenCalledTimes(2)

    await vi.advanceTimersByTimeAsync(RETRY_INTERVAL_MS)
    router.navigate()
    await flush()
    expect(mocks.loadUiSettings).toHaveBeenCalledTimes(2)
  })
})
