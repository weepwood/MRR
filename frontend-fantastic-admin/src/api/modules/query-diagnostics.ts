import { postRequest } from '../index'

export interface QueryDiagnostic {
  steps: { code: string, status: string, message: string, durationMs: number, details: Record<string, unknown> }[]
  readOnly: boolean
  scope?: string
}
export function diagnoseQuery(data: { bah: string, sjh: string, requestId: string }, signal: AbortSignal, authSessionToken: string) {
  return postRequest<QueryDiagnostic>('/api/v1/operations/query-diagnostics', data, {
    signal,
    authSessionToken,
    skipGlobalError: true,
  })
}
