import api from '../index'

export function searchSystemLogs(params: any = {}) {
  return api.get('/logs-api/search', { params })
}

export function getLogById(id: string | number) {
  return api.get(`/logs-api/${id}`)
}

export function runLogRetentionCleanup(params: any = {}) {
  return api.post('/logs-api/retention/cleanup', null, { params })
}

export function exportLogRetentionLogs() {
  return api.get('/logs-api/retention/export', { responseType: 'blob' })
}
