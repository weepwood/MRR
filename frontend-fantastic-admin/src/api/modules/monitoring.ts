import type { PressureTestRequest } from '../types'
import api from '../index'

/** POST /api/v1/monitoring/pressure-tests/run — 执行压测 */
export function runPressureTest(data: PressureTestRequest) {
  return api.post('/api/v1/monitoring/pressure-tests/run', data)
}

/** GET /api/v1/monitoring/pressure-tests/history — 获取压测历史 */
export function getPressureTestHistory() {
  return api.get('/api/v1/monitoring/pressure-tests/history')
}

/** GET /api/v1/monitoring/pressure-tests/latest — 获取最近一次压测结果 */
export function getLatestPressureTest() {
  return api.get('/api/v1/monitoring/pressure-tests/latest')
}

/** GET /api/v1/monitoring/pressure-tests/{runId} — 根据runId获取压测详情 */
export function getPressureTestByRunId(runId: string) {
  return api.get(`/api/v1/monitoring/pressure-tests/${runId}`)
}

/** DELETE /api/v1/monitoring/pressure-tests/history — 清空压测历史 */
export function clearPressureTestHistory() {
  return api.delete('/api/v1/monitoring/pressure-tests/history')
}
