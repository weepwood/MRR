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
  maintenance?: MaintenanceStatus
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
  counts: Record<string, number>
  active: number
  failed: number
}

export interface OperationsQuickLink {
  label: string
  path: string
}

export interface OperationsOverview {
  generatedAt: string
  readiness: ReadinessSnapshot
  maintenance: MaintenanceStatus
  runtime: OperationsRuntime
  taskSummary: OperationsTaskSummary
  recentServerErrors: number
  latestOperation: Record<string, unknown>
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
