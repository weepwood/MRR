import { postRequest } from '../index'

export interface QueryDiagnostic {
  steps: { code: string, status: string, message: string, durationMs: number, details: Record<string, unknown> }[]
  readOnly: boolean
  scope?: string
}
export const diagnoseQuery = (data: { bah?: string, sjh?: string, requestId?: string }) => postRequest<QueryDiagnostic>('/api/v1/operations/query-diagnostics', data)
