import type { createOcrStore } from './ocr-jobs'
import type { MockScanRecord } from './records'
import type { OcrBatch, OcrBatchPreview } from '@/api/modules/ocr-batches'

interface BatchWork {
  batch: OcrBatch
  key: string
  ids: number[]
  index: number
  children: string[]
  nextAt: number
}

interface Reply<T> {
  code: number
  message: string
  data?: T
}

const ok = <T>(data: T): Reply<T> => ({ code: 200, message: '模拟操作成功', data })
const error = <T>(code: number, message: string): Reply<T> => ({ code, message })

export function createOcrBatchStore(scans: readonly MockScanRecord[], ocr: ReturnType<typeof createOcrStore>) {
  const batches: BatchWork[] = []
  let nextId = 1

  function scope(body: Record<string, unknown>) {
    const { afterId, upperId, maxPages, idempotencyKey } = body ?? {}
    if (typeof afterId !== 'number' || !Number.isSafeInteger(afterId) || afterId < 0
      || typeof upperId !== 'number' || !Number.isSafeInteger(upperId) || upperId < 0
      || typeof maxPages !== 'number' || !Number.isSafeInteger(maxPages) || maxPages < 1 || maxPages > 10000
      || typeof idempotencyKey !== 'string' || !/^[\w.:-]{1,128}$/.test(idempotencyKey)) { return undefined }
    const upper = upperId || Math.max(0, ...scans.map(scan => scan.id))
    if (upper < afterId) { return undefined }
    const ids = scans.filter(scan => scan.id > afterId && scan.id <= upper && scan.archiveId && scan.uploadFlag && scan.btype === 0)
      .map(scan => scan.id).sort((a, b) => a - b)
    return { afterId, upperId: upper, maxPages, key: idempotencyKey, ids }
  }

  function advance(now: number) {
    for (const work of batches) {
      const { batch } = work
      if (!['RUNNING', 'PAUSED'].includes(batch.status)) { continue }
      const children = work.children.map(id => ocr.job(id, now)).filter(job => job !== undefined)
      batch.processedCount = children.reduce((sum, job) => sum + job.processedCount, 0)
      batch.successCount = children.reduce((sum, job) => sum + job.ocrSuccessCount, 0)
      batch.failedCount = children.reduce((sum, job) => sum + job.failedCount, 0)
      batch.reviewRequiredCount = work.children.reduce((sum, id) => sum + (ocr.items(id, now)?.filter(item => item.status === 'SUGGESTED').length ?? 0), 0)
      if (batch.status === 'PAUSED' || children.some(job => ['PENDING', 'RUNNING'].includes(job.status))) { continue }
      if (work.index >= work.ids.length) {
        batch.status = batch.failedCount ? 'PARTIAL_SUCCESS' : 'SUCCESS'
        batch.reasonCode = null
        continue
      }
      if (now < work.nextAt) { continue }
      const scanId = work.ids[work.index]
      const scan = scans.find(item => item.id === scanId)
      if (!scan?.archiveId || !scan.uploadFlag || scan.btype !== 0) {
        batch.cursorId = scanId
        work.index++
        continue
      }
      const result = ocr.create(scan.archiveId, { scanId, idempotencyKey: `${batch.id}-${scanId}` }, now)
      if (!result.data) {
        batch.status = 'PAUSED'
        batch.reasonCode = 'AUTH_OR_QUEUE_BLOCKED'
        continue
      }
      work.children.push(result.data.id)
      batch.dispatchedCount++
      batch.cursorId = scanId
      work.index++
      work.nextAt = now + 1000
    }
  }

  function preview(body: Record<string, unknown>): Reply<OcrBatchPreview> {
    const selected = scope(body)
    if (!selected) { return error(400, '历史批次范围无效') }
    return ok({
      eligibleInLimit: Math.min(selected.ids.length, selected.maxPages),
      upperId: selected.upperId,
      truncated: selected.ids.length > selected.maxPages,
      readiness: 'READY',
      autoAvailable: false,
      policyChecksum: '',
      autoTypes: [],
    })
  }

  function create(body: Record<string, unknown>, now = Date.now()): Reply<OcrBatch> {
    const selected = scope(body)
    if (!selected) { return error(400, '历史批次范围无效') }
    if (body.autoApply !== false) { return error(409, '演示环境没有自动归类验收证据') }
    advance(now)
    const prior = batches.find(work => work.key === selected.key)
    if (prior) {
      const { batch } = prior
      if (batch.afterId !== selected.afterId || (body.upperId !== 0 && batch.upperId !== selected.upperId) || batch.maxPages !== selected.maxPages) {
        return error(409, '幂等键已用于其他批次范围')
      }
      return ok({ ...batch })
    }
    if (batches.filter(work => ['RUNNING', 'PAUSED'].includes(work.batch.status)).length >= 10) { return error(429, '历史活动批次已达到上限 10') }
    const batch: OcrBatch = {
      id: `demo-batch-${nextId++}`,
      status: 'RUNNING',
      afterId: selected.afterId,
      upperId: selected.upperId,
      cursorId: selected.afterId,
      maxPages: selected.maxPages,
      dispatchedCount: 0,
      processedCount: 0,
      successCount: 0,
      failedCount: 0,
      autoApply: false,
      autoAppliedCount: 0,
      reviewRequiredCount: 0,
      reasonCode: null,
    }
    batches.unshift({ batch, key: selected.key, ids: selected.ids.slice(0, selected.maxPages), index: 0, children: [], nextAt: now + 1000 })
    return ok({ ...batch })
  }

  return {
    preview,
    create,
    list(after = '', now = Date.now()) {
      advance(now)
      const index = after ? batches.findIndex(work => work.batch.id === after) + 1 : 0
      return batches.slice(index, index + 20).map(work => ({ ...work.batch }))
    },
    get(id: string, now = Date.now()) {
      advance(now)
      const work = batches.find(item => item.batch.id === id)
      return work ? { ...work.batch } : undefined
    },
    children(id: string, after = '', now = Date.now()) {
      advance(now)
      const work = batches.find(item => item.batch.id === id)
      if (!work) { return undefined }
      const index = after ? work.children.indexOf(after) + 1 : 0
      return work.children.slice(index, index + 50)
    },
    action(id: string, action: string, now = Date.now()): Reply<OcrBatch> {
      advance(now)
      const work = batches.find(item => item.batch.id === id)
      if (!work) { return error(404, '历史批次不存在') }
      const { batch } = work
      if (action === 'pause' && batch.status === 'RUNNING') {
        batch.status = 'PAUSED'
        batch.reasonCode = 'USER_PAUSED'
      }
      else if (action === 'resume' && batch.status === 'PAUSED') {
        batch.status = 'RUNNING'
        batch.reasonCode = null
        work.nextAt = now + 1000
      }
      else if (action === 'cancel' && ['RUNNING', 'PAUSED'].includes(batch.status)) {
        for (const child of work.children) { ocr.cancel(child, now) }
        batch.status = 'CANCELLED'
        batch.reasonCode = null
      }
      else if (action !== 'cancel') {
        return error(409, '批次状态不支持此操作')
      }
      return ok({ ...batch })
    },
  }
}
