/** HTTP and business-level access denial; transport failures are not authorization changes. */
export function isSessionAccessDenied(error: unknown) {
  const failure = error as { response?: { status?: number, data?: { code?: number } }, code?: number | string } | null
  const status = failure?.response?.status
  const code = failure?.response?.data?.code ?? failure?.code
  return status === 401 || status === 403 || code === 401 || code === 403 || code === 'UNAUTHORIZED' || code === 'FORBIDDEN'
}
