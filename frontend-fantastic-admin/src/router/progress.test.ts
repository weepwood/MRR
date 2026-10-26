import type { Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import setupProgress from './progress'

const mocks = vi.hoisted(() => ({
  loading: { value: false },
  settings: { settings: { app: { enableProgress: true } } },
  useNProgress: vi.fn(),
}))

vi.mock('@vueuse/integrations/useNProgress', () => ({
  useNProgress: mocks.useNProgress,
}))
vi.mock('@/store/modules/settings', () => ({
  useSettingsStore: () => mocks.settings,
}))

async function createTestRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['/', '/first', '/second'].map(path => ({ path, component: { template: '<div />' } })),
  })
  setupProgress(router)
  await router.push('/')
  return router
}

async function holdNavigation(router: Router, path: string, error?: Error, startPath = path) {
  let release!: () => void
  let entered!: () => void
  const blocked = new Promise<void>((resolve) => { release = resolve })
  const started = new Promise<void>((resolve) => { entered = resolve })
  router.beforeResolve(async (to) => {
    if (to.path === path) {
      entered()
      await blocked
      if (error) {
        throw error
      }
    }
  })
  const navigation = router.push(startPath).catch(reason => reason)
  await started
  return { release, navigation }
}

describe('路由顶部进度条', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mocks.loading.value = false
    mocks.settings.settings.app.enableProgress = true
    mocks.useNProgress.mockReset().mockReturnValue({ isLoading: mocks.loading })
  })

  afterEach(() => {
    vi.clearAllTimers()
    vi.useRealTimers()
  })

  it('关闭转圈动画', async () => {
    await createTestRouter()
    expect(mocks.useNProgress).toHaveBeenCalledWith(null, expect.objectContaining({ showSpinner: false }))
  })

  it('快速完成的导航不显示进度条，也不留下延迟任务', async () => {
    const router = await createTestRouter()
    const pending = await holdNavigation(router, '/first')
    vi.advanceTimersByTime(199)
    expect(mocks.loading.value).toBe(false)
    pending.release()
    await pending.navigation
    vi.advanceTimersByTime(500)
    expect(mocks.loading.value).toBe(false)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('等待200ms后显示，导航完成后结束', async () => {
    const router = await createTestRouter()
    const pending = await holdNavigation(router, '/first')
    vi.advanceTimersByTime(199)
    expect(mocks.loading.value).toBe(false)
    vi.advanceTimersByTime(1)
    expect(mocks.loading.value).toBe(true)
    pending.release()
    await pending.navigation
    expect(mocks.loading.value).toBe(false)
  })

  it('旧导航被取消时不会结束正在进行的新导航', async () => {
    const router = await createTestRouter()
    const first = await holdNavigation(router, '/first')
    vi.advanceTimersByTime(200)
    const second = await holdNavigation(router, '/second')
    first.release()
    await first.navigation
    expect(mocks.loading.value).toBe(true)
    second.release()
    await second.navigation
    expect(mocks.loading.value).toBe(false)
  })

  it('导航出错时清理已显示的进度条', async () => {
    const router = await createTestRouter()
    const error = new Error('合成页面加载失败')
    const pending = await holdNavigation(router, '/first', error)
    vi.advanceTimersByTime(200)
    expect(mocks.loading.value).toBe(true)
    pending.release()
    expect(await pending.navigation).toBe(error)
    expect(mocks.loading.value).toBe(false)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('旧导航迟到的异常不会结束新导航', async () => {
    const router = await createTestRouter()
    const error = new Error('合成旧页面加载失败')
    const first = await holdNavigation(router, '/first', error)
    vi.advanceTimersByTime(200)
    const second = await holdNavigation(router, '/second')
    first.release()
    expect(await first.navigation).toBe(error)
    expect(mocks.loading.value).toBe(true)
    second.release()
    await second.navigation
    expect(mocks.loading.value).toBe(false)
  })

  it('重定向后跟随最终目标显示和结束', async () => {
    const router = await createTestRouter()
    router.beforeResolve(to => to.path === '/first' ? '/second' : undefined)
    const pending = await holdNavigation(router, '/second', undefined, '/first')
    vi.advanceTimersByTime(199)
    expect(mocks.loading.value).toBe(false)
    vi.advanceTimersByTime(1)
    expect(mocks.loading.value).toBe(true)
    pending.release()
    await pending.navigation
    expect(router.currentRoute.value.path).toBe('/second')
    expect(mocks.loading.value).toBe(false)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('显示前导航出错不会随后闪现', async () => {
    const router = await createTestRouter()
    const pending = await holdNavigation(router, '/first', new Error('合成加载失败'))
    pending.release()
    await pending.navigation
    vi.advanceTimersByTime(500)
    expect(mocks.loading.value).toBe(false)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('关闭进度条设置时不显示', async () => {
    mocks.settings.settings.app.enableProgress = false
    const router = await createTestRouter()
    const pending = await holdNavigation(router, '/first')
    vi.advanceTimersByTime(500)
    expect(mocks.loading.value).toBe(false)
    pending.release()
    await pending.navigation
  })

  it('等待期间关闭设置后不显示，结束时仍能清理', async () => {
    const router = await createTestRouter()
    const pending = await holdNavigation(router, '/first')
    mocks.settings.settings.app.enableProgress = false
    vi.advanceTimersByTime(200)
    expect(mocks.loading.value).toBe(false)
    pending.release()
    await pending.navigation
    expect(vi.getTimerCount()).toBe(0)
  })
})
