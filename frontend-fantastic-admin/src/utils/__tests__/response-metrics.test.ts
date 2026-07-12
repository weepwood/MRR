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
    })
  })

  it('marks HTTP and business failures as unsuccessful', () => {
    expect(createResponseMetric({ ...baseInput, status: 500, businessCode: undefined })?.success).toBe(false)
    expect(createResponseMetric({ ...baseInput, businessCode: 400 })?.success).toBe(false)
  })

  it('never copies actual URL, query, body, headers, or token', () => {
    const metric = createResponseMetric({
      ...baseInput,
      url: '/api/v1/scan/secret-patient-id?token=secret',
      query: { bah: 'secret-bah' },
      body: { idCard: 'secret-id-card' },
      headers: { Authorization: 'Bearer secret' },
      token: 'secret',
    } as typeof baseInput)

    expect(Object.keys(metric ?? {}).sort()).toEqual([
      'businessCode',
      'clientDurationMs',
      'httpStatus',
      'method',
      'occurredAt',
      'requestId',
      'routePattern',
      'serverDurationMs',
      'success',
    ])
    expect(JSON.stringify(metric)).not.toContain('secret')
  })

  it('skips metrics without a backend endpoint template', () => {
    expect(createResponseMetric({ ...baseInput, endpointTemplate: '' })).toBeNull()
  })

  it('flushes automatically at 20 queued metrics', async () => {
    const sender = vi.fn().mockResolvedValue(undefined)
    const queue = createResponseMetricQueue(sender)
    const metric = createResponseMetric(baseInput) as FrontendResponseMetric

    for (let index = 0; index < 20; index += 1) {
      queue.enqueue(metric)
    }
    await vi.waitFor(() => expect(sender).toHaveBeenCalledOnce())
    expect(sender).toHaveBeenCalledWith(Array.from({ length: 20 }).fill(metric))
    queue.dispose()
  })

  it('flushes after five seconds and keeps sender failures silent', async () => {
    vi.useFakeTimers()
    const sender = vi.fn().mockRejectedValue(new Error('offline'))
    const queue = createResponseMetricQueue(sender)
    const metric = createResponseMetric(baseInput) as FrontendResponseMetric

    queue.enqueue(metric)
    await vi.advanceTimersByTimeAsync(5000)

    expect(sender).toHaveBeenCalledWith([metric])
    await expect(queue.flush()).resolves.toBeUndefined()
    queue.dispose()
    vi.useRealTimers()
  })

  it('keeps batches capped while a previous send is still pending', async () => {
    let resolveFirstSend: (() => void) | undefined
    const firstSend = new Promise<void>((resolve) => {
      resolveFirstSend = resolve
    })
    const sender = vi.fn()
      .mockImplementationOnce(() => firstSend)
      .mockResolvedValue(undefined)
    const queue = createResponseMetricQueue(sender)
    const metric = createResponseMetric(baseInput) as FrontendResponseMetric

    for (let index = 0; index < 20; index += 1) {
      queue.enqueue(metric)
    }
    await vi.waitFor(() => expect(sender).toHaveBeenCalledOnce())

    for (let index = 0; index < 205; index += 1) {
      queue.enqueue(metric)
    }
    resolveFirstSend?.()

    await vi.waitFor(() => expect(sender).toHaveBeenCalledTimes(11))
    expect(sender.mock.calls.every(([batch]) => batch.length <= 20)).toBe(true)
    queue.dispose()
  })
})
