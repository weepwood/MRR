/** 扫描记录 */
export interface ScanRecord {
  id?: number
  brxh?: string
  bah?: string
  filename?: string
  btype?: number | null
  pages?: number | null
  openerNo?: string
  uploadDate?: string | null
  uploadFlag?: number | null
  folder?: string
}

/** 扫描记录创建/更新请求 */
export interface ScanRequest {
  bah?: string
  brxh?: string
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
  filename?: string
  btype?: number | null
  pages?: number | null
  openerNo?: string
  uploadDate?: string | null
  uploadFlag?: number | null
  img_url?: string
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
  id?: number
  bah?: string
  cid?: string
  openerNo?: string
  date?: string
  type?: string
  pages?: number
}

/** BAH 统计汇总 */
export interface BAHStatistics {
  bah?: string
  recordCount?: number
  totalPages?: number
}

/** 日期统计汇总 */
export interface DateStatistics {
  date?: string
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

/** 日志记录 */
export interface LogRecord {
  id?: number
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
}

/** 压测请求 */
export interface PressureTestRequest {
  concurrency: number
  requests: number
  targetUrl: string
}

/** 压测报告 */
export interface PressureTestReport {
  runId?: string
  targetUrl?: string
  concurrency?: number
  totalRequests?: number
  successfulRequests?: number
  failedRequests?: number
  averageResponseTime?: number
  minResponseTime?: number
  maxResponseTime?: number
  p95ResponseTime?: number
  startTime?: string
  endTime?: string
  status?: string
  message?: string
}

/** 加密ID搜索参数 */
export interface EncryptIDSearchParams {
  EncryptID: string
  userId: string
  iv: string
  timestamp: string
}

/** 认证用户（对齐后端 AuthUserProfileDTO） */
export interface AuthUser {
  id?: number
  username?: string
  displayName?: string
  roleCode?: string
  roleName?: string
  permissions?: string[]
  status?: string
  lastLoginAt?: string
}

/** 认证角色（对齐后端 AuthRole） */
export interface AuthRole {
  code?: string
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
  user?: AuthUser
}
