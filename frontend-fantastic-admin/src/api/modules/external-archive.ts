import type { ApiResult, BAHImageData } from '../types'
import axios from 'axios'

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

const externalApi = axios.create({
  baseURL: import.meta.env.DEV ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL,
  timeout: 1000 * 60,
  responseType: 'json',
  withCredentials: true,
})

externalApi.interceptors.response.use((response) => {
  const payload = response.data
  if (payload?.code === 200) {
    return payload
  }
  const error = new Error(payload?.message || '外部影像访问失败')
  Object.assign(error, { response })
  return Promise.reject(error)
})

export function exchangeExternalArchiveTicket(ticket: string) {
  return externalApi.post<unknown, ApiResult<ExternalArchiveSession>>('/api/v1/external/archive/session', { ticket })
}

export function getExternalArchiveContext() {
  return externalApi.get<unknown, ApiResult<ExternalArchiveSession>>('/api/v1/external/archive/context')
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
