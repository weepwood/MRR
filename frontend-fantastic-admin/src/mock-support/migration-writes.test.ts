import { describe, expect, it } from 'vitest'
import { migrationSnapshot, resetDemoFailures, uploadDemoScans } from './migration'
import { createDemoRecords } from './records'

describe('demo migration writes', () => {
  it('persists success, skips repeats and rejects unknown IDs', () => {
    const { scans } = createDemoRecords()
    const before = migrationSnapshot(scans).statistics
    const result = uploadDemoScans(scans, [1, 1, 99999])
    expect(result.results.map(item => item.status)).toEqual(['success', 'skipped', 'failed'])
    expect(result.success).toBe(2)
    expect(result.failed).toBe(1)
    expect(scans[0].migrationStatus).toBe('migrated')
    expect(scans[0].ossUrl).toMatch(/^data:image\//)
    expect(migrationSnapshot(scans).statistics.migratedCount).toBe(before.migratedCount + 1)
  })

  it('resets only failed records once and preserves waiting and unuploaded states', () => {
    const { scans } = createDemoRecords()
    const failed = scans.find(scan => scan.migrationStatus === 'failed')!
    const waiting = scans.find(scan => scan.migrationStatus === 'waiting_sjh')!
    const retry = scans.find(scan => scan.migrationStatus === 'retry_wait')!
    expect(resetDemoFailures(scans, [failed.id, failed.id, waiting.id, retry.id])).toBe(1)
    expect(failed.migrationStatus).toBe('not_migrated')
    expect(failed.migrationAttempts).toBe(0)
    expect(waiting.migrationStatus).toBe('waiting_sjh')
    expect(uploadDemoScans(scans, [waiting.id]).waitingSjh).toBe(1)
    expect(uploadDemoScans(scans, [16]).results[0].status).toBe('skipped')
    expect(uploadDemoScans(scans, [retry.id]).results[0].status).toBe('retry_wait')
    expect(uploadDemoScans(scans, [retry.id]).results[0].status).toBe('skipped')
  })
})
