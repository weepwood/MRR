import { describe, expect, it } from 'vitest'
import { createOcrBatchStore } from './ocr-batches'
import { createOcrStore } from './ocr-jobs'
import { createDemoRecords } from './records'

describe('demo historical OCR batches', () => {
  it('previews a bounded range and disallows automatic classification', () => {
    const { scans } = createDemoRecords()
    const store = createOcrBatchStore(scans, createOcrStore(scans))
    const body = { afterId: 0, upperId: 0, maxPages: 2, idempotencyKey: 'preview', autoApply: false }
    expect(store.preview(body).data).toMatchObject({ upperId: 64, eligibleInLimit: 2, truncated: true, autoAvailable: false })
    expect(store.create({ ...body, autoApply: true }).code).toBe(409)
    expect(store.preview({ ...body, maxPages: 0 }).code).toBe(400)
    expect(store.list()).toEqual([])
  })

  it('dispatches bounded child tasks, pauses discovery and resumes without duplicates', () => {
    const { scans } = createDemoRecords()
    const ocr = createOcrStore(scans)
    const store = createOcrBatchStore(scans, ocr)
    const now = Date.now()
    const body = { afterId: 0, upperId: 4, maxPages: 2, idempotencyKey: 'bounded', autoApply: false }
    const created = store.create(body, now).data!
    expect(store.create(body, now).data?.id).toBe(created.id)
    expect(store.create({ ...body, upperId: 3 }, now).code).toBe(409)
    expect(store.get(created.id, now + 1000)?.dispatchedCount).toBe(1)
    expect(store.action(created.id, 'pause', now + 1000).data?.status).toBe('PAUSED')
    expect(store.get(created.id, now + 5000)?.dispatchedCount).toBe(1)
    expect(store.action(created.id, 'resume', now + 5000).data?.status).toBe('RUNNING')
    store.get(created.id, now + 6000)
    const done = store.get(created.id, now + 8000)
    expect(done).toMatchObject({ status: 'SUCCESS', processedCount: 2, successCount: 2, cursorId: 4, autoAppliedCount: 0 })
    const ids = store.children(created.id, '', now + 8000)!
    expect(new Set(ids).size).toBe(2)
    expect(ocr.fact(3, true, now + 8000)?.text).toContain('合成影像')
    expect(scans.find(scan => scan.id === 3)?.btype).toBe(0)
  })

  it('cancels pending work and preserves already completed child facts', () => {
    const { scans } = createDemoRecords()
    const store = createOcrBatchStore(scans, createOcrStore(scans))
    const now = Date.now()
    const id = store.create({ afterId: 0, upperId: 12, maxPages: 4, idempotencyKey: 'cancel', autoApply: false }, now).data!.id
    store.get(id, now + 1000)
    expect(store.action(id, 'cancel', now + 1000).data?.status).toBe('CANCELLED')
    expect(store.get(id, now + 10000)?.dispatchedCount).toBe(1)
    expect(store.action(id, 'resume', now + 10000).code).toBe(409)
    expect(store.get('unknown')).toBeUndefined()
  })
})
