import type { AxiosRequestConfig, Method } from 'axios'
import axios from 'axios'

export interface AuthTestRequest {
  method: Method
  path: string
  token?: string
  headers?: Record<string, string>
  body?: unknown
  rawBody?: string
}

export interface AuthTestResult {
  status: number
  statusText: string
  durationMs: number
  headers: Record<string, unknown>
  data: unknown
}

const authTestApi = axios.create({
  baseURL: import.meta.env.DEV ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL,
  timeout: 1000 * 60,
  responseType: 'json',
  withCredentials: true,
  validateStatus: () => true,
})

function normalizeApiPath(path: string): string {
  const value = path.trim()
  if (!value.startsWith('/api/')) {
    throw new Error('测试接口必须是以 /api/ 开头的当前 MRR 后端路径')
  }
  if (value.startsWith('//') || value.includes('://')) {
    throw new Error('测试台不允许向外部域名发送请求')
  }
  return value
}

export async function executeAuthTestRequest(request: AuthTestRequest): Promise<AuthTestResult> {
  const headers: Record<string, string> = { ...(request.headers || {}) }
  if (request.token?.trim()) {
    headers.Authorization = `Bearer ${request.token.trim()}`
  }
  const data = request.rawBody === undefined ? request.body : request.rawBody
  const config: AxiosRequestConfig = {
    method: request.method,
    url: normalizeApiPath(request.path),
    headers,
    data,
  }

  const startedAt = performance.now()
  const response = await authTestApi.request(config)
  return {
    status: response.status,
    statusText: response.statusText,
    durationMs: Math.round((performance.now() - startedAt) * 100) / 100,
    headers: typeof response.headers.toJSON === 'function'
      ? response.headers.toJSON()
      : { ...response.headers },
    data: response.data,
  }
}
