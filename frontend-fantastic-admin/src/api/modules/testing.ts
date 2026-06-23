import type { SmokeTestItem, ApiTestRequest, ApiTestResponse, DataCheckItem } from '../types'
import { getRequest, postRequest } from '../index'

/** GET /api/v1/testing/smoke — 执行全量冒烟测试 */
export function runSmokeTests() {
  return getRequest<SmokeTestItem[]>('/api/v1/testing/smoke')
}

/** POST /api/v1/testing/api-test — 调试指定 API 接口 */
export function runApiTest(data: ApiTestRequest) {
  return postRequest<ApiTestResponse>('/api/v1/testing/api-test', data)
}

/** GET /api/v1/testing/data-check — 执行数据完整性检查 */
export function runDataCheck() {
  return getRequest<DataCheckItem[]>('/api/v1/testing/data-check')
}
