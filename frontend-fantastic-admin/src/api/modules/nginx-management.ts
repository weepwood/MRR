import type { ApiResult } from '../types'
import { getRequest, postRequest } from '../index'

export interface NginxManagedStatus {
  enabled: boolean
  supported: boolean
  installed: boolean
  serviceName: string
  serviceState: 'RUNNING' | 'STOPPED' | 'OTHER' | 'UNAVAILABLE'
  version: string
  configurationPresent: boolean
  lastCheckedAt: string
}

export interface NginxManagedFile {
  name: string
  present: boolean
  bytes: number | null
  modifiedAt: string | null
}

export interface NginxConfigTest {
  valid: boolean
  message: string
  durationMs: number
}

export interface NginxLogTail {
  type: 'access' | 'error'
  lines: string[]
  truncated: boolean
}

async function unwrap<T>(request: Promise<ApiResult<T>>, action: string): Promise<T> {
  const response = await request
  if (response.data === undefined) {
    throw new Error(`${action}未返回数据`)
  }
  return response.data
}

const base = '/api/v1/nginx/management'

export function getNginxManagedStatus() {
  return unwrap(getRequest<NginxManagedStatus>(`${base}/status`), '网关状态查询')
}

export function getNginxManagedFiles() {
  return unwrap(getRequest<NginxManagedFile[]>(`${base}/config/files`), 'Nginx 配置文件列表')
}

export function testNginxManagedConfig() {
  return unwrap(postRequest<NginxConfigTest>(`${base}/config/test`), 'Nginx 配置检查')
}

export function getNginxManagedLogs(type: 'access' | 'error', lines = 100) {
  return unwrap(getRequest<NginxLogTail>(`${base}/logs`, {
    params: { type, lines },
    skipResponseMetrics: true,
  }), 'Nginx 日志查询')
}
