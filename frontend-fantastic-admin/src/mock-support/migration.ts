import type { MockScanRecord } from './records'

function hasValidShelf(scan: MockScanRecord) {
  return /^\d{4,}$/.test(String(scan.sjh ?? '').trim())
}

export function migrationSnapshot(scans: readonly MockScanRecord[], now = Date.now()) {
  const uploaded = scans.filter(scan => scan.uploadFlag !== 0)
  const migrated = uploaded.filter(scan => Boolean(scan.ossUrl))
  const unmigrated = uploaded.filter(scan => !scan.ossUrl)
  const waiting = unmigrated.filter(scan => !hasValidShelf(scan))
  const pending = unmigrated.filter((scan) => {
    if (!hasValidShelf(scan)) { return false }
    if (!scan.migrationStatus || ['not_migrated', 'waiting_sjh'].includes(scan.migrationStatus)) { return true }
    return scan.migrationStatus === 'retry_wait'
      && (!scan.migrationNextRetryAt || Date.parse(scan.migrationNextRetryAt) <= now)
  })
  const counts = new Map<string, number>()
  for (const scan of pending) {
    counts.set(scan.folder, (counts.get(scan.folder) ?? 0) + 1)
  }
  const folders = [...counts].map(([folder, cnt]) => ({ folder, cnt })).sort((a, b) => a.folder.localeCompare(b.folder))
  return {
    pending,
    waiting,
    folders,
    statistics: {
      totalCount: uploaded.length,
      migratedCount: migrated.length,
      pendingCount: pending.length,
      waitingSjhCount: waiting.length,
      failedCount: unmigrated.filter(scan => scan.migrationStatus === 'failed').length,
      retryWaitCount: unmigrated.filter(scan => scan.migrationStatus === 'retry_wait').length,
      migratingCount: unmigrated.filter(scan => scan.migrationStatus === 'migrating').length,
      percentage: uploaded.length ? Math.round(migrated.length * 10000 / uploaded.length) / 100 : 0,
    },
  }
}
