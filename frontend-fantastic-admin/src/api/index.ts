import type { AxiosError } from 'axios'
import axios from 'axios'
import { toast } from 'vue-sonner'

declare module 'axios' {
  export interface AxiosRequestConfig {
    retry?: boolean
    retryCount?: number
    skipGlobalError?: boolean
  }
}

const MAX_RETRY_COUNT = 3
const RETRY_DELAY = 1000

const api = axios.create({
  baseURL: import.meta.env.DEV ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL,
  timeout: 1000 * 60,
  responseType: 'json',
})

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

async function handleError(error: AxiosError | any) {
  const config = error?.config
  if (error?.response?.status === 401) {
    useUserStore().requestLogout()
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

  if (!config?.skipGlobalError) {
    toast.error('Error', {
      description: getErrorMessage(error),
    })
  }

  return Promise.reject(error)
}

api.interceptors.request.use((request) => {
  const userStore = useUserStore()
  if (request.headers && userStore.isLogin) {
    request.headers.Authorization = `Bearer ${userStore.token}`
  }
  return request
})

api.interceptors.response.use(
  (response) => {
    const payload = response.data

    if (payload && typeof payload === 'object') {
      if ('status' in payload && !('code' in payload)) {
        const statusValue = payload.status
        if (statusValue === 1) {
          return payload
        }
        if (statusValue === 0) {
          useUserStore().requestLogout()
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

export * from './modules/auth'
export * from './modules/image'
export * from './modules/logs'
export * from './modules/monitoring'
export * from './modules/oss'
export * from './modules/records'
export * from './modules/search'
export * from './modules/statistics'
export * from './modules/system'
