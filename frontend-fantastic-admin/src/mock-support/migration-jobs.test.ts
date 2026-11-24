import { describe, expect, it } from 'vitest'
import { createMigrationJobs } from './migration-jobs'
import { createMigrationJournal } from './migration-journal'
import { createDemoRecords } from './records'

describe('demo migration execution', () => {
  it('scopes and limits work, progresses once per elapsed interval and updates logs', () => {
    const { scans } = createDemoRecords()
    const journal = createMigrationJournal(scans)
    const jobs = createMigrationJobs(scans, journal)
    const now = Date.now()
    const job = jobs.create({ mode: 'pilot', limit: 1, folder: scans[0].folder }, now)!
    expect(job.totalCount).toBe(1)
    expect(job.status).toBe('pending')
    expect(jobs.create({ mode: 'batch' }, now)?.reused).toBe(true)
    jobs.advance(now + 1000)
    expect(jobs.list()[0].status).toBe('running')
    expect(jobs.list()[0].processedCount).toBe(0)
    jobs.advance(now + 2000)
    expect(jobs.list()[0].status).toBe('completed')
    expect(jobs.list()[0].processedCount).toBe(1)
    expect(scans[0].ossUrl).toMatch(/^data:image/)
    expect(journal.list({ scanId: 1, status: 'success' })).toHaveLength(1)
    jobs.advance(now + 100000)
    expect(journal.list({ scanId: 1, status: 'success' })).toHaveLength(1)
  })

  it('cancels without processing further records and rejects empty scopes', () => {
    const { scans } = createDemoRecords()
    const journal = createMigrationJournal(scans)
    const jobs = createMigrationJobs(scans, journal)
    const now = Date.now()
    expect(jobs.create({ mode: 'batch', folder: 'unknown' }, now)).toBeUndefined()
    const job = jobs.create({ mode: 'batch', limit: 3 }, now)!
    jobs.advance(now + 2000)
    expect(jobs.cancel(job.id!, now + 2000)?.status).toBe('cancelled')
    const count = jobs.list()[0].processedCount
    jobs.advance(now + 100000)
    expect(jobs.list()[0].processedCount).toBe(count)
    expect(jobs.active()).toBeUndefined()
    expect(jobs.cancel(job.id!, now + 100000)).toBeUndefined()
  })
})
