import { describe, expect, it } from 'vitest'
import { createMigrationJournal } from './migration-journal'
import { createDemoRecords } from './records'

describe('demo migration journal', () => {
  it('initializes events only for known scans and their actual initial migration outcome', () => {
    const { scans } = createDemoRecords()
    const journal = createMigrationJournal(scans)
    expect(journal.list().length).toBeGreaterThan(0)
    for (const event of journal.list()) {
      const scan = scans.find(item => item.id === event.scanId)!
      expect(scan).toBeDefined()
      expect(event.migrationStatus === 'success').toBe(Boolean(scan.ossUrl))
      expect(event.ossUrl).toBe(scan.ossUrl)
    }
  })

  it('adds immutable actual outcomes, not duplicate skips or unknown IDs', () => {
    const { scans } = createDemoRecords()
    const journal = createMigrationJournal(scans)
    const before = journal.list().length
    const result = journal.upload([1, 1, 99999])
    expect(result.results.map(item => item.status)).toEqual(['success', 'skipped', 'failed'])
    expect(journal.list()).toHaveLength(before + 1)
    const event = journal.list({ scanId: 1, status: 'success' })[0]
    expect(event.ossUrl).toBe(scans[0].ossUrl)
    const failed = scans.find(scan => scan.migrationStatus === 'failed')!
    const previous = journal.list({ scanId: failed.id, status: 'failed' })[0]
    journal.reset([failed.id])
    expect(previous.migrationStatus).toBe('failed')
    expect(journal.list({ scanId: failed.id, status: 'failed' })[0]).toEqual(previous)
    expect(new Set(journal.list().map(item => item.id)).size).toBe(journal.list().length)
  })
})
