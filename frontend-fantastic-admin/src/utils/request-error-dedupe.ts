export interface ErrorDedupeOptions {
  ttlMs?: number
  maxEntries?: number
  cleanupIntervalMs?: number
  now?: () => number
}

export interface ErrorDedupeDecision {
  shouldNotify: boolean
  firstRequestId?: string
}

export interface ErrorDedupeCache {
  check: (key: string, requestId?: string) => ErrorDedupeDecision
  clear: () => void
  dispose: () => void
  size: () => number
}

interface ErrorDedupeEntry {
  shownAt: number
  firstRequestId?: string
}

const DEFAULT_TTL_MS = 2000
const DEFAULT_MAX_ENTRIES = 100

function sanitizeRequestId(value: string | undefined): string | undefined {
  const sanitized = value?.trim().replace(/[^a-zA-Z0-9._:-]/g, '').slice(0, 64)
  return sanitized || undefined
}

export function createErrorDedupeCache(options: ErrorDedupeOptions = {}): ErrorDedupeCache {
  const ttlMs = Math.max(1, options.ttlMs ?? DEFAULT_TTL_MS)
  const maxEntries = Math.max(1, Math.trunc(options.maxEntries ?? DEFAULT_MAX_ENTRIES))
  const cleanupIntervalMs = Math.max(1000, options.cleanupIntervalMs ?? ttlMs)
  const now = options.now ?? Date.now
  const entries = new Map<string, ErrorDedupeEntry>()

  function cleanupExpired(currentTime = now()) {
    for (const [key, entry] of entries) {
      if (currentTime - entry.shownAt >= ttlMs) entries.delete(key)
    }
  }

  function evictOldestIfNeeded() {
    while (entries.size >= maxEntries) {
      const oldestKey = entries.keys().next().value
      if (oldestKey === undefined) break
      entries.delete(oldestKey)
    }
  }

  function check(key: string, requestId?: string): ErrorDedupeDecision {
    const currentTime = now()
    cleanupExpired(currentTime)

    const existing = entries.get(key)
    if (existing) {
      return { shouldNotify: false, firstRequestId: existing.firstRequestId }
    }

    evictOldestIfNeeded()
    const firstRequestId = sanitizeRequestId(requestId)
    entries.set(key, { shownAt: currentTime, firstRequestId })
    return { shouldNotify: true, firstRequestId }
  }

  const timer = typeof setInterval === 'function'
    ? setInterval(() => cleanupExpired(), cleanupIntervalMs)
    : undefined

  function clear() {
    entries.clear()
  }

  function dispose() {
    if (timer !== undefined) clearInterval(timer)
    clear()
  }

  return {
    check,
    clear,
    dispose,
    size: () => entries.size,
  }
}
