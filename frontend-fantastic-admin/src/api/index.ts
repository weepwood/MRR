import type { AxiosError, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getRequestErrorMessage } from '@/utils/request-error-message'
import { registerRequestErrorFallback } from '@/utils/request-error-notification'
import { createResponseMetric, createResponseMetricQueue } from '@/utils/response-metrics'

declare module 'axios' {
  export interface AxiosRequestConfig {
    retry?: boolean
    retryCount?: number
    skipGlobalError?: boolean
    skipResponseMetrics?: boolean
    metricStartedAt?: number
  }
}

const MAX_RETRY_COUNT = 3
const RETRY_DELAY = 1000
const ERROR_TOAST_DEDUPE_MS = 2000
let isLoggingOut = false
let isRedirectingToPasswordChange = false
const recentErrorToasts = new Map<string, number>()

const api = axios.create({
  baseURL: import.meta.env.DEV ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL,
  timeout: 1000 * 60,
  responseType: 'json',
})

const responseMetricQueue = createResponseMetricQueue(async (metrics) => {
  const { reportFrontendResponseMetrics } = await import('./modules/response-metrics')
  await reportFrontendResponseMetrics(metrics)
})

function parseServerDuration(response: AxiosResponse) {
  const explicitDuration = Number(
    response.headers['x-server-duration-ms'] ?? response.headers['x-response-time-ms'],
  )
  if (Number.isFinite(explicitDuration)) {
    return explicitDuration
  }
  const serverTiming = String(response.headers['server-timing'] ?? '')
  const durationMatch = serverTiming.match(/(?:^|,)\s*app;dur=([\d.]+)/i)
  return durationMatch ? Number(durationMatch[1]) : undefined
}

function extractBusinessCode(payload: unknown) {
  if (!payload || typeof payload !== 'object') return undefined
  if ('code' in payload && typeof payload.code === 'number') return payload.code
  if ('status' in payload && typeof payload.status === 'number') return payload.status
  return undefined
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
    businessCode: extractBusinessCode(payload),
    startedAt: config.metricStartedAt,
    serverDurationMs: parseServerDuration(response),
  })
  if (metric) responseMetricQueue.enqueue(metric)
}

function normalizeRequestError(error: AxiosError | any) {
  if (error && typeof error === 'object') {
    error.message = getRequestErrorMessage(error)
  }
  return error
}

function showGlobalError(error: AxiosError | any) {
  const message = getRequestErrorMessage(error)
  const key = `${error?.response?.status ?? 'network'}:${message}`
  registerRequestErrorFallback(error, () => {
    const now = Date.now()
    const lastShownAt = recentErrorToasts.get(key) ?? 0
    if (now - lastShownAt < ERROR_TOAST_DEDUPE_MS) return
    recentErrorToasts.set(key, now)
    ElMessage.error({ message, grouping: true, showClose: true })
  })
}

function redirectToRequiredPasswordChange() {
  if (isRedirectingToPasswordChange) return
  isRedirectingToPasswordChange = true

  const userStore = useUserStore()
  const nextProfile = { ...userStore.profile, mustChangePassword: true }
  userStore.profile.mustChangePassword = true
  localStorage.setItem('profile', JSON.stringify(nextProfile))

  if (window.location.pathname !== '/password/change-required') {
    window.location.assign('/password/change-required')
  }
  else {
    isRedirectingToPasswordChange = false
  }
}

async function handleError(error: AxiosError | any) {
  normalizeRequestError(error)
  const config = error?.config
  const responseCode = error?.response?.data?.code

  if (error?.response?.status === 428 || responseCode === 'AUTH_PASSWORD_CHANGE_REQUIRED') {
    enqueueResponseMetric(config, error.response, error.response.data)
    redirectToRequiredPasswordChange()
    return Promise.reject(error)
  }

  if (error?.response?.status === 401) {
    enqueueResponseMetric(config, error.response, error.response.data)
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

  if (config?.retry) {
    config.retryCount = config.retryCount || 0
    if (config.retryCount < MAX_RETRY_COUNT) {
      config.retryCount += 1
      await new Promise(resolve => setTimeout(resolve, RETRY_DELAY))
      return api(config)
    }
  }

  enqueueResponseMetric(config, error?.response, error?.response?.data)
  if (!config?.skipGlobalError) showGlobalError(error)
  return Promise.reject(error)
}

api.interceptors.request.use((request) => {
  if (request.metricStartedAt === undefined) request.metricStartedAt = performance.now()
  const userStore = useUserStore()
  if (request.headers && userStore.isLogin) {
    request.headers.Authorization = `Bearer ${userStore.token}`
  }
  return request
})

api.interceptors.response.use(
  (response) => {
    const payload = response.data
    enqueueResponseMetric(response.config, response, payload)

    if (payload && typeof payload === 'object') {
      if ('status' in payload && !('code' in payload)) {
        const statusValue = payload.status
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

      if ('code' in payload) {
        if (payload.code === 200) return payload
        if (payload.code === 'AUTH_PASSWORD_CHANGE_REQUIRED') {
          redirectToRequiredPasswordChange()
        }
        return Promise.reject(normalizeRequestError(payload))
      }
    }

    return payload
  },
  handleError,
)

export default api

export function getRequest<T = any>(url: string, config?: import('axios').AxiosRequestConfig): Promise<import('./types').ApiResult<T>> {
  return api.get(url, config) as Promise<import('./types').ApiResult<T>>
}

export function postRequest<T = any, D = any>(url: string, data?: D, config?: import('axios').AxiosRequestConfig): Promise<import('./types').ApiResult<T>> {
  return api.post(url, data, config) as Promise<import('./types').ApiResult<T>>
}

export function putRequest<T = any, D = any>(url: string, data?: D, config?: import('axios').AxiosRequestConfig): Promise<import('./types').ApiResult<T>> {
  return api.put(url, data, config) as Promise<import('./types').ApiResult<T>>
}

export function deleteRequest<T = any>(url: string, config?: import('axios').AxiosRequestConfig): Promise<import('./types').ApiResult<T>> {
  return api.delete(url, config) as Promise<import('./types').ApiResult<T>>
}
