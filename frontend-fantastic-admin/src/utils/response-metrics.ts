import type { FrontendResponseMetric } from '@/api/types'

const DEFAULT_BATCH_SIZE = 20
const DEFAULT_MAX_QUEUE_SIZE = 200
const DEFAULT_FLUSH_INTERVAL_MS = 5000
const DEFAULT_MAX_SEND_RETRIES = 2
const DEFAULT_RETRY_DELAY_MS = 500

interface ResponseMetricInput {
  requestId?: string
  endpointTemplate?: string
  method?: string
  status?: number
  businessCode?: number
  startedAt?: number
  now?: number
  serverDurationMs?: number
  occurredAt?: string
  retryCount?: number
  retryOutcome?: 'succeeded' | 'failed' | 'canceled'
}

export type ResponseMetricDropReason = 'overflow' | 'send-failed' | 'unload-failed' | 'disposed'

export interface ResponseMetricQueueStats {
  queued: number
  sent: number
  dropped: number
  failedAttempts: number
}

export interface ResponseMetricDropEvent {
  count: number
  totalDropped: number
  reason: ResponseMetricDropReason
}

export interface ResponseMetricQueueOptions {
  batchSize?: number
  maxQueueSize?: number
  flushIntervalMs?: number
  maxSendRetries?: number
  retryDelayMs?: number
  unloadSender?: (metrics: FrontendResponseMetric[]) => boolean
  onDrop?: (event: ResponseMetricDropEvent) => void
  installUnloadHandlers?: boolean
}

export interface ResponseMetricQueue {
  enqueue: (metric: FrontendResponseMetric) => void
  flush: () => Promise<void>
  flushOnUnload: () => void
  getStats: () => ResponseMetricQueueStats
  dispose: () => void
}

function roundDuration(value: number) {
  return Math.round(Math.max(0, value))
}

function normalizePositiveInteger(value: number | undefined, fallback: number): number {
  if (!Number.isFinite(value)) return fallback
  return Math.max(1, Math.trunc(value as number))
}

function sleep(delayMs: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, Math.max(0, delayMs)))
}

export function createResponseMetric(input: ResponseMetricInput): FrontendResponseMetric | null {
  const requestId = input.requestId?.trim()
  const endpointTemplate = input.endpointTemplate?.trim()
  if (!requestId || !endpointTemplate || input.startedAt === undefined) {
    return null
  }

  const now = input.now ?? performance.now()
  const status = input.status ?? 0
  const businessSucceeded = input.businessCode === undefined || input.businessCode === 200 || input.businessCode === 1

  return {
    requestId,
    routePattern: endpointTemplate,
    method: (input.method || 'GET').toUpperCase(),
    httpStatus: status,
    businessCode: input.businessCode,
    success: status >= 200 && status < 300 && businessSucceeded,
    clientDurationMs: roundDuration(now - input.startedAt),
    serverDurationMs: input.serverDurationMs === undefined ? undefined : roundDuration(input.serverDurationMs),
    occurredAt: input.occurredAt ?? new Date().toISOString(),
    retryCount: Math.max(0, Math.trunc(input.retryCount ?? 0)),
    retryOutcome: input.retryOutcome,
  }
}

export function createResponseMetricQueue(
  sender: (metrics: FrontendResponseMetric[]) => Promise<unknown>,
  options: ResponseMetricQueueOptions = {},
): ResponseMetricQueue {
  const batchSize = normalizePositiveInteger(options.batchSize, DEFAULT_BATCH_SIZE)
  const maxQueueSize = Math.max(batchSize, normalizePositiveInteger(options.maxQueueSize, DEFAULT_MAX_QUEUE_SIZE))
  const flushIntervalMs = normalizePositiveInteger(options.flushIntervalMs, DEFAULT_FLUSH_INTERVAL_MS)
  const maxSendRetries = Math.max(0, Math.trunc(options.maxSendRetries ?? DEFAULT_MAX_SEND_RETRIES))
  const retryDelayMs = normalizePositiveInteger(options.retryDelayMs, DEFAULT_RETRY_DELAY_MS)
  const installUnloadHandlers = options.installUnloadHandlers ?? true

  let metrics: FrontendResponseMetric[] = []
  let timer: ReturnType<typeof setTimeout> | undefined
  let pendingFlush: Promise<void> | undefined
  let disposed = false
  const stats = {
    sent: 0,
    dropped: 0,
    failedAttempts: 0,
  }

  function getStats(): ResponseMetricQueueStats {
    return {
      queued: metrics.length,
      sent: stats.sent,
      dropped: stats.dropped,
      failedAttempts: stats.failedAttempts,
    }
  }

  function recordDrop(count: number, reason: ResponseMetricDropReason) {
    if (count <= 0) return
    stats.dropped += count
    options.onDrop?.({ count, totalDropped: stats.dropped, reason })
  }

  function clearFlushTimer() {
    if (timer !== undefined) {
      clearTimeout(timer)
      timer = undefined
    }
  }

  function scheduleFlush() {
    if (!disposed && timer === undefined && metrics.length > 0) {
      timer = setTimeout(() => void flush(), flushIntervalMs)
    }
  }

  async function sendBatch(batch: FrontendResponseMetric[]): Promise<void> {
    for (let attempt = 0; attempt <= maxSendRetries; attempt += 1) {
      try {
        await sender(batch)
        stats.sent += batch.length
        return
      }
      catch {
        stats.failedAttempts += 1
        if (attempt >= maxSendRetries) {
          recordDrop(batch.length, 'send-failed')
          return
        }
        await sleep(Math.min(10_000, retryDelayMs * 2 ** attempt))
      }
    }
  }

  async function flush(): Promise<void> {
    if (disposed) return
    if (pendingFlush) return pendingFlush

    clearFlushTimer()
    if (!metrics.length) return

    pendingFlush = (async () => {
      while (!disposed && metrics.length > 0) {
        const batch = metrics.splice(0, batchSize)
        await sendBatch(batch)
      }
    })().finally(() => {
      pendingFlush = undefined
      scheduleFlush()
    })
    return pendingFlush
  }

  function enqueue(metric: FrontendResponseMetric) {
    if (disposed) {
      recordDrop(1, 'disposed')
      return
    }

    if (metrics.length >= maxQueueSize) {
      metrics.shift()
      recordDrop(1, 'overflow')
    }
    metrics.push(metric)

    if (metrics.length >= batchSize) {
      void flush()
      return
    }
    scheduleFlush()
  }

  function flushOnUnload() {
    clearFlushTimer()
    if (!metrics.length) return

    while (metrics.length > 0) {
      const batch = metrics.splice(0, batchSize)
      let accepted = false
      try {
        accepted = options.unloadSender?.(batch) === true
      }
      catch {
        accepted = false
      }

      if (accepted) stats.sent += batch.length
      else recordDrop(batch.length, 'unload-failed')
    }
  }

  const handlePageHide = () => flushOnUnload()
  const handleVisibilityChange = () => {
    if (typeof document !== 'undefined' && document.visibilityState === 'hidden') flushOnUnload()
  }

  if (installUnloadHandlers && typeof window !== 'undefined') {
    window.addEventListener('pagehide', handlePageHide)
    document.addEventListener('visibilitychange', handleVisibilityChange)
  }

  function dispose() {
    if (disposed) return
    disposed = true
    clearFlushTimer()
    if (installUnloadHandlers && typeof window !== 'undefined') {
      window.removeEventListener('pagehide', handlePageHide)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
    if (metrics.length > 0) recordDrop(metrics.length, 'disposed')
    metrics = []
  }

  return { enqueue, flush, flushOnUnload, getStats, dispose }
}
