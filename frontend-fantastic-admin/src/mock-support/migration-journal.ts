import type { MockScanRecord } from './records'
import { resetDemoFailures, uploadDemoScans } from './migration'
import { demoRecords } from './records'

interface DemoMigrationEvent {
  id: number
  scanId: number
  localPath: string
  migrationStatus: string
  errorMessage?: string
  ossUrl?: string
  fileSize?: number
  checksumMd5?: string
  migratedAt?: string
  verifiedAt?: string
  createdAt: string
  updatedAt: string
}

function eventTime(value: string) {
  return new Date(value.replace(' ', 'T')).toISOString()
}

export function createMigrationJournal(scans: MockScanRecord[]) {
  const events: DemoMigrationEvent[] = []
  let nextId = 1

  function append(scan: MockScanRecord, status: string, timestamp: string, errorMessage?: string) {
    const succeeded = status === 'success'
    events.push({
      id: nextId++,
      scanId: scan.id,
      localPath: `DEMO:scan:${scan.id}`,
      migrationStatus: status,
      errorMessage,
      ossUrl: succeeded ? scan.ossUrl : undefined,
      fileSize: succeeded ? scan.fileSize : undefined,
      checksumMd5: succeeded ? scan.checksumMd5 : undefined,
      migratedAt: succeeded ? eventTime(scan.migratedAt ?? timestamp) : undefined,
      verifiedAt: succeeded && scan.migrationStatus === 'verified' ? timestamp : undefined,
      createdAt: timestamp,
      updatedAt: timestamp,
    })
  }

  for (const scan of scans) {
    if (!scan.uploadFlag) { continue }
    if (scan.ossUrl) {
      append(scan, 'success', eventTime(scan.migratedAt ?? scan.uploadDate))
    }
    else if (['failed', 'retry_wait', 'waiting_sjh'].includes(scan.migrationStatus)) {
      append(scan, scan.migrationStatus, eventTime(scan.uploadDate), scan.migrationErrorCode)
    }
  }

  return {
    list(query: { status?: unknown, scanId?: unknown } = {}) {
      return events.filter(event => (!query.status || event.migrationStatus === String(query.status))
        && (!query.scanId || event.scanId === Number(query.scanId)))
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id)
        .map(event => ({ ...event }))
    },
    upload(ids: number[], now = Date.now()) {
      const summary = uploadDemoScans(scans, ids, now)
      for (const result of summary.results) {
        const scan = scans.find(item => item.id === result.scanId)
        if (scan && ['success', 'retry_wait', 'waiting_sjh'].includes(result.status)) {
          append(scan, result.status, new Date(now).toISOString(), result.errorMessage)
        }
      }
      return summary
    },
    reset(ids: number[], now = Date.now()) {
      // A reset changes the current record, not historical outcomes.
      return resetDemoFailures(scans, ids, now)
    },
  }
}

export const demoMigrationJournal = createMigrationJournal(demoRecords.scans)
