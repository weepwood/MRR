import api from '../index'

export function searchSystemLogs(params: any = {}) {
  return api.get('/v1/logs-api/search', { params })
}

export function getLogById(id: string | number) {
  return api.get(`/v1/logs-api/${id}`)
}

export function runLogRetentionCleanup(params: any = {}) {
  return api.post('/v1/logs-api/retention/cleanup', null, { params })
}

export function exportLogRetentionLogs() {
  return api.get('/v1/logs-api/retention/export', { responseType: 'blob' })
}
