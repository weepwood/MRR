import type { MigrationJob } from '@/api/modules/oss'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, ref } from 'vue'
import { getMigrationJob } from '@/api/modules/oss'
import { useMigrationPolling } from './useMigrationPolling'

vi.mock('@/api/modules/oss', () => ({ getMigrationJob: vi.fn() }))

function setup() {
  const job = ref<MigrationJob>({ id: 1, status: 'running' })
  const refresh = vi.fn().mockResolvedValue(undefined)
  const overview = vi.fn().mockResolvedValue(undefined)
  const scope = effectScope()
  const polling = scope.run(() => useMigrationPolling(job, refresh, overview))!
  return { job, refresh, overview, scope, polling }
}

describe('migration polling lifecycle', () => {
  beforeEach(() => { vi.useFakeTimers(); vi.mocked(getMigrationJob).mockReset() })
  afterEach(() => { vi.clearAllTimers(); vi.useRealTimers() })

  it('does not overlap slow job requests', async () => {
    const context = setup()
    vi.mocked(getMigrationJob).mockReturnValue(new Promise(() => {}))
    context.polling.startPolling()
    context.polling.startPolling()
    await vi.advanceTimersByTimeAsync(8_000)
    expect(getMigrationJob).toHaveBeenCalledTimes(1)
    context.scope.stop()
  })

  it('ignores a response for an old job', async () => {
    const context = setup()
    let resolve!: (value: any) => void
    vi.mocked(getMigrationJob).mockReturnValue(new Promise((r) => { resolve = r }))
    context.polling.startPolling()
    await vi.advanceTimersByTimeAsync(2_000)
    context.job.value = { id: 2, status: 'running' }
    resolve({ data: { id: 1, status: 'completed' } })
    await vi.advanceTimersByTimeAsync(0)
    expect(context.job.value.id).toBe(2)
    expect(context.refresh).not.toHaveBeenCalled()
    context.scope.stop()
  })

  it('stops at a terminal status and refreshes once', async () => {
    const context = setup()
    vi.mocked(getMigrationJob).mockResolvedValue({ data: { id: 1, status: 'completed' } } as any)
    context.polling.startPolling()
    await vi.advanceTimersByTimeAsync(10_000)
    expect(getMigrationJob).toHaveBeenCalledTimes(1)
    expect(context.refresh).toHaveBeenCalledTimes(1)
    context.scope.stop()
  })

  it('keeps polling a new job when an old job request rejects late', async () => {
    const context = setup()
    let reject!: (reason: Error) => void
    vi.mocked(getMigrationJob).mockReturnValueOnce(new Promise((_, r) => { reject = r }))
    vi.mocked(getMigrationJob).mockResolvedValue({ data: { id: 2, status: 'running' } } as any)
    context.polling.startPolling()
    await vi.advanceTimersByTimeAsync(2_000)
    context.job.value = { id: 2, status: 'running' }
    context.polling.startPolling()
    reject(new Error('old job failed'))
    await vi.advanceTimersByTimeAsync(2_000)
    expect(getMigrationJob).toHaveBeenLastCalledWith(2)
    expect(vi.getTimerCount()).toBe(1)
    context.scope.stop()
  })

  it('stops on failure, then permits an explicit restart', async () => {
    const context = setup()
    vi.mocked(getMigrationJob).mockRejectedValue(new Error('synthetic failure'))
    context.polling.startPolling()
    await vi.advanceTimersByTimeAsync(6_000)
    expect(getMigrationJob).toHaveBeenCalledTimes(1)
    context.polling.startPolling()
    await vi.advanceTimersByTimeAsync(2_000)
    expect(getMigrationJob).toHaveBeenCalledTimes(2)
    context.scope.stop()
  })

  it('disposal cancels timers and ignores pending job completion', async () => {
    const context = setup()
    let resolve!: (value: any) => void
    vi.mocked(getMigrationJob).mockReturnValue(new Promise((r) => { resolve = r }))
    context.polling.startPolling()
    context.polling.startOverviewRefresh()
    await vi.advanceTimersByTimeAsync(2_000)
    context.scope.stop()
    resolve({ data: { id: 1, status: 'completed' } })
    await vi.advanceTimersByTimeAsync(30_000)
    expect(context.job.value.status).toBe('running')
    expect(context.refresh).not.toHaveBeenCalled()
    expect(context.overview).not.toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('does not overlap overview refresh and supports disabling it', async () => {
    const context = setup()
    context.overview.mockReturnValue(new Promise(() => {}))
    context.polling.startOverviewRefresh()
    await vi.advanceTimersByTimeAsync(60_000)
    expect(context.overview).toHaveBeenCalledTimes(1)
    context.polling.stopOverviewRefresh()
    await vi.advanceTimersByTimeAsync(60_000)
    expect(context.overview).toHaveBeenCalledTimes(1)
    context.scope.stop()
  })

  it('ignores a stopped request after restarting the same job', async () => {
    const context = setup()
    let resolve!: (value: any) => void
    vi.mocked(getMigrationJob).mockReturnValueOnce(new Promise((r) => { resolve = r }))
    context.polling.startPolling()
    await vi.advanceTimersByTimeAsync(2_000)
    context.polling.stopPolling()
    context.polling.startPolling()
    resolve({ data: { id: 1, status: 'completed' } })
    await vi.advanceTimersByTimeAsync(0)
    expect(context.job.value.status).toBe('running')
    context.scope.stop()
  })

  it('does not create a job timer without a selected job', async () => {
    const context = setup()
    context.job.value = { status: 'running' }
    context.polling.startPolling()
    await vi.advanceTimersByTimeAsync(10_000)
    expect(getMigrationJob).not.toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)
    context.scope.stop()
  })

  it('allows overview to recover after a failed refresh', async () => {
    const context = setup()
    context.overview.mockRejectedValueOnce(new Error('synthetic failure'))
    context.polling.startOverviewRefresh()
    await vi.advanceTimersByTimeAsync(30_000)
    expect(context.overview).toHaveBeenCalledTimes(2)
    context.scope.stop()
  })
})
