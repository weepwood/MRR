import type { BAHStatistics, DateStatistics, StatisticsRecord, StatisticsSummary, TypeStatistics } from '../types'
import api from '../index'

/** GET /v1/statistics-api — 获取所有统计数据(分页+条件) */
export function getStatisticsList(params: {
  page: number
  size: number
  keyword?: string
  type?: string
  startDate?: string
  endDate?: string
  sortBy?: string
  sortOrder?: string
}) {
  return api.get('/v1/statistics-api', { params })
}

/** GET /v1/statistics-api/summary — 获取总体统计信息 */
export function getStatisticsSummary() {
  return api.get<StatisticsSummary>('/v1/statistics-api/summary')
}

/** GET /v1/statistics-api/date-summary — 统计每个日期的记录数和总页数 */
export function getStatisticsDateSummary() {
  return api.get<DateStatistics[]>('/v1/statistics-api/date-summary')
}

/** GET /v1/statistics-api/dashboard — 获取综合统计面板数据 */
export function getDashboardData() {
  return api.get('/v1/statistics-api/dashboard')
}

/** GET /v1/statistics-api/bah/{bah} — 根据病案号查询统计数据 */
export function getStatisticsByBah(bah: string) {
  return api.get<StatisticsRecord[]>(`/v1/statistics-api/bah/${bah}`)
}

/** GET /v1/statistics-api/date/{date} — 根据日期查询统计数据 */
export function getStatisticsByDate(date: string) {
  return api.get<StatisticsRecord[]>(`/v1/statistics-api/date/${date}`)
}

/** GET /v1/statistics-api/bah-summary — 统计每个病案号的记录数和总页数 */
export function getBAHStatistics() {
  return api.get<BAHStatistics[]>('/v1/statistics-api/bah-summary')
}

/** GET /v1/statistics-api/type-summary — 按类型统计 */
export function getTypeStatistics() {
  return api.get<TypeStatistics[]>('/v1/statistics-api/type-summary')
}

/** GET /v1/statistics-api/date-summary/condition — 按条件统计每日数据 */
export function getDateSummaryByCondition(params?: {
  startDate?: string
  endDate?: string
  type?: string
}) {
  return api.get<DateStatistics[]>('/v1/statistics-api/date-summary/condition', { params })
}
