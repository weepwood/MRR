import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { createErrorDedupeCache } from '@/utils/request-error-dedupe'
import { getRequestErrorMessage } from '@/utils/request-error-message'
import { registerRequestErrorFallback } from '@/utils/request-error-notification'
import {
  getRequestRetryDecision,
  isRequestCanceled,
  waitForRetryDelay,
} from '@/utils/request-retry'
import {
  createResponseMetric,
  createResponseMetricQueue,
  type QueuedResponseMetric,
  type RetryOutcome,
} from '@/utils/response-metrics'

type BusinessCode = number | string

interface BusinessErrorPayload {
  code?: BusinessCode
  status?: BusinessCode
  message?: string
  msg?: string
}

class BusinessRequestError extends Error {
  readonly payload: unknown
  readonly code?: BusinessCode
  readonly status?: BusinessCode

  constructor(payload: unknown) {
    super(getRequestErrorMessage(payload))
    this.name = 'BusinessRequestError'
    this.payload = payload
    const businessPayload = asBusinessPayload(payload)
    this.code = businessPayload?.code
    this.status = businessPayload?.status
  }
}

const ERROR_TOAST_TTL_MS = 2000
const ERROR_TOAST_MAX_ENTRIES = 100
const METRIC_BATCH_PATH = '/api/v1/response-metrics/frontend/batch'
let isLoggingOut = false
let isRedirectingToPasswordChange = false

const errorToastDedupe = createErrorDedupeCache({
  ttlMs: ERROR_TOAST_TTL_MS,
  maxEntries: ERROR_TOAST_MAX_ENTRIES,
  cleanupIntervalMs: 5000,
})

const api = axios.create({
  baseURL: import.meta.env.DEV ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL,
  timeout: 1000 * 60,
  responseType: 'json',
})

function resolveApiUrl(path: string): string {
  if (typeof window === 'undefined') return path
  const rawBase = String(api.defaults.baseURL || '/')
  const base = rawBase.endsWith('/') ? rawBase : `${rawBase}/`
  return new URL(path.replace(/^\/+/, ''), new URL(base, window.location.origin)).toString()
}

function sendResponseMetricsOnUnload(metrics: QueuedResponseMetric[]): boolean {
  if (typeof window === 'undefined' || typeof navigator === 'undefined') return false

  const body = JSON.stringify({ metrics })
  const token = useUserStore().token
  const url = resolveApiUrl(METRIC_BATCH_PATH)

  if (!token && typeof navigator.sendBeacon === 'function') {
    return navigator.sendBeacon(url, new Blob([body], { type: 'application/json' }))
  }

  try {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (token) headers.Authorization = `Bearer ${token}`
    void fetch(url, {
      method: 'POST',
      body,
      headers,
      credentials: 'same-origin',
      keepalive: true,
    }).catch(() => undefined)
    return true
  }
  catch {
    return false
  }
}

const responseMetricQueue = createResponseMetricQueue(async (metrics) => {
  const { reportFrontendResponseMetrics } = await import('./modules/response-metrics')
  await reportFrontendResponseMetrics(metrics)
}, {
  batchSize: 20,
  maxQueueSize: 200,
  maxSendRetries: 2,
  unloadSender: sendResponseMetricsOnUnload,
  onDrop: event => console.warn('[Response metrics dropped]', event),
})

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function asBusinessPayload(value: unknown): BusinessErrorPayload | undefined {
  if (!isRecord(value)) return undefined
  const payload: BusinessErrorPayload = {}
  if (typeof value.code === 'number' || typeof value.code === 'string') payload.code = value.code
  if (typeof value.status === 'number' || typeof value.status === 'string') payload.status = value.status
  if (typeof value.message === 'string') payload.message = value.message
  if (typeof value.msg === 'string') payload.msg = value.msg
  return payload
}

function extractBusinessCode(payload: unknown): BusinessCode | undefined {
  const businessPayload = asBusinessPayload(payload)
  return businessPayload?.code ?? businessPayload?.status
}

function extractMetricBusinessCode(payload: unknown): number | undefined {
  const code = extractBusinessCode(payload)
  return typeof code === 'number' ? code : undefined
}

