import api from '../index'

export function getPressureTestHistory() {
  return api.get('/v1/monitoring/pressure/history')
}

export function getLatestPressureTest() {
  return api.get('/v1/monitoring/pressure/latest')
}

export function runPressureTest(data: {
  concurrency: number
  requests: number
  targetPath: string
}) {
  return api.post('/v1/monitoring/pressure/run', data)
}

export function clearPressureTestHistory() {
  return api.delete('/v1/monitoring/pressure/history')
}
