import { describe, expect, it } from 'vitest'
import { createOcrStore } from './ocr-jobs'
import { createDemoRecords } from './records'

describe('demo OCR facts and jobs', () => {
  it('runs only linked unclassified pages, hides text by default and never changes type', () => {
    const { scans } = createDemoRecords()
    const store = createOcrStore(scans)
    const now = Date.now()
    expect(store.fact(3, false, now)?.status).toBe('PENDING')
    expect(store.create(2, { scanId: 1, idempotencyKey: 'wrong' }, now).code).toBe(409)
    const result = store.create(2, { idempotencyKey: 'demo' }, now)
    expect(result.data?.plannedCount).toBe(2)
    const id = result.data!.id
    expect(store.create(2, { idempotencyKey: 'demo' }, now).data?.id).toBe(id)
    expect(store.create(2, { idempotencyKey: 'duplicate' }, now).code).toBe(409)
    expect(store.job(id, now + 3000)?.status).toBe('SUCCESS')
    expect(store.fact(3, false, now + 3000)?.text).toBeNull()
    expect(store.fact(3, true, now + 3000)?.text).toContain('合成影像')
    expect(store.items(id, now + 3000)?.map(item => item.status)).toEqual(['NO_MATCH', 'LOW_CONFIDENCE'])
    expect(scans.find(scan => scan.id === 3)?.btype).toBe(0)
  })

  it('produces synthetic review suggestions without automatically changing medical type', () => {
    const { scans } = createDemoRecords()
    const store = createOcrStore(scans, true)
    const now = Date.now()
    const id = store.create(2, { idempotencyKey: 'review' }, now).data!.id
    const suggestion = store.suggestions(id, now + 3000)![0]
    expect(suggestion.reviewStatus).toBe('PENDING')
    expect(suggestion.autoEligible).toBe(false)
    expect(scans.find(scan => scan.id === suggestion.scanId)?.btype).toBe(0)
    expect(store.review(suggestion.id, 'ACCEPT', undefined, now + 3000).code).toBe(200)
    expect(store.review(suggestion.id, 'REJECT', undefined, now + 3000).code).toBe(409)
    expect(scans.find(scan => scan.id === suggestion.scanId)?.btype).toBe(suggestion.suggestedType)
  })

  it('人工拒绝和改类保留OCR事实，非法类型及重复审核不改变记录', () => {
    const { scans } = createDemoRecords()
    const store = createOcrStore(scans, true)
    const now = 10000
    const rejectJob = store.create(2, { scanId: 3, idempotencyKey: 'reject' }, now).data!
    const reject = store.suggestions(rejectJob.id, now + 2000)![0]
    const fact = store.fact(3, true, now + 2000)
    expect(store.review(reject.id, 'REJECT', undefined, now + 2000).code).toBe(200)
    expect(scans.find(row => row.id === 3)?.btype).toBe(0)
    expect(store.fact(3, true, now + 2000)).toEqual(fact)
    const overrideJob = store.create(2, { scanId: 3, idempotencyKey: 'override' }, now + 2000).data!
    const override = store.suggestions(overrideJob.id, now + 4000)![0]
    const newFact = store.fact(3, true, now + 4000)
    expect(store.review(override.id, 'OVERRIDE', 999, now + 4000).code).toBe(400)
    expect(store.review(override.id, 'UNKNOWN', undefined, now + 4000).code).toBe(400)
    expect(store.review(override.id, 'OVERRIDE', 15, now + 4000).code).toBe(200)
    expect(scans.find(row => row.id === 3)?.btype).toBe(15)
    expect(store.fact(3, true, now + 4000)).toEqual(newFact)
    expect(store.review(override.id, 'REJECT', undefined, now + 4000).code).toBe(409)
  })

  it('重新识别后旧建议不可修改分类，新建议仍可人工确认', () => {
    const { scans } = createDemoRecords()
    const store = createOcrStore(scans, true)
    const now = 10000
    const first = store.create(2, { scanId: 3, idempotencyKey: 'first' }, now).data!
    const old = store.suggestions(first.id, now + 2000)![0]
    const oldFact = store.fact(3, true, now + 2000)
    const second = store.create(2, { scanId: 3, idempotencyKey: 'second' }, now + 2000).data!
    const current = store.suggestions(second.id, now + 4000)![0]
    expect(store.fact(3, true, now + 4000)?.resultVersion).not.toBe(oldFact?.resultVersion)
    expect(store.suggestions(first.id, now + 4000)![0].reviewStatus).toBe('SUPERSEDED')
    expect(store.review(old.id, 'ACCEPT', undefined, now + 4000).code).toBe(409)
    expect(scans.find(row => row.id === 3)?.btype).toBe(0)
    expect(store.review(current.id, 'ACCEPT', undefined, now + 4000).code).toBe(200)
  })

  it('cancels queued work and demonstrates a failed page without fake text', () => {
    const { scans } = createDemoRecords()
    const store = createOcrStore(scans)
    const now = Date.now()
    const id = store.create(2, { idempotencyKey: 'cancel' }, now).data!.id
    expect(store.cancel(id, now)?.status).toBe('CANCELLED')
    expect(store.job(id, now + 5000)?.processedCount).toBe(0)
    const failed = store.create(6, { idempotencyKey: 'failure' }, now).data!.id
    expect(store.job(failed, now + 3000)?.status).toBe('PARTIAL_SUCCESS')
    expect(store.fact(11, true, now + 3000)?.text).toBeNull()
    expect(store.fact(11, true, now + 3000)?.status).toBe('FAILED')
    expect(store.fact(999999, true, now)).toBeUndefined()
  })
})
