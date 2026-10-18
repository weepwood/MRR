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
export const getRuntimeLogs = (params: Record<string, unknown>) => getRequest<{ list: RuntimeLogRow[], hasMore: boolean }>('/api/v1/runtime-logs', { params })
export const getRuntimeLogHealth = () => getRequest<Record<string, unknown>>('/api/v1/runtime-logs/health')
export const getRuntimeLogFiles = () => getRequest<{ fileName: string, bytes: number }[]>('/api/v1/runtime-logs/files')
export const importRuntimeLog = (fileName: string) => postRequest('/api/v1/runtime-logs/imports', { fileName })
