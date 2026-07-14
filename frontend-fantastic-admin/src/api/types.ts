/** 后端统一响应格式 */
export interface ApiResult<T = unknown> {
  code?: number
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

/** 系统健康信息 */
export interface HealthInfo {
  status?: string
  timestamp?: string
  port?: string
  application?: string
  components?: Record<string, { status?: string, [key: string]: unknown }>
}

/** JVM 运行时信息 */
export interface RuntimeInfo {
  name?: string
  startTime?: number
  uptimeMillis?: number
  uptimeFormatted?: string
  classPath?: string
  inputArguments?: string[]
}

/** 内存信息 */
export interface MemorySegment {
  init?: string
  used?: string
  committed?: string
  max?: string
}

export interface MemoryInfo {
  heap?: MemorySegment
  nonHeap?: MemorySegment
  usagePercent?: string
}

/** 系统与 JVM 基本信息 */
export interface SystemInfo {
  application?: {
    name?: string
    startTime?: string
    runTime?: string
  }
  jvm?: {
    javaVersion?: string
    javaVendor?: string
    javaHome?: string
    availableProcessors?: number
    maxMemory?: string
    totalMemory?: string
    freeMemory?: string
    usedMemory?: string
  }
  operatingSystem?: {
    name?: string
    version?: string
    arch?: string
    availableProcessors?: string
    systemLoadAverage?: string
  }
}

export interface GcStatItem {
  name?: string
  count?: number
  timeMs?: number
}

export interface GcStats {
  totalCollections?: number
  totalTimeMs?: number
  [key: string]: GcStatItem | number | undefined
}

export interface ThreadStats {
  currentCount?: number
  daemonCount?: number
  peakCount?: number
  totalStarted?: number
}

export interface SystemOverview {
  info?: SystemInfo
  memory?: MemoryInfo
  runtime?: RuntimeInfo
  health?: HealthInfo
  properties?: Record<string, string>
  gc?: GcStats
  threads?: ThreadStats
}

export interface ActuatorMeasurement {
  statistic?: string
  value?: number
}

export interface ActuatorMetric {
  name?: string
  description?: string
  baseUnit?: string
  measurements?: ActuatorMeasurement[]
  availableTags?: Array<{ tag?: string, values?: string[] }>
}
