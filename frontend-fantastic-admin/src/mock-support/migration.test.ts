import { describe, expect, it } from 'vitest'
import { createDemoClock } from './clock'
import { migrationSnapshot } from './migration'
import { createDemoRecords } from './records'

describe('demo migration statistics', () => {
  it('counts uploaded scans, not all scans, and separates OSS presence from upload', () => {
    const { scans } = createDemoRecords(createDemoClock(new Date(2026, 10, 24, 12)))
    const state = migrationSnapshot(scans, Date.parse('2026-11-24T12:00:00Z'))
    expect(state.statistics.totalCount).toBe(scans.filter(scan => scan.uploadFlag !== 0).length)
    expect(state.statistics.totalCount).toBeLessThan(scans.length)
    expect(state.statistics.migratedCount).toBe(scans.filter(scan => scan.uploadFlag !== 0 && scan.ossUrl).length)
    expect(state.pending.length).toBeGreaterThan(0)
    expect(state.waiting.length).toBeGreaterThan(0)
    expect(state.statistics.failedCount).toBeGreaterThan(0)
    expect(state.folders.reduce((sum, folder) => sum + folder.cnt, 0)).toBe(state.pending.length)
  })

  it('handles retry deadlines, invalid shelf codes and live edits', () => {
    const { scans } = createDemoRecords()
    const now = Date.parse('2026-11-24T12:00:00Z')
    const [scan] = scans
    Object.assign(scan, { uploadFlag: 1, ossUrl: undefined, sjh: '1234', migrationStatus: 'retry_wait', migrationNextRetryAt: new Date(now + 1000).toISOString() })
    expect(migrationSnapshot([scan], now).statistics.pendingCount).toBe(0)
    expect(migrationSnapshot([scan], now + 1000).statistics.pendingCount).toBe(1)
    expect(migrationSnapshot([scan], now + 1000).statistics.retryWaitCount).toBe(1)
    scan.sjh = '12A'
    expect(migrationSnapshot([scan], now + 1000).statistics.waitingSjhCount).toBe(1)
    expect(migrationSnapshot([scan], now + 1000).pending).toHaveLength(0)
    scan.ossUrl = 'synthetic'
    expect(migrationSnapshot([scan], now).statistics.migratedCount).toBe(1)
    scan.uploadFlag = 0
    expect(migrationSnapshot([scan], now).statistics.totalCount).toBe(0)
  })
})
