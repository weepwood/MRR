import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useRuntimeErrorsBadgeStore } from '../runtimeErrorsBadge'

const api = vi.hoisted(() => ({
  getSystemErrorOverview: vi.fn(),
}))
const userState = vi.hoisted(() => ({ permissions: ['system:error:read'] }))

vi.mock('@/api/modules/system-errors', () => api)
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({ permissions: userState.permissions }),
}))

beforeEach(() => {
  setActivePinia(createPinia())
  vi.useFakeTimers()
  api.getSystemErrorOverview.mockReset()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('runtimeErrorsBadge store', () => {
  it('refresh 写入近 24h ERROR 组数', async () => {
    api.getSystemErrorOverview.mockResolvedValue({ data: { recentErrorGroups: 3 } })
    const store = useRuntimeErrorsBadgeStore()
    await store.refresh()
    expect(store.recentErrorGroups).toBe(3)
  })

  it('接口失败保留旧值', async () => {
    api.getSystemErrorOverview.mockResolvedValue({ data: { recentErrorGroups: 2 } })
    const store = useRuntimeErrorsBadgeStore()
    await store.refresh()
    api.getSystemErrorOverview.mockRejectedValue(new Error('network'))
    await store.refresh()
    expect(store.recentErrorGroups).toBe(2)
  })

  it('无 system:error:read 权限不发起请求', async () => {
    userState.permissions = []
    const store = useRuntimeErrorsBadgeStore()
    await store.refresh()
    expect(api.getSystemErrorOverview).not.toHaveBeenCalled()
    expect(store.recentErrorGroups).toBe(0)
    userState.permissions = ['system:error:read']
  })

  it('start 幂等并按 30 秒轮询，页面隐藏时跳过', async () => {
    api.getSystemErrorOverview.mockResolvedValue({ data: { recentErrorGroups: 1 } })
    const store = useRuntimeErrorsBadgeStore()
    store.start()
    store.start()
    await vi.advanceTimersByTimeAsync(0)
    expect(api.getSystemErrorOverview).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(30_000)
    expect(api.getSystemErrorOverview).toHaveBeenCalledTimes(2)

    Object.defineProperty(document, 'hidden', { value: true, configurable: true })
    await vi.advanceTimersByTimeAsync(30_000)
    expect(api.getSystemErrorOverview).toHaveBeenCalledTimes(2)

    Object.defineProperty(document, 'hidden', { value: false, configurable: true })
    await vi.advanceTimersByTimeAsync(30_000)
    expect(api.getSystemErrorOverview).toHaveBeenCalledTimes(3)
  })

  it('stop 清理计时器并允许重新 start', async () => {
    api.getSystemErrorOverview.mockResolvedValue({ data: { recentErrorGroups: 4 } })
    const store = useRuntimeErrorsBadgeStore()
    store.start()
    await vi.advanceTimersByTimeAsync(0)
    expect(api.getSystemErrorOverview).toHaveBeenCalledTimes(1)

    store.stop()
    expect(store.recentErrorGroups).toBe(0)
    await vi.advanceTimersByTimeAsync(90_000)
    expect(api.getSystemErrorOverview).toHaveBeenCalledTimes(1)

    store.start()
    await vi.advanceTimersByTimeAsync(0)
    expect(api.getSystemErrorOverview).toHaveBeenCalledTimes(2)
    store.stop()
  })
})
