import type { EffectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { useOcrJob } from '../useOcrJob'

const api = vi.hoisted(() => ({ getOcrJob: vi.fn() }))
vi.mock('@/api/modules/ocr', () => api)

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}

describe('oCR只读轮询生命周期', () => {
  const scopes: EffectScope[] = []
  function setup() {
    const scope = effectScope()
    scopes.push(scope)
    return { scope, task: scope.run(() => useOcrJob())! }
  }
  beforeEach(() => {
    vi.resetAllMocks()
    vi.useFakeTimers()
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
  })
  afterEach(() => {
    scopes.splice(0).forEach(scope => scope.stop())
    vi.clearAllTimers()
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('销毁后follow不请求、不回填、不创建定时器', async () => {
    api.getOcrJob.mockResolvedValue({ data: { id: 'late', status: 'RUNNING' } })
    const { scope, task } = setup()
    scope.stop()
    task.follow('late')
    await vi.advanceTimersByTimeAsync(9000)
    expect(api.getOcrJob).not.toHaveBeenCalled()
    expect(task.job.value).toBeNull()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('销毁与旧请求交错后也不能通过新follow恢复轮询', async () => {
    const old = deferred<{ data: { id: string, status: string } }>()
    api.getOcrJob.mockReturnValueOnce(old.promise).mockResolvedValue({ data: { id: 'new', status: 'RUNNING' } })
    const { scope, task } = setup()
    task.follow('old')
    scope.stop()
    task.follow('new')
    old.resolve({ data: { id: 'old', status: 'SUCCESS' } })
    await vi.advanceTimersByTimeAsync(9000)
    expect(api.getOcrJob).toHaveBeenCalledTimes(1)
    expect(task.job.value).toBeNull()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('新任务开始等待时立即清除旧任务错误', async () => {
    api.getOcrJob.mockRejectedValueOnce(new Error('synthetic error'))
    const { task } = setup()
    task.follow('old')
    await vi.advanceTimersByTimeAsync(0)
    expect(task.error.value).not.toBe('')
    api.getOcrJob.mockReturnValue(new Promise(() => {}))
    task.follow('new')
    expect(task.error.value).toBe('')
    expect(vi.getTimerCount()).toBe(0)
  })

  it('隐藏时暂停并忽略旧响应，恢复可继续跟踪当前任务', async () => {
    const old = deferred<{ data: { id: string, status: string } }>()
    api.getOcrJob.mockReturnValueOnce(old.promise).mockResolvedValue({ data: { id: 'current', status: 'SUCCESS' } })
    const { task } = setup()
    task.follow('current')
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(true)
    document.dispatchEvent(new Event('visibilitychange'))
    old.resolve({ data: { id: 'old-response', status: 'RUNNING' } })
    await vi.advanceTimersByTimeAsync(6000)
    expect(task.job.value).toBeNull()
    expect(api.getOcrJob).toHaveBeenCalledTimes(1)
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(6000)
    expect(task.job.value?.id).toBe('current')
    expect(api.getOcrJob).toHaveBeenCalledTimes(2)
    expect(vi.getTimerCount()).toBe(0)
  })
})
