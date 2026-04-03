import api from '../index'

export function getStatisticsSummary() {
  return api.get('/statistics-api/summary')
}

export function getStatisticsDateSummary() {
  return api.get('/statistics-api/date-summary')
}

export function getDashboardData() {
  return api.get('/statistics-api/dashboard')
}

export function getStatisticsList(pageOrParams: any = 1, size = 100) {
  let params
  if (typeof pageOrParams === 'object' && pageOrParams !== null) {
    params = { ...pageOrParams }
  } else {
    params = { page: pageOrParams, size }
  }
  return api.get('/statistics-api', { params })
}
