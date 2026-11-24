import { describe, expect, it } from 'vitest'
import { createOcrStore } from './ocr-jobs'
import { createDemoRecords } from './records'

describe('demo classification review', () => {
  it('requires explicit review, preserves text and rejects repeated approval', () => {
    const { scans } = createDemoRecords()
    const store = createOcrStore(scans)
    const now = Date.now()
    const job = store.create(10, { scanId: 19, idempotencyKey: 'review' }, now).data!
    const suggestion = store.suggestions(job.id, now + 2000)![0]
    expect(suggestion.reviewStatus).toBe('PENDING')
    expect(suggestion.autoEligible).toBe(false)
    const scan = scans.find(row => row.id === 19)!
    const fact = store.fact(19, true, now + 2000)
    expect(scan.btype).toBe(0)
    expect(store.review(suggestion.id, { action: 'ACCEPT' }, now + 2000).code).toBe(200)
    expect(scan.btype).toBe(suggestion.suggestedType)
    expect(store.fact(19, true, now + 2000)).toEqual(fact)
    expect(store.suggestions(job.id, now + 2000)![0].reviewStatus).toBe('ACCEPTED')
    expect(store.review(suggestion.id, { action: 'REJECT' }, now + 2000).code).toBe(409)
  })

  it('supports rejection and override, checks type and stale facts', () => {
    const { scans } = createDemoRecords()
    const store = createOcrStore(scans)
    const now = Date.now()
    const jobs = [21, 23, 25].map(scanId => store.create(Math.ceil(scanId / 2), { scanId, idempotencyKey: `review-${scanId}` }, now).data!)
    const [reject, override, stale] = jobs.map(job => store.suggestions(job.id, now + 2000)![0])
    expect(store.review(reject.id, { action: 'REJECT' }, now + 2000).code).toBe(200)
    expect(scans.find(row => row.id === 21)?.btype).toBe(0)
    expect(store.review(override.id, { action: 'OVERRIDE', type: 999 }, now + 2000).code).toBe(400)
    expect(store.review(override.id, { action: 'OVERRIDE', type: 15 }, now + 2000).code).toBe(200)
    expect(scans.find(row => row.id === 23)?.btype).toBe(15)
    scans.find(row => row.id === 25)!.btype = 2
    expect(store.review(stale.id, { action: 'ACCEPT' }, now + 2000).code).toBe(409)
    expect(store.review(99999, { action: 'ACCEPT' }, now + 2000).code).toBe(404)
  })
})
