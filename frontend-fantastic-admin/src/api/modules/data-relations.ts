import { getRequest, postRequest } from '../index'

export type RelationStatus = 'HEALTHY' | 'WARNING' | 'CRITICAL' | 'LEGACY'
export type ComparisonStatus = 'EXACT' | 'FORMAT_ONLY' | 'CONFLICT' | 'MISSING' | 'NO_CANONICAL'

export interface RelationCoverage {
  tableName: string
  label: string
  relation: string
  totalCount: number
  linkedCount: number
  missingCount: number
  coverage: number
  estimated: boolean
  source: string
  coverageIncluded: boolean
  status: RelationStatus
  estimatedMissingCount?: number
  totalBytes?: number
}

export interface QualityRun {
  id: number
  status: string
  triggeredBy?: string
  triggered_by?: string
  checkCount?: number
  check_count?: number
  totalIssues?: number
  total_issues?: number
  criticalCount?: number
  critical_count?: number
  warningCount?: number
  warning_count?: number
  startedAt?: string
  started_at?: string
  completedAt?: string
  completed_at?: string
  errorMessage?: string | null
  error_message?: string | null
}

export interface QualityCheck {
  checkCode?: string
  check_code?: string
  checkName?: string
  check_name?: string
  severity: 'CRITICAL' | 'WARNING'
  issueCount?: number
  issue_count?: number
  sampledCount?: number
  sampled_count?: number
  checkedAt?: string
  checked_at?: string
}

export interface DataRelationOverview {
  generatedAt: string
  archiveCount: number
  healthScore: number
  relations: RelationCoverage[]
  latestQualityRun: QualityRun | null
  relationChecks: QualityCheck[]
  notes: string[]
}

export interface ArchiveSearchResult {
  id: number
  bah?: string | null
  sjh?: string | null
  patientId?: string | null
  patientName?: string | null
  department?: string | null
  archiveDate?: string | null
  dischargeDate?: string | null
  archiveType?: string | null
  pageCount?: number | null
  scanCount?: number
  matchType: 'EXACT' | 'FORMAT_ONLY'
}

export interface FieldComparison {
  field: string
  canonicalValue?: unknown
  source: string
  sourceValue?: unknown
  status: ComparisonStatus
}

export interface ArchiveRelationDetail {
  archive: Record<string, unknown>
  statistics: Array<Record<string, unknown>>
  patients: Array<Record<string, unknown>>
  boxes: Array<Record<string, unknown>>
  scanSummary: Record<string, unknown>
  scanSamples: Array<Record<string, unknown>>
  migrationSummary: Record<string, unknown>
  comparisons: FieldComparison[]
  warnings: string[]
  readOnly: boolean
}

export interface DataQualitySummary {
  running: boolean
  enabled: boolean
  latestRun: QualityRun | null
  checks: QualityCheck[]
}

export interface DataQualityIssue {
  id: number
  runId?: number
  run_id?: number
  checkCode?: string
  check_code?: string
  checkName?: string
  check_name?: string
  severity: 'CRITICAL' | 'WARNING'
  entityType?: string
  entity_type?: string
  entityId?: string
  entity_id?: string
  bah?: string | null
  sjh?: string | null
  detail?: string
  detectedAt?: string
  detected_at?: string
}

export interface RepairPreview {
  issue: DataQualityIssue
  currentEntity: Record<string, unknown>
  candidateArchives: ArchiveSearchResult[]
  suggestedAction: string
  deterministic: boolean
  readOnly: boolean
  canApply: boolean
  reason: string
}

export function getDataRelationOverview() {
  return getRequest<DataRelationOverview>('/api/v1/system/data-relations/overview')
}

export function searchDataRelationArchives(type: string, value: string, limit = 20) {
  return getRequest<ArchiveSearchResult[]>('/api/v1/system/data-relations/archives/search', {
    params: { type, value, limit },
  })
}

export function getArchiveRelationDetail(archiveId: number) {
  return getRequest<ArchiveRelationDetail>(`/api/v1/system/data-relations/archives/${archiveId}`)
}

export function getDataQualitySummary() {
  return getRequest<DataQualitySummary>('/api/v1/system/data-quality/summary')
}

export function getDataQualityIssues(limit = 200) {
  return getRequest<DataQualityIssue[]>('/api/v1/system/data-quality/issues', {
    params: { limit },
  })
}

export function getDataQualityIssue(issueId: number) {
  return getRequest<DataQualityIssue>(`/api/v1/system/data-quality/issues/${issueId}`)
}

export function getDataQualityRepairPreview(issueId: number) {
  return getRequest<RepairPreview>(`/api/v1/system/data-quality/issues/${issueId}/repair-preview`)
}

export function runDataQualityChecks() {
  return postRequest<DataQualitySummary>('/api/v1/system/data-quality/run')
}
