import type { ApiResult } from '../types'
import { getRequest, postRequest } from '../index'

export interface DiagnosticStep {
  code: string
  success: boolean
  message: string
  details: Record<string, unknown>
}

export interface ImageSourceDiagnosis {
  diagnosedAt: string
  found: boolean
  scan: Record<string, unknown>
  selectedSource: string
  selectedUrl: string
  fallbackReason: string
  steps: DiagnosticStep[]
}

export interface IntegrityTableCoverage {
  table: string
  total: number
  linked: number
  unlinked: number
  coverage: number
}

export interface IntegritySummary {
  sampleScope?: string
  status?: 'PENDING' | 'READY' | 'ERROR'
  generatedAt: string
  lastAttemptAt?: string
  lastError?: string
  refreshing?: boolean
  archiveCoverage: number
  ossCoverage: number
  missingSjh: number
  brokenLinks: number
  duplicateArchiveGroups: number
  duplicateArchiveDetails?: {
    legacyBahGroups: number
    modernSjhGroups: number
  }
  totalActiveScans: number
  tables: IntegrityTableCoverage[]
}

export interface ExportCenterJob {
  id: string
  owner_username?: string
  format: string
  scope: string
  status: string
  bah?: string
  sjh?: string
  scan_ids?: string
  planned_count: number
  processed_count: number
  failed_count: number
  estimated_bytes: number
  output_bytes: number
  source_summary?: string
  file_name?: string
  sha256?: string
  expires_at?: string
  created_at?: string
  started_at?: string
  completed_at?: string
  updated_at?: string
  download_count: number
  last_downloaded_at?: string
  error_message?: string
}

export interface PermissionRole {
  code: string
  name: string
  permissions: string[]
}

export interface PermissionEndpoint {
  key: string
  method: string
  path: string
  operation: string
  policy: string
  requiredPermissions: string[]
  roleAccess: Record<string, boolean>
}

export interface PermissionChange {
  key: string
  type: 'ADDED' | 'REMOVED' | 'CHANGED'
  before?: PermissionEndpoint
  after?: PermissionEndpoint
}

export interface PermissionMatrix {
  generatedAt: string
  roles: PermissionRole[]
  endpoints: PermissionEndpoint[]
  previousVersion?: string
  previousCreatedAt?: string
  diff: {
    available: boolean
    changeCount?: number
    changes: PermissionChange[]
  }
}

export interface MaintenanceStatus {
  enabled: boolean
  reason: string
  updatedAt: string
  updatedBy: string
}

export interface ReadinessCheck {
  code: string
  name: string
  passed: boolean
  severity: 'CRITICAL' | 'WARNING'
  message: string
  details: Record<string, unknown>
}

export interface ReadinessSnapshot {
  ready: boolean
  readOnly: boolean
  automaticReadOnly?: boolean
  maintenanceReadOnly?: boolean
  mode: 'READ_WRITE' | 'READ_ONLY_DEGRADED' | 'READ_ONLY_MAINTENANCE'
  checkedAt: string
  maintenance?: Pick<MaintenanceStatus, 'enabled'>
  checks: ReadinessCheck[]
}

export interface OperationsRuntime {
  uptimeMs: number
  startedAt: string
  javaVersion: string
  processors: number
  heapUsedBytes: number
  heapCommittedBytes: number
  heapMaxBytes: number
  applicationVersion: string
}

export interface OperationsTaskSummary {
  available?: boolean
  counts: Record<string, number>
  active: number
  failed: number
}

export interface OperationsQuickLink {
  label: string
  path: string
}

export interface CircuitHistory {
  opened?: number
  recovered?: number
  probes?: number
  events?: { sequence: number, type: 'OPENED' | 'PROBE_STARTED' | 'METADATA_ONLY' | 'RECOVERED', at: string }[]
}

export interface NasRuntime {
  waiting?: number
  inUse: number
  limit: number
  nodes: { ordinal: number, state: 'CLOSED' | 'OPEN' | 'HALF_OPEN_READY' | 'HALF_OPEN_ACTIVE', inUse: number, limit: number, waiting?: number, history?: CircuitHistory }[]
}

export interface NetworkRuntime {
  inUse: number
  limit: number
  waiting: number
  nodes: { ordinal: number, state: NasRuntime['nodes'][number]['state'] | 'UNOBSERVED', history?: CircuitHistory }[]
  poolSample?: Partial<{ sampledAt: string, leased: number, available: number, pending: number, max: number }>
}

export interface OperationsOverview {
  nasRuntime?: NasRuntime
  networkRuntime?: Record<'HTTP' | 'NGINX' | 'OSS', NetworkRuntime>
  imageDeliveryMode?: 'direct' | 'gateway'
  generatedAt: string
  readiness: ReadinessSnapshot
  maintenance: Pick<MaintenanceStatus, 'enabled'>
  runtime: OperationsRuntime
  taskSummary: OperationsTaskSummary
  recentServerErrors: number
  recentServerErrorsAvailable?: boolean
  quickLinks: OperationsQuickLink[]
}

