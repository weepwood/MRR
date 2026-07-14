import type { AxiosError, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { registerUnhandledRequestError } from '@/utils/request-error-notification'
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

// 401 登出防抖标志：并发多个 401 时只触发一次登出，避免多次 router.push('login')
let isLoggingOut = false
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
  if (!payload || typeof payload !== 'object') {
    return undefined
  }
  if ('code' in payload && typeof payload.code === 'number') {
    return payload.code
  }
  if ('status' in payload && typeof payload.status === 'number') {
    return payload.status
  }
  return undefined
}

function enqueueResponseMetric(
  config: InternalAxiosRequestConfig | undefined,
  response: AxiosResponse | undefined,
  payload: unknown,
) {
  if (!config || config.skipResponseMetrics || !response) {
    return
  }

  const metric = createResponseMetric({
    requestId: String(response.headers['x-request-id'] ?? ''),
    endpointTemplate: String(response.headers['x-endpoint-template'] ?? ''),
    method: config.method,
    status: response.status,
    businessCode: extractBusinessCode(payload),
    startedAt: config.metricStartedAt,
    serverDurationMs: parseServerDuration(response),
  })
  if (metric) {
    responseMetricQueue.enqueue(metric)
  }
}

function getErrorMessage(error: AxiosError | any) {
  if (error?.response?.data?.message) {
    return String(error.response.data.message)
  }

  const message = String(error?.message || '')
  if (message === 'Network Error') {
    return '后端网络异常'
  }
  if (message.includes('timeout')) {
    return '接口请求超时'
  }
  if (message.includes('Request failed with status code')) {
    return `接口${message.slice(-3)}异常`
  }
  return message || '请求失败'
}

function showGlobalError(error: AxiosError | any) {
  const message = getErrorMessage(error)
  const key = `${error?.response?.status ?? 'network'}:${message}`
  const now = Date.now()
  const lastShownAt = recentErrorToasts.get(key) ?? 0

  if (now - lastShownAt < ERROR_TOAST_DEDUPE_MS) {
    return
  }

  recentErrorToasts.set(key, now)
  registerUnhandledRequestError(error, () => {
    ElMessage.error({
      message,
      grouping: true,
      showClose: true,
    })
  })
}

async function handleError(error: AxiosError | any) {
  const config = error?.config
  if (error?.response?.status === 401) {
    enqueueResponseMetric(config, error.response, error.response.data)
    // 防抖：并发 401 时只触发一次登出
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

  if (!config?.skipGlobalError) {
    showGlobalError(error)
  }

  return Promise.reject(error)
}

api.interceptors.request.use((request) => {
  if (request.metricStartedAt === undefined) {
    request.metricStartedAt = performance.now()
  }
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
        if (statusValue === 1) {
          return payload
        }
        if (statusValue === 0) {
          // 防抖：并发请求收到 status=0 时只触发一次登出
          if (!isLoggingOut) {
            isLoggingOut = true
            void Promise.resolve(useUserStore().requestLogout())
              .finally(() => { isLoggingOut = false })
          }
        }
        if (typeof statusValue === 'string') {
          const upperStatus = statusValue.toUpperCase()
          if (['UP', 'DOWN', 'WARNING', 'UNKNOWN'].includes(upperStatus)) {
            return payload
          }
        }
        return Promise.reject(payload)
      }

      if ('code' in payload) {
        if (payload.code === 200) {
          return payload
        }
        return Promise.reject(payload)
      }
    }

    return payload
  },
  handleError,
)

export default api

/**
 * 类型安全的 GET 请求。
 * 已对齐响应拦截器行为，返回 Promise<ApiResult<T>> 而非 AxiosResponse。
 */
export function getRequest<T = any>(url: string, config?: import('axios').AxiosRequestConfig): Promise<import('./types').ApiResult<T>> {
  return api.get(url, config) as Promise<import('./types').ApiResult<T>>
}

/**
 * 类型安全的 POST 请求。
 */
export function postRequest<T = any, D = any>(url: string, data?: D, config?: import('axios').AxiosRequestConfig): Promise<import('./types').ApiResult<T>> {
  return api.post(url, data, config) as Promise<import('./types').ApiResult<T>>
}

/**
 * 类型安全的 PUT 请求。
 */
export function putRequest<T = any, D = any>(url: string, data?: D, config?: import('axios').AxiosRequestConfig): Promise<import('./types').ApiResult<T>> {
  return api.put(url, data, config) as Promise<import('./types').ApiResult<T>>
}

/**
 * 类型安全的 DELETE 请求。
 */
export function deleteRequest<T = any>(url: string, config?: import('axios').AxiosRequestConfig): Promise<import('./types').ApiResult<T>> {
  return api.delete(url, config) as Promise<import('./types').ApiResult<T>>
}
