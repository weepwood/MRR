import api from '../index'

export function getStatisticsSummary() {
  return api.get('/v1/statistics/summary')
}

export function getStatisticsList(params: {
  page: number
  size: number
  keyword?: string
  type?: string
  startDate?: string
  endDate?: string
  sortBy?: string
  sortOrder?: string
}) {
  return api.get('/v1/statistics/list', { params })
}

export function getStatisticsDateSummary() {
  return api.get('/v1/statistics/date-summary')
}

export function getDashboardData() {
  return api.get('/v1/statistics/dashboard')
}
