import type { AxiosError, AxiosRequestConfig, AxiosResponse } from 'axios'
import {
  getRequestRetryDecision,
  parseRetryAfterMs,
  waitForRetryDelay,
} from '../request-retry'

function createAxiosError(options: {
  method?: string
  status?: number
  code?: string
  retryCount?: number
  maxRetryCount?: number
  idempotencyKey?: string
  retryAfter?: string
  aborted?: boolean
} = {}): AxiosError<unknown> {
  const config = {
    method: options.method ?? 'get',
    retry: true,
    retryCount: options.retryCount,
    maxRetryCount: options.maxRetryCount,
    idempotencyKey: options.idempotencyKey,
    signal: options.aborted ? AbortSignal.abort() : undefined,
    headers: {},
  } as AxiosRequestConfig

  return {
    name: 'AxiosError',
    message: options.code === 'ECONNABORTED' ? 'timeout' : 'request failed',
    code: options.code,
    config,
    response: options.status === undefined
      ? undefined
      : {
          status: options.status,
          headers: options.retryAfter ? { 'retry-after': options.retryAfter } : {},
        } as AxiosResponse,
    isAxiosError: true,
    toJSON: () => ({}),
  } as AxiosError<unknown>
}

describe('request retry policy', () => {
  it('retries idempotent reads for network failures', () => {
    const decision = getRequestRetryDecision(createAxiosError({ method: 'get', code: 'ERR_NETWORK' }), () => 0.5)

    expect(decision).toEqual({
      shouldRetry: true,
      attempt: 1,
      delayMs: 500,
      reason: 'ERR_NETWORK',
    })
  })

  it.each(['post', 'put', 'patch', 'delete'])('does not retry %s without an idempotency key', (method) => {
    expect(getRequestRetryDecision(createAxiosError({ method, code: 'ERR_NETWORK' })).shouldRetry).toBe(false)
  })

  it('allows an explicitly idempotent write request to retry', () => {
    const decision = getRequestRetryDecision(createAxiosError({
      method: 'post',
      status: 503,
      idempotencyKey: 'archive-export-123',
    }), () => 0.5)

    expect(decision.shouldRetry).toBe(true)
    expect(decision.reason).toBe('http-503')
  })

  it.each([408, 429, 502, 503, 504])('retries GET for recoverable HTTP status %s', (status) => {
    expect(getRequestRetryDecision(createAxiosError({ status })).shouldRetry).toBe(true)
  })

  it.each([400, 401, 403, 404, 409, 500])('does not retry GET for non-recoverable HTTP status %s', (status) => {
    expect(getRequestRetryDecision(createAxiosError({ status })).shouldRetry).toBe(false)
  })

  it('retries a GET timeout but stops at the configured limit', () => {
    expect(getRequestRetryDecision(createAxiosError({ code: 'ECONNABORTED' })).shouldRetry).toBe(true)
    expect(getRequestRetryDecision(createAxiosError({ code: 'ECONNABORTED', retryCount: 2, maxRetryCount: 2 }))).toEqual({
      shouldRetry: false,
      attempt: 2,
      delayMs: 0,
      reason: 'retry-limit',
    })
  })

  it('prefers Retry-After seconds for 429 and 503', () => {
    const decision = getRequestRetryDecision(createAxiosError({ status: 429, retryAfter: '2.5' }), () => 0)
    expect(decision.delayMs).toBe(2500)
  })

  it('parses Retry-After HTTP dates and caps excessive delays', () => {
    const now = Date.parse('2026-07-20T10:00:00.000Z')
    expect(parseRetryAfterMs('Sun, 20 Jul 2026 10:00:05 GMT', now)).toBe(5000)
    expect(parseRetryAfterMs('3600', now)).toBe(60_000)
  })

  it('does not retry canceled requests', () => {
    expect(getRequestRetryDecision(createAxiosError({ code: 'ERR_CANCELED' })).shouldRetry).toBe(false)
    expect(getRequestRetryDecision(createAxiosError({ aborted: true })).shouldRetry).toBe(false)
  })

  it('stops a pending retry delay when its signal is aborted', async () => {
    vi.useFakeTimers()
    const controller = new AbortController()
    const waiting = waitForRetryDelay(10_000, controller.signal)

    controller.abort()
    await expect(waiting).resolves.toBe(false)
    vi.useRealTimers()
  })
})
