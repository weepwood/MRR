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
  generatedAt: string
  archiveCoverage: number
  ossCoverage: number
  missingSjh: number
  brokenLinks: number
  duplicateArchiveGroups: number
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

export function diagnoseImageSource(params: { bah?: string, sjh?: string, imageId?: number }) {
  return getRequest<ImageSourceDiagnosis>('/api/v1/operations/image-source', { params })
}

export function getIntegritySummary() {
  return getRequest<IntegritySummary>('/api/v1/operations/integrity')
}

export function getExportCenter(limit = 100) {
  return getRequest<ExportCenterJob[]>('/api/v1/operations/exports', { params: { limit } })
}

export function getPermissionMatrix(comparePrevious = true) {
  return getRequest<PermissionMatrix>('/api/v1/operations/permission-matrix', {
    params: { comparePrevious },
  })
}

export function savePermissionMatrixSnapshot(version?: string) {
  return postRequest<{ version: string, createdBy: string, saved: boolean }>(
    '/api/v1/operations/permission-matrix/snapshots',
    { version },
  )
}

export function getDeploymentReadiness(refresh = false) {
  return getRequest<ReadinessSnapshot>('/api/v1/operations/readiness', { params: { refresh } })
}
