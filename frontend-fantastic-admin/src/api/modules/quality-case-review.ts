import { getRequest, postRequest } from '../index'

export type ReviewAction = 'CONFIRM' | 'IGNORE' | 'REOPEN'
export interface QualityReview {
  id: string
  entityType: string
  entityId: string
  checkCode: string
  issueId: string
  runId: string
  status: string
  revision: number
  actorId: string
  updatedAt: string
}
export interface ReviewEvent {
  revision: number
  issueId: string
  runId: string
  status: string
  reason: string
  actorId: string
  occurredAt: string
}
export interface ReviewContext {
  enabled: boolean
  latest: boolean
  reviewedThisSample: boolean
  review: QualityReview | null
  events: ReviewEvent[]
}
const base = '/api/v1/system/data-quality/case-reviews'
export function getQualityReview(issueId: number, signal?: AbortSignal) {
  return getRequest<ReviewContext>(`${base}/issue/${issueId}`, { signal, skipGlobalError: true })
}
export function listQualityReviews(before?: string, signal?: AbortSignal) {
  return getRequest<{ enabled: boolean, reviews: QualityReview[] }>(base, { params: { before }, signal, skipGlobalError: true })
}
export function getQualityReviewEvents(id: string, before = 0, signal?: AbortSignal) {
  return getRequest<ReviewEvent[]>(`${base}/${id}/events`, { params: { before }, signal, skipGlobalError: true })
}
export function decideQualityReview(issueId: number, expectedRevision: number, action: ReviewAction) {
  return postRequest<QualityReview>(`${base}/issue/${issueId}`, undefined, { params: { expectedRevision, action }, retry: false, skipGlobalError: true })
}

export interface QualityRecheck {
  caseId: string
  revision: number
  status: string
  observedAt: string
}
export function recheckQualityReview(id: string, signal?: AbortSignal) {
  return getRequest<QualityRecheck>(`${base}/${id}/recheck`, { signal, skipGlobalError: true })
}
