import { flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'

const getJob = vi.hoisted(() => vi.fn())
vi.mock('@/api/modules/ocr', () => ({ getOcrJob: getJob }))

describe('oCR task polling', () => {
  afterEach(() => { vi.useRealTimers(); vi.clearAllMocks() })

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