function parseServerDuration(response: AxiosResponse) {
  const explicitDuration = Number(
    response.headers['x-server-duration-ms'] ?? response.headers['x-response-time-ms'],
  )
  if (Number.isFinite(explicitDuration)) return explicitDuration

  const serverTiming = String(response.headers['server-timing'] ?? '')
  const durationMatch = serverTiming.match(/(?:^|,)\s*app;dur=([\d.]+)/i)
  return durationMatch ? Number(durationMatch[1]) : undefined
}

function enqueueResponseMetric(
  config: InternalAxiosRequestConfig | undefined,
  response: AxiosResponse | undefined,
  payload: unknown,
) {
  if (!config || config.skipResponseMetrics || !response) return
  const metric = createResponseMetric({
    requestId: String(response.headers['x-request-id'] ?? ''),
    endpointTemplate: String(response.headers['x-endpoint-template'] ?? ''),
    method: config.method,
    status: response.status,
    businessCode: extractMetricBusinessCode(payload),
    startedAt: config.metricStartedAt,
    serverDurationMs: parseServerDuration(response),
    retryCount: config.metricRetryCount,
    retryOutcome: config.metricRetryOutcome,
  })
  if (metric) responseMetricQueue.enqueue(metric)
}

function normalizeRequestError(error: unknown): Error {
  return error instanceof Error ? error : new BusinessRequestError(error)
}

function getRequestId(error: unknown): string | undefined {
  if (!axios.isAxiosError(error)) return undefined
  const value = error.response?.headers?.['x-request-id']
  return value == null ? undefined : String(value)
}

function getErrorDedupeKey(error: unknown): string {
  if (axios.isAxiosError<unknown>(error)) {
    const status = error.response?.status ?? 'network'
    const code = extractBusinessCode(error.response?.data) ?? error.code ?? 'request-failed'
    return `${status}:${String(code).slice(0, 64)}`
  }

  const payload = error instanceof BusinessRequestError ? error.payload : error
  const code = extractBusinessCode(payload) ?? 'business-error'
  return `business:${String(code).slice(0, 64)}`
}

function showGlobalError(error: Error) {
  const message = getRequestErrorMessage(error instanceof BusinessRequestError ? error.payload : error)
  const key = getErrorDedupeKey(error instanceof BusinessRequestError ? error.payload : error)
  const requestId = getRequestId(error)

  registerRequestErrorFallback(error, () => {
    const decision = errorToastDedupe.check(key, requestId)
    if (!decision.shouldNotify) return
    const displayMessage = decision.firstRequestId
      ? `${message}（请求 ID：${decision.firstRequestId}）`
      : message
    ElMessage.error({ message: displayMessage, grouping: true, showClose: true })
  })
}

function redirectToRequiredPasswordChange() {
  if (isRedirectingToPasswordChange) return
  isRedirectingToPasswordChange = true

  useUserStore().markPasswordChangeRequired()

  if (window.location.pathname !== '/password/change-required') {
    window.location.assign('/password/change-required')
  }
  else {
    isRedirectingToPasswordChange = false
  }
}

function recordFinalRetry(config: InternalAxiosRequestConfig | undefined, outcome: RetryOutcome) {
  if (!config?.metricRetryCount) return
  config.metricRetryOutcome = outcome
  console.info('[HTTP retry completed]', {
    method: String(config.method ?? 'GET').toUpperCase(),
    retryCount: config.metricRetryCount,
    outcome,
  })
}

