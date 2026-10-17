import { getRequest, postRequest } from '../index'

export interface OcrJob {
  id: string
  archiveId: number
  status: string
  plannedCount: number
  processedCount: number
  ocrSuccessCount: number
  failedCount: number
  cancelRequested: boolean
}

export interface OcrFact {
  scanId: number
  archiveId: number
  status: string
  confidence: number | null
  resultVersion: string | null
  contentHash: string | null
  engineVersion: string | null
  processedAt: string | null
  text: string | null
}

export function getOcrFact(scanId: number, full = false) {
  return getRequest<OcrFact>(`/api/v1/scan/${scanId}/ocr`, { params: { full } })
}

export function createOcrJob(archiveId: number, scanId?: number) {
  return postRequest<OcrJob>(`/api/v1/archive-records/${archiveId}/ocr/jobs`, {
    idempotencyKey: `ocr-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    scanId,
  })
}

export function getOcrJob(id: string) {
  return getRequest<OcrJob>(`/api/v1/ocr/jobs/${id}`)
}

export function getOcrJobs(archiveId: number) {
  return getRequest<OcrJob[]>(`/api/v1/archive-records/${archiveId}/ocr/jobs`)
}

export function cancelOcrJob(id: string) {
  return postRequest<OcrJob>(`/api/v1/ocr/jobs/${id}/cancel`)
}
