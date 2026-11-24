import type { MockScanRecord } from './records'
import type { OcrItem } from '@/api/modules/classification'
import type { OcrFact, OcrJob } from '@/api/modules/ocr'

interface Work {
  job: OcrJob
  ids: number[]
  key: string
  selected?: number
  nextAt: number
  items: OcrItem[]
}

const active = (job: OcrJob) => ['PENDING', 'RUNNING'].includes(job.status)

export function createOcrStore(scans: readonly MockScanRecord[]) {
  const jobs: Work[] = []
  const facts = new Map<number, OcrFact>()
  let nextId = 1

  function advance(now: number) {
    for (const work of jobs) {
      const { job } = work
      while (active(job) && now >= work.nextAt) {
        job.status = 'RUNNING'
        const scan = scans.find(row => row.id === work.ids[job.processedCount])
        if (!scan || scan.btype !== 0 || !scan.uploadFlag) {
          work.items[job.processedCount].status = 'SKIPPED'
        }
        else {
          const failed = scan.id % 11 === 0
          const low = scan.id % 4 === 0
          const status = failed ? 'FAILED' : low ? 'LOW_CONFIDENCE' : 'SUCCEEDED'
          facts.set(scan.id, {
            scanId: scan.id,
            archiveId: job.archiveId,
            status,
            confidence: failed ? null : low ? 0.62 : 0.96,
            resultVersion: failed ? null : `demo-ocr-${job.id}-${scan.id}`,
            contentHash: scan.checksumMd5,
            engineVersion: 'synthetic-demo-only',
            processedAt: new Date(work.nextAt).toISOString(),
            text: failed ? null : `合成影像 · 非真实病案\nP${scan.id}\nBAH ${scan.bah}`,
          })
          work.items[job.processedCount].status = failed ? 'FAILED' : low ? 'LOW_CONFIDENCE' : 'NO_MATCH'
          work.items[job.processedCount].failureCode = failed ? 'SOURCE_NOT_FOUND' : null
          if (failed) { job.failedCount++ }
          else { job.ocrSuccessCount++ }
        }
        job.processedCount++
        work.nextAt += 1000
        if (job.processedCount === job.plannedCount) {
          job.status = job.failedCount === job.plannedCount ? 'FAILED' : job.failedCount ? 'PARTIAL_SUCCESS' : 'SUCCESS'
        }
      }
    }
  }

  function create(archiveId: number, body: Record<string, unknown>, now = Date.now()): { code: number, message: string, data?: OcrJob } {
    advance(now)
    const key = body?.idempotencyKey
    const selected = body?.scanId
    if (!Number.isSafeInteger(archiveId) || archiveId <= 0 || typeof key !== 'string' || !/^[\w.:-]{1,128}$/.test(key)
      || (selected !== undefined && (typeof selected !== 'number' || !Number.isSafeInteger(selected) || selected <= 0))) {
      return { code: 400, message: 'OCR 任务参数无效' }
    }
    const prior = jobs.find(work => work.key === key)
    if (prior) {
      if (prior.job.archiveId !== archiveId || prior.selected !== selected) { return { code: 409, message: '幂等键已用于其他 OCR 范围' } }
      return { code: 200, message: '模拟操作成功', data: { ...prior.job } }
    }
    if (jobs.some(work => work.job.archiveId === archiveId && active(work.job))) { return { code: 409, message: '该病案已有活动 OCR 任务' } }
    const ids = scans.filter(scan => scan.archiveId === archiveId && scan.btype === 0 && scan.uploadFlag && (selected === undefined || selected === scan.id)).map(scan => scan.id)
    if (!ids.length) { return { code: 409, message: '没有关联校验通过的未分类有效影像' } }
    const job: OcrJob = {
      id: `demo-ocr-${nextId++}`,
      archiveId,
      status: 'PENDING',
      plannedCount: ids.length,
      processedCount: 0,
      ocrSuccessCount: 0,
      failedCount: 0,
      cancelRequested: false,
    }
    jobs.unshift({ job, ids, key, selected: selected as number | undefined, nextAt: now + 1000, items: ids.map(scanId => ({ scanId, status: 'PENDING', failureCode: null, suggestedType: null })) })
    return { code: 200, message: '模拟操作成功', data: { ...job } }
  }

  return {
    create,
    fact(scanId: number, full = false, now = Date.now()): OcrFact | undefined {
      advance(now)
      const scan = scans.find(row => row.id === scanId)
      if (!scan?.archiveId) { return undefined }
      const fact = facts.get(scanId)
      return fact
        ? { ...fact, text: full ? fact.text : null }
        : { scanId, archiveId: scan.archiveId, status: 'PENDING', confidence: null, resultVersion: null, contentHash: null, engineVersion: null, processedAt: null, text: null }
    },
    job(id: string, now = Date.now()) {
      advance(now)
      const job = jobs.find(work => work.job.id === id)?.job
      return job ? { ...job } : undefined
    },
    list(archiveId: number, now = Date.now()) {
      advance(now)
      return jobs.filter(work => work.job.archiveId === archiveId).map(work => ({ ...work.job }))
    },
    items(id: string, now = Date.now()) {
      advance(now)
      return jobs.find(work => work.job.id === id)?.items.map(item => ({ ...item }))
    },
    cancel(id: string, now = Date.now()) {
      advance(now)
      const work = jobs.find(item => item.job.id === id)
      if (!work) { return undefined }
      if (active(work.job)) {
        work.job.cancelRequested = true
        work.job.status = 'CANCELLED'
      }
      return { ...work.job }
    },
  }
}
