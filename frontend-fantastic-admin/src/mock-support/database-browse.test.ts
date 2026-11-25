import { describe, expect, it } from 'vitest'
import { createDemoBrowse } from './database-browse'
import { createDemoRecords } from './records'

describe('演示数据库只读浏览', () => {
  it('目录和表结构仅提供两个共享合成表，掩码投影不泄漏原始字段', () => {
    const records = createDemoRecords()
    const browse = createDemoBrowse(records)
    expect(browse.tables().data.map(row => row.tableKey)).toEqual(['patient', 'scan'])
    expect(browse.schema('__proto__').code).toBe(404)
    expect(browse.rows('unknown', {}).code).toBe(404)
    const result = browse.rows('patient', { size: 1 })
    expect(result.code).toBe(200)
    if (!('data' in result)) { throw new Error('Expected data') }
    const row = result.data.rows[0]
    expect(row).toEqual({ id: 32, bah: '26****31', name: '演示***', department: records.patients[31].department })
    expect(JSON.stringify(result)).not.toContain(records.patients[31].idCard)
    expect(result.data.estimatedRows).toBe(32)
    records.patients.pop()
    expect(browse.tables().data[0].estimatedRows).toBe(31)
  })
  it('沿受限游标翻页，拒绝不同表、页码、大小或匹配集合', () => {
    const browse = createDemoBrowse(createDemoRecords())
    const first = browse.rows('scan', { page: 1, size: 50 })
    if (!('data' in first)) { throw new Error('Expected data') }
    expect(first.data.rows).toHaveLength(50)
    expect(first.data.hasMore).toBe(true)
    const cursor = first.data.nextCursor
    expect(cursor).toMatch(/^demo-browse-\d+$/)
    const second = browse.rows('scan', { page: 2, size: 50, cursor })
    if (!('data' in second)) { throw new Error('Expected data') }
    expect(second.data.rows).toHaveLength(14)
    expect(second.data.hasMore).toBe(false)
    expect(new Set([...first.data.rows, ...second.data.rows].map(row => row.id)).size).toBe(64)
    expect(browse.rows('scan', { page: 2, size: 20, cursor }).code).toBe(3006)
    expect(browse.rows('scan', { page: 2, size: 50, cursor, filter: 'bah:EQ:26070000' }).code).toBe(3006)
    expect(browse.rows('patient', { page: 2, size: 50, cursor }).code).toBe(3006)
    expect(browse.rows('scan', { page: 2, size: 50 }).code).toBe(3006)
  })
  it('精确筛选合成数据并拒绝任意列、操作及范围参数', () => {
    const browse = createDemoBrowse(createDemoRecords())
    const result = browse.rows('scan', { filter: 'bah:EQ:26070000', size: 10 })
    if (!('data' in result)) { throw new Error('Expected data') }
    expect(result.data.rows.map(row => row.id)).toEqual([2, 1])
    expect(result.data.rows.every(row => row.bah === '26****00')).toBe(true)
    for (const query of [{ filter: 'bah:PREFIX:26' }, { filter: 'idcard:EQ:synthetic' }, { orderBy: 'folder' }, { size: 51 }, { page: 0 }]) {
      expect(browse.rows('scan', query).code).toBe(400)
    }
  })
})
