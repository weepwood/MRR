import type { AxiosResponse } from 'axios'
import type { ApiResult, BAHImageData } from '../types'
import axios from 'axios'
import { readonly, shallowRef } from 'vue'

export interface ExternalArchiveCase {
  bah: string
  sjh?: string
  patientName?: string
  department?: string
  admissionTime?: string
}

export interface ExternalArchiveSession {
  clientId: string
  externalUserId: string
  allowDownload: boolean
  expiresIn: number
  cases: ExternalArchiveCase[]
}

export interface ExternalArchiveRequestOptions {
  timeout?: number
}

const externalArchiveSessionState = shallowRef<ExternalArchiveSession | null>(null)

export const externalArchiveSession = readonly(externalArchiveSessionState)

export function setExternalArchiveSession(session: ExternalArchiveSession): void {
  externalArchiveSessionState.value = session
}

export function clearExternalArchiveSession(): void {
  externalArchiveSessionState.value = null
}

const externalApi = axios.create({
  baseURL: import.meta.env.DEV ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL,
  timeout: 1000 * 60,
  responseType: 'json',
  withCredentials: true,
})

externalApi.interceptors.response.use((response) => {
  if (response.config.responseType === 'blob') {
    return response
  }
  const payload = response.data
  if (payload?.code === 200) {
    return payload
  }
  if (payload?.code === 401) {
    clearExternalArchiveSession()
  }
  const error = new Error(payload?.message || '外部影像访问失败')
  Object.assign(error, { response })
  return Promise.reject(error)
}, (error) => {
  if (error?.response?.status === 401) {
    clearExternalArchiveSession()
  }
  return Promise.reject(error)
})

export function exchangeExternalArchiveTicket(ticket: string, options: ExternalArchiveRequestOptions = {}) {
  return externalApi.post<unknown, ApiResult<ExternalArchiveSession>>(
    '/api/v1/external/archive/session',
    { ticket },
    { timeout: options.timeout },
  )
}

export function getExternalArchiveContext(options: ExternalArchiveRequestOptions = {}) {
  return externalApi.get<unknown, ApiResult<ExternalArchiveSession>>('/api/v1/external/archive/context', {
    timeout: options.timeout,
  })
}

export function getExternalArchiveImages(bah: string, sjh?: string, forceRefresh = false) {
  return externalApi.get<unknown, ApiResult<BAHImageData[]>>('/api/v1/external/archive/images', {
    params: {
      bah,
      sjh: sjh || undefined,
      ...(forceRefresh ? { _: Date.now() } : {}),
    },
    headers: forceRefresh
      ? {
          'Cache-Control': 'no-cache',
          'Pragma': 'no-cache',
        }
      : undefined,
  })
}

export function downloadExternalArchive(bah: string, sjh?: string) {
  return externalApi.get<Blob, AxiosResponse<Blob>>('/api/v1/external/archive/download', {
    params: {
      bah,
      sjh: sjh || undefined,
    },
    responseType: 'blob',
  })
}
