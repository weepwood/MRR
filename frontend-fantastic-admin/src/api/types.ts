/** 后端统一响应格式 */
export interface ApiResult<T = unknown> {
  code?: number | string
  message?: string
  data?: T
  timestamp?: string
}

/** 分页响应包装 */
export interface PaginatedResult<T> {
  list: T[]
  total: number
  page: number
  size: number
  totalPages?: number
}

/** 扫描记录 */
export interface ScanRecord {
  id?: number
  brxh?: string
  bah?: string
  sjh?: string
  filename?: string
  btype?: number | null
  pages?: number | null
  openerNo?: string
  uploadDate?: string | null
  uploadFlag?: number | null
  folder?: string
  ossUrl?: string
  fileSize?: number | null
  checksumMd5?: string
  migrationStatus?: string
  migratedAt?: string | null
}

/** 扫描记录创建/更新请求 */
export interface ScanRequest {
  bah?: string
  brxh?: string
  sjh?: string
  filename?: string
  btype?: number | null
  pages?: number | null
  openerNo?: string
  uploadFlag?: number | null
  folder?: string
}

/** BAH 图片数据 */
export interface BAHImageData {
  id?: number
  brxh?: string
  bah?: string
  sjh?: string
  filename?: string
  btype?: number | null
  pages?: number | null
  openerNo?: string
  uploadDate?: string | null
  uploadFlag?: number | null
  img_url?: string
  ossUrl?: string
}

/** 住院病案搜索记录（服务端持久化） */
export interface ArchiveSearchHistoryRecord {
  id?: number
  bah?: string
  sjh?: string
  success: boolean
  imageCount: number
  queryCount: number
  failureReason?: string
  favorite: boolean
  searchedAt: string
}

/** 病案记录（身份证查询返回） */
export interface BAHRecord {
  id?: number
  bah?: string
  name?: string
  department?: string
  admissionTime?: string
}

/** 图片类型更新请求 */
export interface ImageTypeRequest {
  btype: number
}

/** 统计记录 */
export interface StatisticsRecord {
  bah?: string
  cid?: string
  openerNo?: string
  date?: string
  type?: string
  pages?: number
  sjh?: string
}

/** BAH 统计汇总 */
export interface BAHStatistics {
  bah?: string
  recordCount?: number
  totalPages?: number
}

/** 日期统计汇总 */
export interface DateStatistics {
  date: string
  recordCount?: number
  totalPages?: number
}

/** 类型统计汇总 */
export interface TypeStatistics {
  type?: string
  recordCount?: number
  totalPages?: number
}

/** 总体统计摘要 */
export interface StatisticsSummary {
  total?: {
    totalRecords?: number
    totalPages?: number
  }
  uniqueBAHCount?: number
  byType?: TypeStatistics[]
}

/** Dashboard 数据 */
export interface DashboardData {
  overview?: Record<string, any>
  uniqueBAHCount?: number
  recentTrend?: DateStatistics[]
  topBAH?: BAHStatistics[]
}

/** 浏览器观测到的单次 API 响应指标（不包含请求参数或响应内容） */
export interface FrontendResponseMetric {
  requestId: string
  routePattern: string
  method: string
  httpStatus: number
  businessCode?: number
  success: boolean
  clientDurationMs: number
  serverDurationMs?: number
  occurredAt: string
}

/** 接口响应分析总览 */
export interface ResponseMetricOverview {
  totalRequests: number
  frontendSampleCount?: number
  successRate: number
  avgServerDurationMs: number
  avgClientDurationMs: number
  p95ClientDurationMs: number
}

/** 接口响应趋势点 */
export interface ResponseMetricTrendPoint {
  bucket: string
  requestCount: number
  errorCount: number
  avgServerDurationMs: number
  avgClientDurationMs: number
}

/** 慢接口排行项 */
export interface SlowEndpointMetric {
  routePattern: string
  method: string
  requestCount: number
  errorCount: number
  avgServerDurationMs: number
  avgClientDurationMs: number
  p95ClientDurationMs: number
}

/** 接口响应分析聚合结果 */
export interface ResponseMetricAnalysis {
  overview: ResponseMetricOverview
  trend: ResponseMetricTrendPoint[]
  slowEndpoints: SlowEndpointMetric[]
}

/** 日志记录 */
export interface LogRecord {
  id?: number
  requestId?: string
  username?: string
  clientIp?: string
  requestUri?: string
  endpointTemplate?: string
  method?: string
  userAgent?: string
  accessTime?: string
  queryString?: string
  requestBody?: string
  responseStatus?: number
  executeTime?: number | null
  referer?: string
  errorMessage?: string
  auditAction?: string
  auditTarget?: string
  auditDescription?: string
}

/** 图片访问审计聚合项 */
export interface ImageAuditCountItem {
  label: string
  count: number
}

/** 图片访问审计每日趋势 */
export interface ImageAuditTrendItem {
  date: string
  count: number
}

/** 图片访问审计分析 */
export interface ImageAuditAnalytics {
  totalAccesses: number
  uniqueUsers: number
  uniqueTargets: number
  abnormalAccesses: number
  averageDurationMs: number
  trend: ImageAuditTrendItem[]
  actionDistribution: ImageAuditCountItem[]
  topUsers: ImageAuditCountItem[]
}

/** 图片迁移记录 */
export interface MigrationLogRecord {
  id?: number
  scanId?: number
  localPath?: string
  ossUrl?: string
  migrationStatus?: string
  errorMessage?: string
  fileSize?: number | null
  checksumMd5?: string
  migratedAt?: string | null
  verifiedAt?: string | null
  createdAt?: string
  updatedAt?: string
}
