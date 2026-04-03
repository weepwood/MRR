import api from '../index'

export function searchSystemLogs(params: {
  page: number
  size: number
  keyword?: string
  clientIp?: string
  requestUri?: string
  method?: string
  responseStatus?: string
  startTime?: string
  endTime?: string
}) {
  return api.get('/v1/logs-api/search', { params })
}

export function getLogById(id: string | number) {
  return api.get(`/v1/logs-api/${id}`)
}
