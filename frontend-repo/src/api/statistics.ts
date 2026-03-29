import { authApi } from './config'

export function getStatisticsSummary() {
  return authApi.get('/statistics-api/summary')
}

export function getStatisticsDateSummary() {
  return authApi.get('/statistics-api/date-summary')
}

export function getDashboardData() {
  return authApi.get('/statistics-api/dashboard')
}

export function getStatisticsList(pageOrParams: any = 1, size = 100) {
  let params
  if (typeof pageOrParams === 'object' && pageOrParams !== null) {
    params = { ...pageOrParams }
  } else {
    params = { page: pageOrParams, size }
  }
  return authApi.get('/statistics-api', { params })
}
