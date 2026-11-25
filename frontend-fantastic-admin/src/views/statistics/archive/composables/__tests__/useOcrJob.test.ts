import { flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'

const getJob = vi.hoisted(() => vi.fn())
vi.mock('@/api/modules/ocr', () => ({ getOcrJob: getJob }))

describe('oCR task polling', () => {
  afterEach(() => { vi.useRealTimers(); vi.clearAllMocks() })

  it('rejects wrong job or archive and stops polling until explicit refresh', async () => {
    const { useOcrJob } = await import('../useOcrJob')
    vi.useFakeTimers()
    getJob.mockResolvedValueOnce({ data: { id: 'other', archiveId: 1, status: 'RUNNING' } })
      .mockResolvedValueOnce({ data: { id: 'current', archiveId: 2, status: 'RUNNING' } })
      .mockResolvedValueOnce({ data: { id: 'current', archiveId: 1, status: 'SUCCESS' } })
    const scope = effectScope()
    const poll = scope.run(() => useOcrJob())!
    poll.follow('current', 1)
    expect(poll.tracking.value).toBe(true)
    await flushPromises()
    expect(poll.job.value).toBeNull()
    expect(poll.tracking.value).toBe(false)
    expect(poll.error.value).toContain('任务身份不一致')
    await vi.advanceTimersByTimeAsync(10000)
    expect(getJob).toHaveBeenCalledTimes(1)
    poll.follow('current', 1)
    await flushPromises()
    expect(poll.job.value).toBeNull()
    poll.follow('current', 1)
    await flushPromises()
    expect(poll.job.value?.archiveId).toBe(1)
    expect(poll.tracking.value).toBe(false)
    scope.stop()
  })

  it('stops requesting when finished and discards an obsolete response', async () => {
    const { useOcrJob } = await import('../useOcrJob')
    vi.useFakeTimers()
    let resolveOld: (value: unknown) => void = () => {}
    getJob.mockReturnValueOnce(new Promise((resolve) => { resolveOld = resolve }))
    getJob.mockResolvedValue({ data: { id: 'new', status: 'SUCCESS' } })
    const scope = effectScope()
    const poll = scope.run(() => useOcrJob())!
    poll.follow('old')
    poll.follow('new')
    await flushPromises()
    resolveOld({ data: { id: 'old', status: 'RUNNING' } })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(10000)
    expect(poll.job.value?.id).toBe('new')
    expect(getJob).toHaveBeenCalledTimes(2)
    scope.stop()
  })
})
