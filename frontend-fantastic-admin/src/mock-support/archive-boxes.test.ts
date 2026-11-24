import { describe, expect, it } from 'vitest'
import { createArchiveBoxStore } from './archive-boxes'
import { createDemoRecords } from './records'

describe('archive box demo', () => {
  it('seeds linked records, grouped counts and all anomaly states', () => {
    const { scans } = createDemoRecords()
    const store = createArchiveBoxStore(scans)
    const rows = store.list()
    expect(rows.length).toBeGreaterThan(0)
    expect(rows.every(row => scans.some(scan => scan.bah === row.bah && scan.sjh === row.sjh))).toBe(true)
    expect(new Set(rows.map(row => row.status)).size).toBe(5)
    expect(store.summary().totalRecords).toBe(rows.length)
    expect(store.groups().reduce((sum, row) => sum + row.recordCount, 0)).toBe(rows.filter(row => row.boxNo).length)
  })

  it('validates writes and updates detail, code lookup and group summary', () => {
    const store = createArchiveBoxStore(createDemoRecords().scans)
    const total = store.summary().totalRecords
    expect(() => store.save({ status: 'NORMAL' })).toThrow()
    expect(() => store.save({ bah: '99999990', status: 'NORMAL' })).toThrow()
    const record = store.save({ bah: '99999990', sjh: '99999991', status: 'MISSING' })
    expect(store.summary().totalRecords).toBe(total + 1)
    expect(store.byCode('99999991')).toEqual([record])
    expect(() => store.save({ sjh: '99999991', status: 'NORMAL', boxNo: 'DEMO-NEW' })).toThrow()
    const moved = store.save({ bah: '99999990', sjh: '99999991', status: 'NORMAL', boxNo: 'DEMO-NEW' }, record.id)
    expect(store.byBox('DEMO-NEW')).toEqual([moved])
    expect(store.groups().find(row => row.boxNo === 'DEMO-NEW')?.recordCount).toBe(1)
    expect(store.get(record.id!)).toEqual(moved)
    expect(store.remove(record.id!)).toBe(true)
    expect(store.get(record.id!)).toBeUndefined()
    expect(store.summary().totalRecords).toBe(total)
    expect(store.remove(record.id!)).toBe(false)
  })
})
