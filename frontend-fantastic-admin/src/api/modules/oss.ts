import type {
  ApiResult,
  MigrationLogRecord,
  MigrationStatistics,
  OssUploadResult,
  PaginatedResult,
  ScanRecord,
} from '../types'
import { getRequest, postRequest } from '../index'

interface OssUploadBatchResult {
  results: OssUploadResult[]
  total?: number
  success?: number
  failed?: number
  waitingSjh?: number
  bah?: string
}

type OssUploadResponse = ApiResult<OssUploadBatchResult> & Partial<OssUploadBatchResult>

interface OssUrlResult {
  scanId: number
  ossUrl: string
}

export interface MigrationJob {
  id?: number
  status?: string
  mode?: 'pilot' | 'batch' | 'full' | string
  scopeValue?: string
  requestedCount?: number
  maxScanId?: number
  cancelRequested?: boolean
  totalCount?: number
  processedCount?: number
  failedCount?: number
  rate?: number
  errorMessage?: string
  createdBy?: string
  startedAt?: string
  completedAt?: string
  createdAt?: string
  updatedAt?: string
  reused?: boolean
}

export interface MigrationReadiness {
  ready: boolean
  ossConfigured: boolean
  sourcePathConfigured: boolean
  sourcePathReadable: boolean
  noActiveJob: boolean
  pendingCount: number
  sampleSize: number
  sampleReadableCount: number
  sampleMissingCount: number
  sampleInvalidCount: number
  recommendedMode?: 'pilot' | 'batch' | string
  recommendedAction?: string
  activeJob?: MigrationJob
  warnings: string[]
}

export interface MigrationJobPayload {
  mode: 'pilot' | 'batch' | 'full'
  limit?: number
  folder?: string
  confirmation?: string
}

export function uploadToOss(scanIds: number[]): Promise<OssUploadResponse> {
  return postRequest<OssUploadBatchResult, { scanIds: number[] }>('/api/v1/oss/upload', { scanIds }) as Promise<OssUploadResponse>
}

export function uploadByBah(bah: string): Promise<OssUploadResponse> {
  return postRequest<OssUploadBatchResult>(`/api/v1/oss/upload/bah/${bah}`) as Promise<OssUploadResponse>
}

export function getOssUrl(scanId: number) {
  return getRequest<OssUrlResult>(`/api/v1/oss/url/${scanId}`)
}

export function getMigrationStatistics() {
  return getRequest<MigrationStatistics>('/api/v1/oss/migration/statistics')
}

export function getMigrationReadiness(sampleSize = 100) {
  return getRequest<MigrationReadiness>('/api/v1/oss/migration/readiness', { params: { sampleSize } })
}

export function getPendingMigrations(params: { limit?: number, folder?: string } = {}) {
  return getRequest<{ list: ScanRecord[], total: number, limit?: number }>('/api/v1/oss/migration/pending', { params })
}

export function getPendingFolders() {
  return getRequest<{ folder: string, cnt: number }[]>('/api/v1/oss/migration/pending-folders')
}

export function getMigrationLogs(params: { status?: string, page?: number, size?: number } = {}) {
  return getRequest<PaginatedResult<MigrationLogRecord>>('/api/v1/oss/migration/logs', { params })
}

export function createMigrationJob(payload: MigrationJobPayload) {
  return postRequest<MigrationJob, MigrationJobPayload>('/api/v1/oss/migration/jobs', payload)
}

export function cancelMigrationJob(id: number) {
  return postRequest<MigrationJob>(`/api/v1/oss/migration/jobs/${id}/cancel`)
}

export function retryMigrationScans(scanIds: number[]) {
  return postRequest<{ updated: number }, { scanIds: number[] }>('/api/v1/oss/migration/retry', { scanIds })
}

export function getMigrationJob(id: number) {
  return getRequest<MigrationJob>(`/api/v1/oss/migration/jobs/${id}`)
}

export function getMigrationJobs(params: { page?: number, size?: number } = {}) {
  return getRequest<PaginatedResult<MigrationJob>>('/api/v1/oss/migration/jobs', { params })
}
