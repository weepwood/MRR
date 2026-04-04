import api from '../index'
import type { PressureTestRequest } from '../types'

/** POST /v1/monitoring-api/pressure-tests/run — 执行压测 */
export function runPressureTest(data: PressureTestRequest) {
  return api.post('/v1/monitoring-api/pressure-tests/run', data)
}

/** GET /v1/monitoring-api/pressure-tests/history — 获取压测历史 */
export function getPressureTestHistory() {
  return api.get('/v1/monitoring-api/pressure-tests/history')
}

/** GET /v1/monitoring-api/pressure-tests/latest — 获取最近一次压测结果 */
export function getLatestPressureTest() {
  return api.get('/v1/monitoring-api/pressure-tests/latest')
}

/** GET /v1/monitoring-api/pressure-tests/{runId} — 根据runId获取压测详情 */
export function getPressureTestByRunId(runId: string) {
  return api.get(`/v1/monitoring-api/pressure-tests/${runId}`)
}

/** DELETE /v1/monitoring-api/pressure-tests/history — 清空压测历史 */
export function clearPressureTestHistory() {
  return api.delete('/v1/monitoring-api/pressure-tests/history')
}
