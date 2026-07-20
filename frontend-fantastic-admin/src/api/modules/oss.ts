import type {
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
  bah?: string
}

interface OssUrlResult {
  scanId: number
  ossUrl: string
}

interface MigrationJob {
  id?: number
  status?: string
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
}

export function uploadToOss(scanIds: number[]) {
  return postRequest<OssUploadBatchResult, { scanIds: number[] }>('/api/v1/oss/upload', { scanIds })
}

export function uploadByBah(bah: string) {
  return postRequest<OssUploadBatchResult>(`/api/v1/oss/upload/bah/${bah}`)
}

export function uploadByFolder(folder: string) {
  return postRequest<OssUploadBatchResult>(`/api/v1/oss/upload/folder/${encodeURIComponent(folder)}`)
}

export function getOssUrl(scanId: number) {
  return getRequest<OssUrlResult>(`/api/v1/oss/url/${scanId}`)
}

export function getMigrationStatistics() {
  return getRequest<MigrationStatistics>('/api/v1/oss/migration/statistics')
}

export function getPendingMigrations(params: { limit?: number, folder?: string } = {}) {
  return getRequest<{ list: ScanRecord[], total: number }>('/api/v1/oss/migration/pending', { params })
}

export function getPendingFolders() {
  return getRequest<{ folder: string, cnt: number }[]>('/api/v1/oss/migration/pending-folders')
}

export function getMigrationLogs(params: { status?: string, page?: number, size?: number } = {}) {
  return getRequest<PaginatedResult<MigrationLogRecord>>('/api/v1/oss/migration/logs', { params })
}

export function createMigrationJob() {
  return postRequest<MigrationJob>('/api/v1/oss/migration/jobs')
}

export function getMigrationJob(id: number) {
  return getRequest<MigrationJob>(`/api/v1/oss/migration/jobs/${id}`)
}

export function getMigrationJobs(params: { page?: number, size?: number } = {}) {
  return getRequest<PaginatedResult<MigrationJob>>('/api/v1/oss/migration/jobs', { params })
}