export interface OperationsDiagnosticCheck extends ReadinessCheck {
  suggestion: string
  actionLabel: string
  actionPath: string
}

export interface OperationsDiagnosticRun {
  startedAt: string
  completedAt: string
  mode: ReadinessSnapshot['mode']
  readOnly: boolean
  summary: {
    total: number
    passed: number
    failed: number
    critical: number
    warnings: number
  }
  checks: OperationsDiagnosticCheck[]
}

export interface OperationAuditEntry {
  id: number
  request_id?: string
  username?: string
  client_ip?: string
  request_uri?: string
  method?: string
  response_status?: string
  execute_time?: number
  access_time?: string
  error_message?: string
}

async function unwrap<T>(request: Promise<ApiResult<T>>, action: string): Promise<T> {
  const response = await request
  if (response.data === undefined) {
    throw new Error(`${action}未返回数据`)
  }
  return response.data
}

export function getOperationsOverview() {
  return unwrap(
    getRequest<OperationsOverview>('/api/v1/operations/overview'),
    '运维总览查询',
  )
}

export function runOperationsDiagnostics() {
  return unwrap(
    postRequest<OperationsDiagnosticRun>('/api/v1/operations/diagnostics/run'),
    '全面体检',
  )
}

export function getOperationsDiagnosticReport() {
  return unwrap(
    getRequest<Record<string, unknown>>('/api/v1/operations/diagnostic-report'),
    '诊断报告生成',
  )
}

export function getOperationAudit(limit = 50) {
  return unwrap(
    getRequest<OperationAuditEntry[]>('/api/v1/operations/operation-audit', { params: { limit } }),
    '运维操作审计查询',
  )
}

export function getMaintenanceStatus() {
  return unwrap(
    getRequest<MaintenanceStatus>('/api/v1/operations/maintenance'),
    '维护模式查询',
  )
}

export function enableMaintenanceMode(reason: string) {
  return unwrap(
    postRequest<MaintenanceStatus>('/api/v1/operations/maintenance/enable', { reason }),
    '进入维护模式',
  )
}

export function disableMaintenanceMode() {
  return unwrap(
    postRequest<MaintenanceStatus>('/api/v1/operations/maintenance/disable'),
    '退出维护模式',
  )
}

export function diagnoseImageSource(params: { bah?: string, sjh?: string, imageId?: number }) {
  return unwrap(
    getRequest<ImageSourceDiagnosis>('/api/v1/operations/image-source', { params }),
    '图片来源诊断',
  )
}

export interface ImageContentVerification {
  imageId: number
  status: string
  bytesRead: number
  checkedAt: string
  scope: 'SINGLE_IMAGE_BOUNDED'
}

export function verifyImageContent(imageId: number) {
  return unwrap(
    postRequest<ImageContentVerification>('/api/v1/operations/image-content/verify', { imageId }),
    '影像正文校验',
  )
}

export function getIntegritySummary() {
  return unwrap(
    getRequest<IntegritySummary>('/api/v1/operations/integrity'),
    '病案完整性查询',
  )
}

export function getExportCenter(limit = 100) {
  return unwrap(
    getRequest<ExportCenterJob[]>('/api/v1/operations/exports', { params: { limit } }),
    '导出文件中心查询',
  )
}

export function getPermissionMatrix(comparePrevious = true) {
  return unwrap(
    getRequest<PermissionMatrix>('/api/v1/operations/permission-matrix', {
      params: { comparePrevious },
    }),
    '权限矩阵查询',
  )
}

export function savePermissionMatrixSnapshot(version?: string) {
  return unwrap(
    postRequest<{ version: string, createdBy: string, saved: boolean }>(
      '/api/v1/operations/permission-matrix/snapshots',
      { version },
    ),
    '权限矩阵快照保存',
  )
}

export function getDeploymentReadiness(refresh = false) {
  const request = refresh
    ? postRequest<ReadinessSnapshot>('/api/v1/operations/readiness/refresh')
    : getRequest<ReadinessSnapshot>('/api/v1/operations/readiness')
  return unwrap(request, '部署就绪检查')
}

export interface AccessLogPartitionStatus {
  phase?: 'COPYING' | 'READY' | 'CUTOVER' | 'ABORTED' | null
  partitionCount?: number
  defaultPartitionHasRows?: boolean
  rowsCopied?: number | null
  copyLag?: number | null
  copyEnabled?: boolean
  legacyTablePresent?: boolean
  updatedAt?: string | null
}

/** Read-only status; never invokes partition transition operations. */
export function getAccessLogPartitionStatus() {
  return unwrap(
    getRequest<AccessLogPartitionStatus>('/api/v1/operations/access-log-partition/status'),
    '访问日志分区状态查询',
  )
}

