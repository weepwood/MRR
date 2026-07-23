import type { PaginatedResult } from '../types'
import { getRequest, postRequest } from '../index'

export type SystemErrorLevel = 'WARN' | 'ERROR'
export type SystemErrorStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'

export interface SystemErrorEvent {
  id: number
  errorId: string
  fingerprint: string
  level: SystemErrorLevel
  module?: string
  loggerName: string
  exceptionType?: string
  messageSummary: string
  stackTrace?: string
  requestId?: string
  threadName?: string
  firstSeenAt: string
  lastSeenAt: string
  occurrenceCount: number
  status: SystemErrorStatus
  acknowledgedBy?: string
  resolvedAt?: string
}

export interface SystemErrorOverview {
  totalGroups: number
  totalOccurrences: number
  openGroups: number
  acknowledgedGroups: number
  resolvedGroups: number
  errorGroups: number
  warnGroups: number
  recentOccurrences: number
}

export interface SystemErrorQuery {
  page: number
  size: number
  keyword?: string
  level?: string
  status?: string
  module?: string
}

export function searchSystemErrors(params: SystemErrorQuery) {
  return getRequest<PaginatedResult<SystemErrorEvent>>('/api/v1/system-errors', { params })
}

export function getSystemErrorOverview() {
  return getRequest<SystemErrorOverview>('/api/v1/system-errors/overview')
}

export function getSystemErrorDetail(id: number) {
  return getRequest<SystemErrorEvent>(`/api/v1/system-errors/${id}`)
}

export function updateSystemErrorStatus(id: number, status: SystemErrorStatus) {
  return postRequest<void, { status: SystemErrorStatus }>(`/api/v1/system-errors/${id}/status`, { status })
}
