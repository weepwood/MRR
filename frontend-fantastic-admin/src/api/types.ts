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
  username?: string
  clientIp?: string
  requestUri?: string
  method?: string
  userAgent?: string
  accessTime?: string
  queryString?: string
  requestBody?: string
  responseStatus?: number
  executeTime?: number | null
  referer?: string
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

/** 图片访问审计分析汇总 */
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

/** 加密ID搜索参数 */
export interface EncryptIDSearchParams {
  EncryptID: string
  userId: string
  iv: string
  timestamp: string
}

/** 认证用户（对齐后端 AuthUserProfileDTO / AuthSession） */
export interface AuthUser {
  id?: number
  username?: string
  displayName?: string
  roleCode?: string
  roleName?: string
  permissions?: string[]
  status?: string
  mustChangePassword?: boolean
  passwordVersion?: number
  passwordChangedAt?: string
  temporaryPasswordExpiresAt?: string
  passwordResetAt?: string
  passwordResetBy?: number
  lastLoginAt?: string
  createdAt?: string
  updatedAt?: string
}

/** 认证角色（对齐后端 AuthRole） */
export interface AuthRole {
  code?: string
  name?: string
  description?: string
  permissions?: string
  sortOrder?: number
}

/** 角色更新请求 */
export interface AuthRoleUpdatePayload {
  name?: string
  description?: string
  permissions?: string
  sortOrder?: number
}

/** 用户更新请求（对齐后端 AuthUserUpdateRequest） */
export interface AuthUserUpdatePayload {
  displayName?: string
  roleCode: string
  status: string
}

/** 登录请求 */
export interface LoginRequest {
  username: string
  password: string
}

/** 登录响应 */
export interface LoginResponse {
  token?: string
  accessToken?: string
  jwt?: string
  user?: AuthUser
  profile?: AuthUser
  nextAction?: 'NONE' | 'CHANGE_PASSWORD'
  data?: LoginResponse
}

/** 注册请求 */
export interface RegisterRequest {
  username: string
  password: string
  displayName?: string
}

/** 注册响应（与登录响应结构一致） */
export interface RegisterResponse {
  token?: string
  user?: AuthUser
}

/** OSS 上传结果 */
export interface OssUploadResult {
  scanId?: number
  ossUrl?: string
  fileSize?: number
  checksumMd5?: string
  status?: string
  errorMessage?: string
}

/** 迁移统计 */
export interface MigrationStatistics {
  totalCount?: number
  migratedCount?: number
  pendingCount?: number
  failedCount?: number
  percentage?: number
}

/** 迁移日志记录 */
export interface MigrationLogRecord {
  id?: number
  scanId?: number
  localPath?: string
  ossUrl?: string
  migrationStatus?: string
  errorMessage?: string
  fileSize?: number
  checksumMd5?: string
  migratedAt?: string
  verifiedAt?: string
  createdAt?: string
  updatedAt?: string
}

// ===================================================================
// 系统监控类型
// ===================================================================

/** JVM 基本信息 */
export interface JvmInfo {
  javaVersion?: string
  javaVendor?: string
  javaHome?: string
  availableProcessors?: number
  maxMemory?: string
  totalMemory?: string
  freeMemory?: string
  usedMemory?: string
}

/** 应用基本信息 */
export interface AppInfo {
  name?: string
  startTime?: string
  runTime?: string
}

/** OS 信息 */
export interface OsInfo {
  name?: string
  version?: string
  arch?: string
  availableProcessors?: string
  systemLoadAverage?: string
}

/** 系统信息（GET /system/info） */
export interface SystemInfo {
  application?: AppInfo
  jvm?: JvmInfo
  operatingSystem?: OsInfo
}

/** 内存使用数据 */
export interface MemoryUsage {
  init?: string
  used?: string
  committed?: string
  max?: string
}

/** 内存信息（GET /system/memory） */
export interface MemoryInfo {
  heap?: MemoryUsage
  nonHeap?: MemoryUsage
  usagePercent?: string
}

/** 运行时信息（GET /system/runtime） */
export interface RuntimeInfo {
  name?: string
  startTime?: number
  uptimeMillis?: number
  uptimeFormatted?: string
  classPath?: string
  inputArguments?: string[]
}

/** 组件健康状态 */
export interface ComponentHealth {
  status?: string
  usagePercent?: string
  error?: string
}

/** 健康检查（GET /system/health） */
export interface HealthInfo {
  status?: string
  timestamp?: string
  port?: string
  application?: string
  components?: Record<string, ComponentHealth>
}

/** GC 统计单项 */
export interface GcStatItem {
  name?: string
  count?: number
  timeMs?: number
}

/** GC 统计 */
export interface GcStats extends Record<string, GcStatItem | number | undefined> {
  totalCollections?: number
  totalTimeMs?: number
}

/** 线程统计 */
export interface ThreadStats {
  currentCount?: number
  daemonCount?: number
  peakCount?: number
  totalStarted?: number
}

/** 系统监控总览（GET /system/overview） */
export interface SystemOverview {
  info?: SystemInfo
  memory?: MemoryInfo
  runtime?: RuntimeInfo
  health?: HealthInfo
  properties?: Record<string, string>
  gc?: GcStats
  threads?: ThreadStats
}

// ===================================================================
// 统计报表类型
// ===================================================================

/** 统计明细查询参数 */
export interface StatisticsDetailQuery {
  startDate?: string
  endDate?: string
  type?: string
  keyword?: string
  date?: string
  page?: number
  size?: number
}

/** 统计明细记录 */
export interface StatisticsDetailRecord {
  id?: number
  bah?: string
  name?: string
  department?: string
  admissionTime?: string
  date?: string
  type?: string
  pages?: number
  sjh?: string
  openerNo?: string
  cid?: string
}

/** 病案统计查询参数 */
export interface RecordsStatisticsQuery {
  page?: number
  size?: number
  startDate?: string
  endDate?: string
  type?: string
  date?: string
  keyword?: string
}

/** 病案统计记录 */
export interface RecordsStatistic {
  bah?: string
  cid?: string
  openerNo?: string
  date?: string
  type?: string
  pages?: number
  sjh?: string
  recordCount?: number
  totalPages?: number
  totalRecords?: number
}

/** Actuator 指标测量值 */
export interface ActuatorMeasurement {
  statistic?: string
  value?: number
}

/** Actuator 指标（GET /actuator/metrics/{name}） */
export interface ActuatorMetric {
  name?: string
  description?: string
  baseUnit?: string
  measurements?: ActuatorMeasurement[]
}