async function handleError(error: unknown) {
  const axiosError = axios.isAxiosError<unknown>(error) ? error : undefined
  const config = axiosError?.config
  const responseCode = extractBusinessCode(axiosError?.response?.data)

  if (axiosError?.response?.status === 428 || responseCode === 'AUTH_PASSWORD_CHANGE_REQUIRED') {
    recordFinalRetry(config, 'failed')
    enqueueResponseMetric(config, axiosError.response, axiosError.response.data)
    redirectToRequiredPasswordChange()
    return Promise.reject(error)
  }

  if (axiosError?.response?.status === 401) {
    recordFinalRetry(config, 'failed')
    enqueueResponseMetric(config, axiosError.response, axiosError.response.data)
    if (!isLoggingOut) {
      isLoggingOut = true
      try {
        await useUserStore().requestLogout()
      }
      finally {
        isLoggingOut = false
      }
    }
    return Promise.reject(error)
  }

  const retryDecision = getRequestRetryDecision(error)
  if (retryDecision.shouldRetry && config) {
    config.retryCount = retryDecision.attempt
    config.metricRetryCount = retryDecision.attempt
    console.info('[HTTP retry scheduled]', {
      method: String(config.method ?? 'GET').toUpperCase(),
      attempt: retryDecision.attempt,
      delayMs: retryDecision.delayMs,
      reason: retryDecision.reason,
    })

    const canRetry = await waitForRetryDelay(retryDecision.delayMs, config.signal)
    if (canRetry) return api(config)

    recordFinalRetry(config, 'canceled')
    enqueueResponseMetric(config, axiosError?.response, axiosError?.response?.data)
    return Promise.reject(error)
  }

  recordFinalRetry(config, isRequestCanceled(error) ? 'canceled' : 'failed')
  enqueueResponseMetric(config, axiosError?.response, axiosError?.response?.data)

  const normalizedError = normalizeRequestError(error)
  if (!config?.skipGlobalError) showGlobalError(normalizedError)
  return Promise.reject(normalizedError)
}

api.interceptors.request.use((request) => {
  if (request.metricStartedAt === undefined) request.metricStartedAt = performance.now()
  if (request.idempotencyKey) request.headers.set('Idempotency-Key', request.idempotencyKey)

  const userStore = useUserStore()
  if (userStore.isLogin) request.headers.set('Authorization', `Bearer ${userStore.token}`)
  return request
})

api.interceptors.response.use(
  (response) => {
    const payload: unknown = response.data
    recordFinalRetry(response.config, 'succeeded')
    enqueueResponseMetric(response.config, response, payload)

    const businessPayload = asBusinessPayload(payload)
    if (businessPayload) {
      if (businessPayload.status !== undefined && businessPayload.code === undefined) {
        const statusValue = businessPayload.status
        if (statusValue === 1) return payload
        if (statusValue === 0 && !isLoggingOut) {
          isLoggingOut = true
          void Promise.resolve(useUserStore().requestLogout())
            .finally(() => { isLoggingOut = false })
        }
        if (typeof statusValue === 'string') {
          const upperStatus = statusValue.toUpperCase()
          if (['UP', 'DOWN', 'WARNING', 'UNKNOWN'].includes(upperStatus)) return payload
        }
        return Promise.reject(normalizeRequestError(payload))
      }

      if (businessPayload.code !== undefined) {
        if (typeof businessPayload.code === 'number' && businessPayload.code >= 200 && businessPayload.code < 300) {
          return payload
        }
        if (businessPayload.code === 'AUTH_PASSWORD_CHANGE_REQUIRED') redirectToRequiredPasswordChange()
        return Promise.reject(normalizeRequestError(payload))
      }
    }

    return payload
  },
  handleError,
)

export default api

export function getRequest<T = unknown>(url: string, config?: import('axios').AxiosRequestConfig): Promise<import('./types').ApiResult<T>> {
  return api.get(url, config) as Promise<import('./types').ApiResult<T>>
}

export function postRequest<T = unknown, D = unknown>(url: string, data?: D, config?: import('axios').AxiosRequestConfig): Promise<import('./types').ApiResult<T>> {
  return api.post(url, data, config) as Promise<import('./types').ApiResult<T>>
}

export function putRequest<T = unknown, D = unknown>(url: string, data?: D, config?: import('axios').AxiosRequestConfig): Promise<import('./types').ApiResult<T>> {
  return api.put(url, data, config) as Promise<import('./types').ApiResult<T>>
}

export function deleteRequest<T = unknown>(url: string, config?: import('axios').AxiosRequestConfig): Promise<import('./types').ApiResult<T>> {
  return api.delete(url, config) as Promise<import('./types').ApiResult<T>>
}
