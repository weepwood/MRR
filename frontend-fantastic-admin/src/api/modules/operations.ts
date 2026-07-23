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
  mode: 'READ_WRITE' | 'READ_ONLY_DEGRADED'
  checkedAt: string
  checks: ReadinessCheck[]
}

async function unwrap<T>(request: Promise<ApiResult<T>>, action: string): Promise<T> {
  const response = await request
  if (response.data === undefined) {
    throw new Error(`${action}未返回数据`)
  }
  return response.data
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
