const INITIAL_RETRY_DELAY_MS = 2_000
const MAX_RETRY_DELAY_MS = 60_000

/** 指数退避：首次失败后 2 秒重试，最高每 60 秒重试一次。 */
export function getEnvironmentProbeRetryDelay(failureCount: number): number {
  const normalizedFailureCount = Number.isFinite(failureCount)
    ? Math.max(1, Math.floor(failureCount))
    : 1

  return Math.min(INITIAL_RETRY_DELAY_MS * 2 ** (normalizedFailureCount - 1), MAX_RETRY_DELAY_MS)
}
