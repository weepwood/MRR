import { authApi } from './config'

export function searchSystemLogs(params: any = {}) {
  return authApi.get('/logs-api/search', { params })
}

export function getLogById(id: string | number) {
  return authApi.get(`/logs-api/${id}`)
}

export function runLogRetentionCleanup(params: any = {}) {
  return authApi.post('/logs-api/retention/cleanup', null, { params })
}

export function exportLogRetentionLogs() {
  return authApi.get('/logs-api/retention/export', { responseType: 'blob' })
}
