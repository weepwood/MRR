import api from '../index'

export async function runPressureTest(data: any) {
  const response = await api.post('/v1/monitoring-api/pressure-tests/run', data)
  return response.data
}

export async function getPressureTestHistory() {
  const response = await api.get('/v1/monitoring-api/pressure-tests/history')
  return response.data
}

export async function getLatestPressureTest() {
  const response = await api.get('/v1/monitoring-api/pressure-tests/latest')
  return response.data
}

export async function getPressureTestByRunId(runId: string) {
  const response = await api.get(`/v1/monitoring-api/pressure-tests/${runId}`)
  return response.data
}

export async function clearPressureTestHistory() {
  const response = await api.delete('/v1/monitoring-api/pressure-tests/history')
  return response.data
}
