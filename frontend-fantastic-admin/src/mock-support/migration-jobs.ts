import type { MigrationJob, MigrationJobPayload } from '@/api/modules/oss'
import type { MockScanRecord } from './records'
import type { createMigrationJournal } from './migration-journal'
import { migrationSnapshot } from './migration'
import { demoMigrationJournal } from './migration-journal'
import { demoRecords } from './records'
import { boundedInt } from './response'

const interval = 1000

export function createMigrationJobs(scans: MockScanRecord[], journal: ReturnType<typeof createMigrationJournal>) {
  const jobs: MigrationJob[] = []
  let nextId = 1
  let work: { job: MigrationJob, ids: number[], nextAt: number } | undefined

  function advance(now = Date.now()) {
    if (!work || now < work.nextAt) { return }
    const { job, ids } = work
    if (job.status === 'pending') {
      job.status = 'running'
      job.startedAt = new Date(work.nextAt).toISOString()
      work.nextAt += interval
    }
    while (work && now >= work.nextAt) {
      const index = job.processedCount ?? 0
      const at = work.nextAt
      const result = journal.upload([ids[index]], at)
      job.processedCount = index + 1
      job.failedCount = (job.failedCount ?? 0) + result.results.filter(item => item.status !== 'success' && item.status !== 'skipped').length
      job.rate = Math.round(job.processedCount * 100 / ids.length)
      job.updatedAt = new Date(at).toISOString()
      if (job.processedCount === ids.length) {
        job.status = job.failedCount ? 'completed_with_errors' : 'completed'
        job.completedAt = job.updatedAt
        work = undefined
      }
      else {
        work.nextAt += interval
      }
    }
  }

  return {
    advance,
    active() {
      return work ? { ...work.job } : undefined
    },
    list() {
      return jobs.map(job => ({ ...job }))
    },
    create(payload: MigrationJobPayload, now = Date.now()) {
      advance(now)
      if (work) { return { ...work.job, reused: true } }
      const ids = migrationSnapshot(scans, now).pending
        .filter(scan => !payload.folder || scan.folder === payload.folder)
        .slice(0, payload.mode === 'full' ? scans.length : boundedInt(payload.limit, 500, 10000))
        .map(scan => scan.id)
      if (!ids.length) { return undefined }
      const job: MigrationJob = {
        id: nextId++,
        status: 'pending',
        mode: payload.mode,
        scopeValue: payload.folder,
        totalCount: ids.length,
        processedCount: 0,
        failedCount: 0,
        rate: 0,
        createdBy: 'admin',
        createdAt: new Date(now).toISOString(),
      }
      jobs.unshift(job)
      work = { job, ids, nextAt: now + interval }
      return { ...job }
    },
    cancel(id: number, now = Date.now()) {
      advance(now)
      if (!work || work.job.id !== id) { return undefined }
      const job = work.job
      job.status = 'cancelled'
      job.cancelRequested = true
      job.completedAt = new Date(now).toISOString()
      job.updatedAt = job.completedAt
      work = undefined
      return { ...job }
    },
  }
}

export const demoMigrationJobs = createMigrationJobs(demoRecords.scans, demoMigrationJournal)
