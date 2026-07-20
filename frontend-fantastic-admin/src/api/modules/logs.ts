import type { ImageAuditAnalytics, LogRecord, PaginatedResult } from '../types'
import api, { getRequest, postRequest } from '../index'

export interface ImageAuditFilterParams {
  keyword?: string
  username?: string
  clientIp?: string
  auditAction?: string
  responseStatus?: string
  startTime?: string
  endTime?: string
}

export function searchSystemLogs(params: {
  page: number
  size: number
  keyword?: string
  username?: string
  clientIp?: string
  requestUri?: string
  method?: string
  responseStatus?: string
  startTime?: string
  endTime?: string
}) {
  return getRequest<PaginatedResult<LogRecord>>('/api/v1/logs/search', { params })
}

export function searchImageAuditLogs(params: ImageAuditFilterParams & {
  page: number
  size: number
}) {
  return getRequest<PaginatedResult<LogRecord>>('/api/v1/logs/audit/images', { params })
}

export function getImageAuditAnalytics(params: ImageAuditFilterParams) {
  return getRequest<ImageAuditAnalytics>('/api/v1/logs/audit/images/analytics', { params })
}

export async function exportImageAuditLogs(params: ImageAuditFilterParams & {
  scope?: 'all' | 'user' | 'target'
  value?: string
}) {
  // The global Axios response interceptor returns response.data directly.
  // Normalize the Blob back to the shape expected by the download caller.
  const blob = await api.get<Blob>('/api/v1/logs/audit/images/export', {
    params,
    responseType: 'blob',
    skipResponseMetrics: true,
  }) as unknown as Blob

  if (!(blob instanceof Blob)) {
    throw new TypeError('导出接口未返回有效的文件数据')
  }

  return {
    data: blob,
    headers: {} as Record<string, string>,
  }
}

export function getLogById(id: string | number) {
  return getRequest<LogRecord>(`/api/v1/logs/${id}`)
}

/** POST /api/v1/logs/retention/cleanup — 运行日志清理 */
export function runLogRetentionCleanup(params?: Record<string, any>) {
  return postRequest<void>('/api/v1/logs/retention/cleanup', null, { params })
}

/** GET /api/v1/logs/retention/export — 导出待清理日志（blob 下载，不走 Result 包装） */
export function exportLogRetentionLogs() {
  return api.get<Blob>('/api/v1/logs/retention/export', {
    responseType: 'blob',
  })
}
