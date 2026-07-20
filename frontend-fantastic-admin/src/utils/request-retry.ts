import type { AxiosError, AxiosRequestConfig } from 'axios'
import axios from 'axios'

declare module 'axios' {
  export interface AxiosRequestConfig {
    retry?: boolean
    retryCount?: number
    maxRetryCount?: number
    idempotencyKey?: string
    metricRetryCount?: number
    metricRetryOutcome?: 'succeeded' | 'failed' | 'canceled'
  }
}

const IDEMPOTENT_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])
const RETRYABLE_STATUS_CODES = new Set([408, 429, 502, 503, 504])
const DEFAULT_MAX_RETRY_COUNT = 3
const ABSOLUTE_MAX_RETRY_COUNT = 5
const BASE_RETRY_DELAY_MS = 500
const MAX_BACKOFF_DELAY_MS = 10_000
const MAX_RETRY_AFTER_MS = 60_000

export interface RequestRetryDecision {
  shouldRetry: boolean
  attempt: number
  delayMs: number
  reason?: string
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function clampRetryCount(value: unknown): number {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return DEFAULT_MAX_RETRY_COUNT
  return Math.min(ABSOLUTE_MAX_RETRY_COUNT, Math.max(0, Math.trunc(parsed)))
}

function normalizeMethod(config: AxiosRequestConfig | undefined): string {
  return String(config?.method ?? 'GET').toUpperCase()
}

function hasIdempotencyKey(config: AxiosRequestConfig | undefined): boolean {
  return typeof config?.idempotencyKey === 'string' && config.idempotencyKey.trim().length > 0
}

function isRetryableMethod(config: AxiosRequestConfig | undefined): boolean {
  return IDEMPOTENT_METHODS.has(normalizeMethod(config)) || hasIdempotencyKey(config)
}

function getHeaderValue(headers: unknown, name: string): string | undefined {
  if (!isRecord(headers)) return undefined

  const getter = headers.get
  if (typeof getter === 'function') {
    const value = getter.call(headers, name)
    return value == null ? undefined : String(value)
  }

  const lowerName = name.toLowerCase()
  const value = headers[lowerName] ?? headers[name]
  return value == null ? undefined : String(value)
}

export function parseRetryAfterMs(value: unknown, now = Date.now()): number | undefined {
  if (typeof value !== 'string' && typeof value !== 'number') return undefined
  const text = String(value).trim()
  if (!text) return undefined

  if (/^\d+(?:\.\d+)?$/.test(text)) {
    return Math.min(MAX_RETRY_AFTER_MS, Math.max(0, Math.round(Number(text) * 1000)))
  }

  const retryAt = Date.parse(text)
  if (!Number.isFinite(retryAt)) return undefined
  return Math.min(MAX_RETRY_AFTER_MS, Math.max(0, retryAt - now))
}

function calculateBackoffMs(attempt: number, random: () => number): number {
  const exponential = Math.min(MAX_BACKOFF_DELAY_MS, BASE_RETRY_DELAY_MS * 2 ** Math.max(0, attempt - 1))
  const randomValue = Math.min(1, Math.max(0, random()))
  return Math.round(exponential * (0.5 + randomValue))
}

function calculateRetryDelayMs(
  error: AxiosError<unknown>,
  attempt: number,
  random: () => number,
  now: number,
): number {
  const status = error.response?.status
  if (status === 429 || status === 503) {
    const retryAfter = getHeaderValue(error.response?.headers, 'retry-after')
    const retryAfterMs = parseRetryAfterMs(retryAfter, now)
    if (retryAfterMs !== undefined) return retryAfterMs
  }
  return calculateBackoffMs(attempt, random)
}

export function isRequestCanceled(error: unknown): boolean {
  return axios.isCancel(error)
    || (axios.isAxiosError(error)
      && (error.code === 'ERR_CANCELED' || error.config?.signal?.aborted === true))
}

export function getRequestRetryDecision(
  error: unknown,
  random: () => number = Math.random,
  now = Date.now(),
): RequestRetryDecision {
  if (!axios.isAxiosError<unknown>(error)) {
    return { shouldRetry: false, attempt: 0, delayMs: 0 }
  }

  const config = error.config
  if (!config?.retry || isRequestCanceled(error) || !isRetryableMethod(config)) {
    return { shouldRetry: false, attempt: 0, delayMs: 0 }
  }

  const retryCount = Math.max(0, Math.trunc(Number(config.retryCount) || 0))
  const maxRetryCount = clampRetryCount(config.maxRetryCount)
  if (retryCount >= maxRetryCount) {
    return { shouldRetry: false, attempt: retryCount, delayMs: 0, reason: 'retry-limit' }
  }

  const status = error.response?.status
  const isRetryableFailure = status === undefined || RETRYABLE_STATUS_CODES.has(status)
  if (!isRetryableFailure) {
    return { shouldRetry: false, attempt: retryCount, delayMs: 0, reason: `http-${status}` }
  }

  const attempt = retryCount + 1
  return {
    shouldRetry: true,
    attempt,
    delayMs: calculateRetryDelayMs(error, attempt, random, now),
    reason: status === undefined ? (error.code || 'network') : `http-${status}`,
  }
}

export function waitForRetryDelay(delayMs: number, signal?: AbortSignal): Promise<boolean> {
  if (signal?.aborted) return Promise.resolve(false)

  return new Promise((resolve) => {
    let settled = false
    const finish = (canRetry: boolean) => {
      if (settled) return
      settled = true
      signal?.removeEventListener('abort', handleAbort)
      resolve(canRetry)
    }
    const handleAbort = () => {
      clearTimeout(timer)
      finish(false)
    }
    const timer = setTimeout(() => finish(true), Math.max(0, delayMs))
    signal?.addEventListener('abort', handleAbort, { once: true })
  })
}
