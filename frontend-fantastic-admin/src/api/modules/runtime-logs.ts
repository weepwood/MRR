import { getRequest, postRequest } from '../index'

export interface RuntimeLogRow {
  occurred_at: string
  event_id: string
  level: string
  logger_name: string
  request_id: string
  error_id: string
  message_summary: string
  stack_trace: string
}

/** 窗口内的问题概览：计数、趋势与仍在发生的错误组。 */
export interface RuntimeLogInsight {
  range: { from: string, to: string, bucket: 'hour' | 'day' }
  counts: { ERROR: number, WARN: number }
  trend: { bucketStart: string, error: number, warn: number }[]
  topErrors: {
    errorId: string
    level: string
    module?: string
    message: string
    /** 生命周期累计次数：窗口内发生过的组按它排序，不是窗口内频次。 */
    occurrenceCount: number
    lastSeenAt: string
    requestId?: string
    status: string
  }[]
  topModules: { module: string, occurrences: number, groups: number }[]
  unresolvedGroups: number
}

export const getRuntimeLogs = (params: Record<string, unknown>) => getRequest<{ list: RuntimeLogRow[], hasMore: boolean }>('/api/v1/runtime-logs', { params })
export const getRuntimeLogInsights = (params: Record<string, unknown>) => getRequest<RuntimeLogInsight>('/api/v1/runtime-logs/insights', { params })
export const getRuntimeLogHealth = () => getRequest<Record<string, unknown>>('/api/v1/runtime-logs/health')
export const getRuntimeLogFiles = () => getRequest<{ fileName: string, bytes: number }[]>('/api/v1/runtime-logs/files')
export const importRuntimeLog = (fileName: string) => postRequest('/api/v1/runtime-logs/imports', { fileName })
