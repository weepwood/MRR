import { authApi } from './config'

export function runPressureTest(data: any) {
  return authApi.post('/monitoring-api/pressure-tests/run', data)
}

export function getPressureTestHistory() {
  return authApi.get('/monitoring-api/pressure-tests/history')
}

export function getLatestPressureTest() {
  return authApi.get('/monitoring-api/pressure-tests/latest')
}

export function getPressureTestByRunId(runId: string) {
  return authApi.get(`/monitoring-api/pressure-tests/${runId}`)
}

export function clearPressureTestHistory() {
  return authApi.delete('/monitoring-api/pressure-tests/history')
}
