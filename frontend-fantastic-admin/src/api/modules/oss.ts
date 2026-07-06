import type { MigrationStatistics, MigrationLogRecord, PaginatedResult } from '../types'
import { getRequest, postRequest } from '../index'

export function uploadToOss(scanIds: number[]) {
  return postRequest('/api/v1/oss/upload', { scanIds })
}

export function uploadByBah(bah: string) {
  return postRequest(`/api/v1/oss/upload/bah/${bah}`)
}

export function uploadByFolder(folder: string) {
  return postRequest(`/api/v1/oss/upload/folder/${encodeURIComponent(folder)}`)
}

export function getOssUrl(scanId: number) {
  return getRequest(`/api/v1/oss/url/${scanId}`)
}

export function getMigrationStatistics() {
  return getRequest<MigrationStatistics>('/api/v1/oss/migration/statistics')
}

export function getPendingMigrations(params: { limit?: number, folder?: string } = {}) {
  return getRequest<{ list: import('../types').ScanRecord[], total: number }>('/api/v1/oss/migration/pending', { params })
}

export function getPendingFolders() {
  return getRequest<{ folder: string; cnt: number }[]>('/api/v1/oss/migration/pending-folders')
}

export function getMigrationLogs(params: { status?: string, page?: number, size?: number } = {}) {
  return getRequest<PaginatedResult<MigrationLogRecord>>('/api/v1/oss/migration/logs', { params })
}

export function createMigrationJob() {
  return postRequest('/api/v1/oss/migration/jobs')
}

export function getMigrationJob(id: number) {
  return getRequest(`/api/v1/oss/migration/jobs/${id}`)
}

export function getMigrationJobs(params: { page?: number, size?: number } = {}) {
  return getRequest('/api/v1/oss/migration/jobs', { params })
}
