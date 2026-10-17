import { getRequest, postRequest } from '../index'

export interface OcrBatchRequest { afterId: number, upperId: number, maxPages: number, idempotencyKey: string, autoApply: boolean }
export interface OcrBatch {
  id: string
  status: string
  afterId: number
  upperId: number
  cursorId: number
  maxPages: number
  dispatchedCount: number
  processedCount: number
  successCount: number
  failedCount: number
  autoApply: boolean
  reasonCode: string | null
}
export interface OcrBatchPreview { eligibleInLimit: number, upperId: number, truncated: boolean, readiness: string }
export const previewOcrBatch = (request: OcrBatchRequest) => postRequest<OcrBatchPreview>('/api/v1/ocr/batches/preview', request)
export const createOcrBatch = (request: OcrBatchRequest) => postRequest<OcrBatch>('/api/v1/ocr/batches', request)
export const listOcrBatches = (after = '') => getRequest<OcrBatch[]>('/api/v1/ocr/batches', { params: { after } })
export const actOnOcrBatch = (id: string, action: 'pause' | 'resume' | 'cancel') => postRequest<OcrBatch>(`/api/v1/ocr/batches/${id}/${action}`)
export const listOcrBatchJobs = (id: string, after = '') => getRequest<string[]>(`/api/v1/ocr/batches/${id}/jobs`, { params: { after } })
