import api from '../index'

export function runPressureTest(data: any) {
  return api.post('/monitoring-api/pressure-tests/run', data)
}

export function getPressureTestHistory() {
  return api.get('/monitoring-api/pressure-tests/history')
}

export function getLatestPressureTest() {
  return api.get('/monitoring-api/pressure-tests/latest')
}

export function getPressureTestByRunId(runId: string) {
  return api.get(`/monitoring-api/pressure-tests/${runId}`)
}

export function clearPressureTestHistory() {
  return api.delete('/monitoring-api/pressure-tests/history')
}
