import api from '../index'

export async function getStatisticsSummary() {
  const response = await api.get('/v1/statistics-api/summary')
  return response
}

export async function getStatisticsDateSummary() {
  const response = await api.get('/v1/statistics-api/date-summary')
  return response
}

export async function getDashboardData() {
  const response = await api.get('/v1/statistics-api/dashboard')
  return response
}

export async function getStatisticsList(pageOrParams: any = 1, size = 100) {
  const params = typeof pageOrParams === 'object' && pageOrParams !== null
    ? { ...pageOrParams }
    : { page: pageOrParams, size }
  const response = await api.get('/v1/statistics-api', { params })
  return response
}
