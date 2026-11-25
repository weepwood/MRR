import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useRuntimeErrorsBadgeStore } from '../runtimeErrorsBadge'

const api = vi.hoisted(() => ({
  getSystemErrorOverview: vi.fn(),
}))
const session = vi.hoisted(() => ({ user: null as null | { permissions: string[], token: string, sessionEpoch: number, isSessionVerified: boolean, profile: { roleCode: string } } }))

vi.mock('@/api/modules/system-errors', () => api)
vi.mock('@/store/modules/user', async () => {
  const { reactive } = await import('vue')
  session.user = reactive({ permissions: ['system:error:read'], token: 'synthetic-a', sessionEpoch: 1, isSessionVerified: true, profile: { roleCode: 'OPERATOR' } })
  return { useUserStore: () => session.user }
})

beforeEach(() => {
  setActivePinia(createPinia())
  Object.assign(session.user!, { permissions: ['system:error:read'], token: 'synthetic-a', sessionEpoch: 1, isSessionVerified: true })
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
    session.user!.permissions = []
    const store = useRuntimeErrorsBadgeStore()
    await store.refresh()
    expect(api.getSystemErrorOverview).not.toHaveBeenCalled()
    expect(store.recentErrorGroups).toBe(0)
    session.user!.permissions = ['system:error:read']
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

it('停止、权限撤销及会话变化使角标迟到响应失效', async () => {
  const store = useRuntimeErrorsBadgeStore()
  api.getSystemErrorOverview.mockResolvedValueOnce({ data: { recentErrorGroups: 8 } })
  await store.refresh()
  let finish!: (value: unknown) => void
  api.getSystemErrorOverview.mockReturnValueOnce(new Promise((resolve) => { finish = resolve }))
  const old = store.refresh()
  session.user!.permissions = []
  expect(store.recentErrorGroups).toBe(0)
  finish({ data: { recentErrorGroups: 99 } })
  await old
  expect(store.recentErrorGroups).toBe(0)
  session.user!.permissions = ['system:error:read']
  api.getSystemErrorOverview.mockReturnValueOnce(new Promise((resolve) => { finish = resolve }))
  const second = store.refresh()
  session.user!.sessionEpoch++
  store.stop()
  finish({ data: { recentErrorGroups: 99 } })
  await second
  expect(store.recentErrorGroups).toBe(0)
})

it('服务端403清除旧角标，普通网络错误仍保留', async () => {
  const store = useRuntimeErrorsBadgeStore()
  api.getSystemErrorOverview.mockResolvedValueOnce({ data: { recentErrorGroups: 8 } })
  await store.refresh()
  api.getSystemErrorOverview.mockRejectedValueOnce({ response: { status: 403 } })
  await store.refresh()
  expect(store.recentErrorGroups).toBe(0)
})
