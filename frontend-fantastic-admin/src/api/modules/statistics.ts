import type { BAHStatistics, DashboardData, DateStatistics, PaginatedResult, StatisticsRecord, StatisticsSummary, TypeStatistics } from '../types'
import api, { getRequest } from '../index'

/** GET /api/v1/statistics — 获取所有统计数据(分页+条件) */
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
  return getRequest<PaginatedResult<StatisticsRecord>>('/api/v1/statistics', { params })
}

/** GET /api/v1/statistics/summary — 获取总体统计信息 */
export function getStatisticsSummary() {
  return getRequest<StatisticsSummary>('/api/v1/statistics/summary')
}

/** GET /api/v1/statistics/date-summary — 统计每个日期的记录数和总页数 */
export function getStatisticsDateSummary() {
  return getRequest<DateStatistics[]>('/api/v1/statistics/date-summary')
}

/** GET /api/v1/statistics/dashboard — 获取综合统计面板数据 */
export function getDashboardData() {
  return getRequest<DashboardData>('/api/v1/statistics/dashboard')
}

/** GET /api/v1/statistics/bah/{bah} — 根据病案号查询统计数据 */
export function getStatisticsByBah(bah: string) {
  return getRequest<StatisticsRecord[]>(`/api/v1/statistics/bah/${bah}`)
}

/** GET /api/v1/statistics/date/{date} — 根据日期查询统计数据 */
export function getStatisticsByDate(date: string) {
  return getRequest<StatisticsRecord[]>(`/api/v1/statistics/date/${date}`)
}

/** GET /api/v1/statistics/bah-summary — 统计每个病案号的记录数和总页数 */
export function getBAHStatistics() {
  return getRequest<BAHStatistics[]>('/api/v1/statistics/bah-summary')
}

/** GET /api/v1/statistics/type-summary — 按类型统计 */
export function getTypeStatistics() {
  return getRequest<TypeStatistics[]>('/api/v1/statistics/type-summary')
}

/** GET /api/v1/statistics/date-summary/condition — 按条件统计每日数据 */
export function getDateSummaryByCondition(params?: {
  startDate?: string
  endDate?: string
  type?: string
}) {
  return getRequest<DateStatistics[]>('/api/v1/statistics/date-summary/condition', { params })
}

/** GET /api/v1/statistics/export/csv — 导出统计明细 CSV（blob 下载，不走 Result 包装） */
export function exportStatisticsCsv(params: {
  keyword?: string
  bah?: string
  sjh?: string
  type?: string
  startDate?: string
  endDate?: string
}) {
  return api.get<Blob>('/api/v1/statistics/export/csv', {
    params,
    responseType: 'blob',
  })
}
