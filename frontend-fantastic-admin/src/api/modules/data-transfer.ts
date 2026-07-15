import type { AxiosProgressEvent } from 'axios'
import type { ApiResult } from '../types'
import api, { getRequest, postRequest } from '../index'

export type DataTransferEntityType = 'MR_STATISTICS' | 'MR_SCAN'
export type DataTransferDirection = 'IMPORT' | 'EXPORT'
export type DataTransferImportMode = 'SKIP_DUPLICATES' | 'UPSERT'

export interface DataTransferJob {
  id: number
  direction: DataTransferDirection
  entityType: DataTransferEntityType
  status: string
  importMode: DataTransferImportMode
  sourceType?: string
  totalFiles: number
  completedFiles: number
  totalRows: number
  processedRows: number
  validRows: number
  invalidRows: number
  insertedRows: number
  updatedRows: number
  skippedRows: number
  progress: number
  currentStage?: string
  currentFileNo?: number
  errorMessage?: string
  createdBy?: string
  createdAt: string
  startedAt?: string
  completedAt?: string
  heartbeatAt?: string
  updatedAt: string
  failedFiles?: number
}

export interface DataTransferFile {
  id: number
  jobId: number
  sequenceNo: number
  originalFilename: string
  downloadName?: string
  fileSize: number
  sha256?: string
  status: string
  totalRows: number
  processedRows: number
  validRows: number
  invalidRows: number
  insertedRows: number
  updatedRows: number
  skippedRows: number
  firstRecordId?: number
  lastRecordId?: number
  errorMessage?: string
  createdAt: string
  completedAt?: string
}

export interface DataTransferError {
  id: number
  jobId: number
  fileId?: number
  sourceRowNo?: number
  fieldName?: string
  errorCode: string
  errorMessage: string
  rawData?: string
  createdAt: string
}

export interface DataTransferJobDetail {
  job: DataTransferJob
  files: DataTransferFile[]
  errors: DataTransferError[]
}

export interface InboxImportRequest {
  entityType: DataTransferEntityType
  importMode: DataTransferImportMode
  filenames: string[]
}

export interface ExportRequest {
  entityType: DataTransferEntityType
  startId?: number
  endId?: number
  rowsPerPart?: number
}

export function getDataTransferJobs(limit = 50) {
  return getRequest<DataTransferJob[]>('/api/v1/data-transfer/jobs', { params: { limit } })
}

export function getDataTransferJob(jobId: number) {
  return getRequest<DataTransferJobDetail>(`/api/v1/data-transfer/jobs/${jobId}`)
}

export function getDataTransferInbox() {
  return getRequest<string[]>('/api/v1/data-transfer/inbox')
}

export function createUploadImport(
  entityType: DataTransferEntityType,
  importMode: DataTransferImportMode,
  files: File[],
  onUploadProgress?: (event: AxiosProgressEvent) => void,
) {
  const formData = new FormData()
  formData.append('entityType', entityType)
  formData.append('importMode', importMode)
  files.forEach(file => formData.append('files', file))
  return api.post('/api/v1/data-transfer/imports/upload', formData, {
    timeout: 0,
    onUploadProgress,
    skipResponseMetrics: true,
  }) as Promise<ApiResult<DataTransferJob>>
}

export function createInboxImport(request: InboxImportRequest) {
  return postRequest<DataTransferJob, InboxImportRequest>('/api/v1/data-transfer/imports/inbox', request)
}

export function createDataExport(request: ExportRequest) {
  return postRequest<DataTransferJob, ExportRequest>('/api/v1/data-transfer/exports', request)
}

export function executeDataTransfer(jobId: number) {
  return postRequest<void>(`/api/v1/data-transfer/jobs/${jobId}/execute`)
}

export function pauseDataTransfer(jobId: number) {
  return postRequest<void>(`/api/v1/data-transfer/jobs/${jobId}/pause`)
}

export function resumeDataTransfer(jobId: number) {
  return postRequest<void>(`/api/v1/data-transfer/jobs/${jobId}/resume`)
}

export function cancelDataTransfer(jobId: number) {
  return postRequest<void>(`/api/v1/data-transfer/jobs/${jobId}/cancel`)
}

export function retryDataTransfer(jobId: number) {
  return postRequest<void>(`/api/v1/data-transfer/jobs/${jobId}/retry`)
}

export function downloadDataTransferTemplate(entityType: DataTransferEntityType) {
  return api.get(`/api/v1/data-transfer/templates/${entityType}.csv`, {
    responseType: 'blob',
    skipResponseMetrics: true,
  }) as Promise<Blob>
}

export function downloadDataTransferFile(fileId: number) {
  return api.get(`/api/v1/data-transfer/files/${fileId}/download`, {
    responseType: 'blob',
    timeout: 0,
    skipResponseMetrics: true,
  }) as Promise<Blob>
}

export function downloadDataTransferErrors(jobId: number, fileId: number) {
  return api.get(`/api/v1/data-transfer/jobs/${jobId}/files/${fileId}/errors.csv.gz`, {
    responseType: 'blob',
    timeout: 0,
    skipResponseMetrics: true,
  }) as Promise<Blob>
}
