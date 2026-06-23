import api from '../index'

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
  return api.get('/api/v1/logs/search', { params })
}

export function searchImageAuditLogs(params: {
  page: number
  size: number
  keyword?: string
  username?: string
  clientIp?: string
  auditAction?: string
  responseStatus?: string
  startTime?: string
  endTime?: string
}) {
  return api.get('/api/v1/logs/audit/images', { params })
}

export function getLogById(id: string | number) {
  return api.get(`/api/v1/logs/${id}`)
}

/** POST /api/v1/logs/retention/cleanup — 运行日志清理 */
export function runLogRetentionCleanup(params?: Record<string, any>) {
  return api.post('/api/v1/logs/retention/cleanup', null, { params })
}

/** GET /api/v1/logs/retention/export — 导出待清理日志 */
export function exportLogRetentionLogs() {
  return api.get('/api/v1/logs/retention/export', {
    responseType: 'blob',
  })
}
