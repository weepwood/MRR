import type { FrontendResponseMetric } from '@/api/types'

const BATCH_SIZE = 20
const FLUSH_INTERVAL_MS = 5000

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
}

export interface ResponseMetricQueue {
  enqueue: (metric: FrontendResponseMetric) => void
  flush: () => Promise<void>
  dispose: () => void
}

function roundDuration(value: number) {
  return Math.round(Math.max(0, value))
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
  }
}

export function createResponseMetricQueue(
  sender: (metrics: FrontendResponseMetric[]) => Promise<unknown>,
): ResponseMetricQueue {
  let metrics: FrontendResponseMetric[] = []
  let timer: ReturnType<typeof setTimeout> | undefined
  let pendingFlush: Promise<void> | undefined

  function clearFlushTimer() {
    if (timer !== undefined) {
      clearTimeout(timer)
      timer = undefined
    }
  }

  function scheduleFlush() {
    if (timer === undefined && metrics.length > 0) {
      timer = setTimeout(() => void flush(), FLUSH_INTERVAL_MS)
    }
  }

  async function flush(): Promise<void> {
    if (pendingFlush) {
      return pendingFlush
    }

    clearFlushTimer()
    const batch = metrics.splice(0, BATCH_SIZE)
    if (!batch.length) {
      return
    }

    pendingFlush = (async () => {
      let currentBatch = batch
      while (currentBatch.length > 0) {
        await Promise.resolve(sender(currentBatch)).catch(() => undefined)
        currentBatch = metrics.length >= BATCH_SIZE ? metrics.splice(0, BATCH_SIZE) : []
      }
    })().finally(() => {
      pendingFlush = undefined
      scheduleFlush()
    })
    return pendingFlush
  }

  function enqueue(metric: FrontendResponseMetric) {
    metrics.push(metric)
    if (metrics.length >= BATCH_SIZE) {
      void flush()
      return
    }
    scheduleFlush()
  }

  function dispose() {
    clearFlushTimer()
    metrics = []
  }

  return { enqueue, flush, dispose }
}
