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
