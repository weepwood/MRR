import type { AxiosRequestConfig } from 'axios'
import api from '../index'

/** GET /actuator/health */
export function getActuatorHealth(config?: AxiosRequestConfig) {
  return api.get('/actuator/health', config)
}

/** GET /actuator/metrics/{name} — 获取指定指标 */
export function getMetric(name: string, config?: AxiosRequestConfig) {
  return api.get(`/actuator/metrics/${name}`, config)
}

/** GET /actuator/metrics — 获取全部指标名列表 */
export function listMetrics(config?: AxiosRequestConfig) {
  return api.get('/actuator/metrics', config)
}
