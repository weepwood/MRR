import type { FrontendResponseMetric, ResponseMetricAnalysis } from '../types'
import { getRequest, postRequest } from '../index'

/** POST /api/v1/response-metrics/frontend/batch — 批量上报浏览器响应指标 */
export function reportFrontendResponseMetrics(metrics: FrontendResponseMetric[]) {
  return postRequest<void, { metrics: FrontendResponseMetric[] }>(
    '/api/v1/response-metrics/frontend/batch',
    { metrics },
    { skipResponseMetrics: true, skipGlobalError: true },
  )
}

/** GET /api/v1/response-metrics/analysis — 默认获取最近 365 天响应分析 */
export function getResponseMetricAnalysis(days = 365) {
  return getRequest<ResponseMetricAnalysis>('/api/v1/response-metrics/analysis', {
    params: { days },
  })
}
