import type { FrontendResponseMetric } from '@/api/types'
import { createResponseMetric, createResponseMetricQueue } from '../response-metrics'

describe('response metrics', () => {
  const baseInput = {
    requestId: 'req-123',
    endpointTemplate: '/api/v1/scan/{id}',
    method: 'get',
    status: 200,
    businessCode: 200,
    startedAt: 100,
    now: 125.678,
    serverDurationMs: 10.25,
    occurredAt: '2026-07-13T00:00:00.000Z',
  }

  it('creates a successful metric from whitelisted response metadata', () => {
    expect(createResponseMetric(baseInput)).toEqual({
      requestId: 'req-123',
      routePattern: '/api/v1/scan/{id}',
      method: 'GET',
      httpStatus: 200,
      businessCode: 200,
      success: true,
      clientDurationMs: 26,
      serverDurationMs: 10,
      occurredAt: '2026-07-13T00:00:00.000Z',
      retryCount: 0,
      retryOutcome: undefined,
    })
  })

  it('records the final retry outcome without copying request data', () => {
    const metric = createResponseMetric({
      ...baseInput,
      retryCount: 2,
      retryOutcome: 'succeeded',
      url: '/api/v1/scan/secret-patient-id?token=secret',
      query: { bah: 'secret-bah' },
      body: { idCard: 'secret-id-card' },
      headers: { Authorization: 'Bearer secret' },
      token: 'secret',
    } as typeof baseInput)

    expect(metric?.retryCount).toBe(2)
    expect(metric?.retryOutcome).toBe('succeeded')
    expect(Object.keys(metric ?? {}).sort()).toEqual([
      'businessCode',
      'clientDurationMs',
      'httpStatus',
      'method',
      'occurredAt',
      'requestId',
      'retryCount',
      'retryOutcome',
      'routePattern',
      'serverDurationMs',
      'success',
    ])
    expect(JSON.stringify(metric)).not.toContain('secret')
  })

  it('marks HTTP and business failures as unsuccessful', () => {
    expect(createResponseMetric({ ...baseInput, status: 500, businessCode: undefined })?.success).toBe(false)
    expect(createResponseMetric({ ...baseInput, businessCode: 400 })?.success).toBe(false)
  })

  it('skips metrics without a backend endpoint template', () => {
    expect(createResponseMetric({ ...baseInput, endpointTemplate: '' })).toBeNull()
  })

  it('flushes automatically at the configured batch size', async () => {
    const sender = vi.fn().mockResolvedValue(undefined)
    const queue = createResponseMetricQueue(sender, { installUnloadHandlers: false })
    const metric = createResponseMetric(baseInput) as FrontendResponseMetric

    for (let index = 0; index < 20; index += 1) queue.enqueue(metric)

    await vi.waitFor(() => expect(sender).toHaveBeenCalledOnce())
    expect(sender).toHaveBeenCalledWith(Array.from({ length: 20 }).fill(metric))
    expect(queue.getStats()).toMatchObject({ queued: 0, sent: 20, dropped: 0 })
    queue.dispose()
  })

  it('retries sender failures a finite number of times and records dropped metrics', async () => {
    vi.useFakeTimers()
    const sender = vi.fn().mockRejectedValue(new Error('offline'))
    const onDrop = vi.fn()
    const queue = createResponseMetricQueue(sender, {
      installUnloadHandlers: false,
      maxSendRetries: 2,
      retryDelayMs: 500,
      onDrop,
    })
    const metric = createResponseMetric(baseInput) as FrontendResponseMetric

    queue.enqueue(metric)
    await vi.advanceTimersByTimeAsync(6500)

    expect(sender).toHaveBeenCalledTimes(3)
    expect(queue.getStats()).toEqual({ queued: 0, sent: 0, dropped: 1, failedAttempts: 3 })
    expect(onDrop).toHaveBeenCalledWith({ count: 1, totalDropped: 1, reason: 'send-failed' })
    queue.dispose()
    vi.useRealTimers()
  })

  it('drops the oldest queued metric when the queue reaches capacity', async () => {
    let resolveFirstSend: (() => void) | undefined
    const firstSend = new Promise<void>((resolve) => {
      resolveFirstSend = resolve
    })
    const sender = vi.fn()
      .mockImplementationOnce(() => firstSend)
      .mockResolvedValue(undefined)
    const queue = createResponseMetricQueue(sender, {
      batchSize: 2,
      maxQueueSize: 3,
      installUnloadHandlers: false,
    })
    const metric = createResponseMetric(baseInput) as FrontendResponseMetric

    queue.enqueue(metric)
    queue.enqueue(metric)
    await vi.waitFor(() => expect(sender).toHaveBeenCalledOnce())

    for (let index = 0; index < 5; index += 1) queue.enqueue(metric)
    expect(queue.getStats()).toMatchObject({ queued: 3, dropped: 2 })

    resolveFirstSend?.()
    await vi.waitFor(() => expect(sender).toHaveBeenCalledTimes(3))
    expect(sender.mock.calls.every(([batch]) => batch.length <= 2)).toBe(true)
    queue.dispose()
  })

  it('uses the unload sender when the page closes', () => {
    const sender = vi.fn().mockResolvedValue(undefined)
    const unloadSender = vi.fn().mockReturnValue(true)
    const queue = createResponseMetricQueue(sender, {
      batchSize: 20,
      maxQueueSize: 20,
      unloadSender,
      installUnloadHandlers: false,
    })
    const metric = createResponseMetric(baseInput) as FrontendResponseMetric

    queue.enqueue(metric)
    queue.enqueue(metric)
    queue.enqueue(metric)
    queue.flushOnUnload()

    expect(sender).not.toHaveBeenCalled()
    expect(unloadSender).toHaveBeenCalledOnce()
    expect(unloadSender.mock.calls[0]?.[0]).toHaveLength(3)
    expect(queue.getStats()).toEqual({ queued: 0, sent: 3, dropped: 0, failedAttempts: 0 })
    queue.dispose()
  })

  it('records unload rejection as dropped metrics', () => {
    const queue = createResponseMetricQueue(vi.fn().mockResolvedValue(undefined), {
      unloadSender: () => false,
      installUnloadHandlers: false,
    })
    const metric = createResponseMetric(baseInput) as FrontendResponseMetric

    queue.enqueue(metric)
    queue.flushOnUnload()

    expect(queue.getStats()).toEqual({ queued: 0, sent: 0, dropped: 1, failedAttempts: 0 })
    queue.dispose()
  })
})
