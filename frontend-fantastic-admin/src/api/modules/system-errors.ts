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
  /** 最近 24 小时发生次数（最多统计最近 50 次）。 */
  recent24hCount?: number
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
  /** 近 24 小时仍活跃的 ERROR 错误组数，供菜单角标使用。 */
  recentErrorGroups?: number
}

export interface SystemErrorQuery {
  page: number
  size: number
  keyword?: string
  level?: string
  status?: string
  module?: string
  /** 按最后发生时间过滤的起始点，本地时区 ISO 格式（yyyy-MM-ddTHH:mm:ss）。 */
  since?: string
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
