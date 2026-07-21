import { createErrorDedupeCache } from '../request-error-dedupe'

describe('request error dedupe cache', () => {
  it('suppresses duplicate errors within the TTL and preserves the first request ID', () => {
    let now = 1000
    const cache = createErrorDedupeCache({
      ttlMs: 2000,
      maxEntries: 10,
      cleanupIntervalMs: 60_000,
      now: () => now,
    })

    expect(cache.check('503:SERVICE_UNAVAILABLE', 'req-first')).toEqual({
      shouldNotify: true,
      firstRequestId: 'req-first',
    })

    now += 1000
    expect(cache.check('503:SERVICE_UNAVAILABLE', 'req-second')).toEqual({
      shouldNotify: false,
      firstRequestId: 'req-first',
    })

    now += 1000
    expect(cache.check('503:SERVICE_UNAVAILABLE', 'req-third')).toEqual({
      shouldNotify: true,
      firstRequestId: 'req-third',
    })
    cache.dispose()
  })

  it('evicts the oldest error when capacity is reached', () => {
    let now = 1000
    const cache = createErrorDedupeCache({
      ttlMs: 60_000,
      maxEntries: 2,
      cleanupIntervalMs: 60_000,
      now: () => now,
    })

    cache.check('500:A', 'req-a')
    now += 1
    cache.check('500:B', 'req-b')
    now += 1
    cache.check('500:C', 'req-c')

    expect(cache.size()).toBe(2)
    expect(cache.check('500:A', 'req-a2').shouldNotify).toBe(true)
    cache.dispose()
  })

  it('sanitizes and bounds request IDs stored for diagnostics', () => {
    const cache = createErrorDedupeCache({ cleanupIntervalMs: 60_000 })
    const decision = cache.check('network:ERR_NETWORK', ` request id / patient? ${'x'.repeat(100)}`)

    expect(decision.firstRequestId).not.toContain(' ')
    expect(decision.firstRequestId).not.toContain('/')
    expect(decision.firstRequestId?.length).toBeLessThanOrEqual(64)
    cache.dispose()
  })
})
