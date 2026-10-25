import { getRequest, postRequest } from '../index'

export interface OcrBatchRequest { afterId: number, upperId: number, maxPages: number, idempotencyKey: string, autoApply: boolean, policyChecksum?: string }
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
  autoAppliedCount: number
  reviewRequiredCount: number
  reasonCode: string | null
  caseMode?: boolean
  completedCases?: number
  currentCaseCode?: string | null
  currentCaseProcessed?: number
  currentCasePlanned?: number
  currentCaseContextCursor?: number
  currentCaseContextDone?: boolean
}
export interface OcrBatchPreview { eligibleInLimit: number, upperId: number, truncated: boolean, readiness: string, autoAvailable: boolean, policyChecksum: string, autoTypes: number[], firstCasePages?: number, caseReadiness?: string }
export interface OcrCase { id: string, archiveId: number, caseCode: string | null, status: string, plannedCount: number, processedCount: number, contextDone: boolean, contextCursor: number }
export interface OcrRevert { auditId: number, scanId: number, newType: number, typeVersion: number, eligible: boolean }
export const previewOcrBatch = (request: OcrBatchRequest) => postRequest<OcrBatchPreview>('/api/v1/ocr/batches/preview', request)
export const createOcrBatch = (request: OcrBatchRequest) => postRequest<OcrBatch>('/api/v1/ocr/batches', request)
export const listOcrBatches = (after = '') => getRequest<OcrBatch[]>('/api/v1/ocr/batches', { params: { after } })
export const actOnOcrBatch = (id: string, action: 'pause' | 'resume' | 'cancel') => postRequest<OcrBatch>(`/api/v1/ocr/batches/${id}/${action}`)
export const listOcrBatchJobs = (id: string, after = '') => getRequest<string[]>(`/api/v1/ocr/batches/${id}/jobs`, { params: { after } })
export const listOcrBatchCases = (id: string, after = '') => getRequest<OcrCase[]>(`/api/v1/ocr/batches/${id}/cases`, { params: { after } })
export const previewOcrRollback = (id: string, after = 0) => getRequest<OcrRevert[]>(`/api/v1/ocr/batches/${id}/rollback/preview`, { params: { after, limit: 50 } })
export const rollbackOcrBatchPage = (id: string, afterAuditId: number) => postRequest<{ reverted: number, skipped: number, nextAfterAuditId: number }>(`/api/v1/ocr/batches/${id}/rollback`, { afterAuditId, limit: 50 })