export interface OperationsHistorySample {
  sampledAt: string
  readinessCheckedAt: string
  mode: ReadinessSnapshot['mode']
  criticalFailures: number
  heapUsedBytes: number
  heapMaxBytes: number
  scanTotalBytes: number | null
  storageOpenNodes: number | null
  storageWaiting: number | null
}

export interface OperationsHistory {
  enabled: boolean
  hours: number
  bucketSeconds: number
  from: string
  to: string
  samples: OperationsHistorySample[]
}

export function getOperationsHistory(hours: 24 | 168, signal?: AbortSignal) {
  return unwrap(getRequest<OperationsHistory>('/api/v1/operations/history', { params: { hours }, signal, skipGlobalError: true }), '运维历史查询')
}

export interface NginxConfigStatus {
  enabled: boolean
  state: 'DISABLED' | 'UNSUPPORTED_PLATFORM' | 'NOT_CONFIGURED' | 'INSTALLATION_UNAVAILABLE' | 'AVAILABLE'
  configBytes: number | null
  configModifiedAt: string | null
}

export interface NginxConfigCheck {
  status: 'DISABLED' | 'UNSUPPORTED_PLATFORM' | 'NOT_CONFIGURED' | 'BUSY' | 'PASSED' | 'FAILED' | 'TIMEOUT' | 'INTERRUPTED' | 'UNAVAILABLE'
  checkedAt: string
  exitCode: number | null
}

export function getNginxConfigStatus(signal?: AbortSignal) {
  return unwrap(getRequest<NginxConfigStatus>('/api/v1/operations/nginx', { signal, skipGlobalError: true }), 'Nginx 配置状态')
}

export function checkNginxConfig(signal?: AbortSignal) {
  return unwrap(postRequest<NginxConfigCheck>('/api/v1/operations/nginx/check', undefined, { signal, retry: false, skipGlobalError: true }), 'Nginx 配置校验')
}

export interface StorageCircuitEvent {
  circuitId: string
  sequence: number
  sourceKind: 'NAS' | 'HTTP' | 'NGINX' | 'OSS'
  nodeOrdinal: number
  eventType: 'OPENED' | 'PROBE_STARTED' | 'METADATA_ONLY' | 'RECOVERED'
  occurredAt: string
  collectedAt: string
}

export interface StorageCircuitHistory {
  enabled: boolean
  hours: number
  truncated: boolean
  events: StorageCircuitEvent[]
}

export function getStorageCircuitHistory(hours: 24 | 168, signal?: AbortSignal) {
  return unwrap(getRequest<StorageCircuitHistory>('/api/v1/operations/storage-circuit-history', { params: { hours }, signal, skipGlobalError: true }), '存储熔断历史')
}

export interface NginxRuntimeStatus {
  state: string
  observedAt: string
}

export interface NginxLogEntry {
  time: string
  method: string | null
  status: number | null
  requestSeconds: number | null
  severity: string | null
}

export interface NginxLogTail {
  state: string
  kind: 'access' | 'error'
  observedAt: string
  truncated: boolean
  omitted: number
  entries: NginxLogEntry[]
}

export function getNginxRuntime(signal?: AbortSignal) {
  return unwrap(getRequest<NginxRuntimeStatus>('/api/v1/operations/nginx/runtime', { signal, skipGlobalError: true }), 'Nginx 运行观察')
}

export function getNginxLogTail(kind: 'access' | 'error', signal?: AbortSignal) {
  return unwrap(getRequest<NginxLogTail>('/api/v1/operations/nginx/logs', { params: { kind, limit: 50 }, signal, skipGlobalError: true }), 'Nginx 脱敏日志')
}

export interface NginxVersion {
  state: string
  version: string | null
  observedAt: string
}

export interface NginxListenerHints {
  state: string
  ports: number[]
  observedAt: string
}

export function getNginxListenerHints(signal?: AbortSignal) {
  return unwrap(getRequest<NginxListenerHints>('/api/v1/operations/nginx/listener-hints', { signal, skipGlobalError: true }), 'Nginx 监听文本提示')
}

export function readNginxVersion(signal?: AbortSignal) {
  return unwrap(postRequest<NginxVersion>('/api/v1/operations/nginx/version', undefined, { signal, retry: false, skipGlobalError: true }), 'Nginx 安装程序版本')
}

export interface WindowsGatewayStatus {
  state: string
  serviceState: string
  listenerState: string
  ports: number[]
  observedAt: string
}

export function observeWindowsGateway(signal?: AbortSignal) {
  return unwrap(postRequest<WindowsGatewayStatus>('/api/v1/operations/nginx/windows-observation', undefined, { signal, retry: false, skipGlobalError: true }), 'Windows 网关运行核对')
}
