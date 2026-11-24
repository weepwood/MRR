import type { MockScanRecord } from './records'
import type { ArchiveBoxGroup, ArchiveBoxRecord, ArchiveBoxRecordPayload, ArchiveBoxStatus } from '@/api/modules/archive-boxes'

const statuses: ArchiveBoxStatus[] = ['NORMAL', 'MISSING', 'MISPLACED', 'CONFLICT', 'OTHER']

function clean(value: unknown) {
  return typeof value === 'string' ? value.trim() || undefined : undefined
}

function code(value: unknown) {
  const text = clean(value)
  return text && /^\d+$/.test(text) ? text.replace(/^0+(?=\d)/, '') : text
}

export function createArchiveBoxStore(scans: readonly MockScanRecord[]) {
  const records: ArchiveBoxRecord[] = scans.filter(scan => /^\d{4,}$/.test(scan.sjh)).slice(0, 24).map((scan, index) => {
    const status = statuses[index % statuses.length]
    const expectedBoxNo = `DEMO-BOX-${String(Math.floor(index / 4) + 1).padStart(3, '0')}`
    return {
      id: index + 1,
      bah: scan.bah,
      sjh: scan.sjh,
      boxNo: status === 'MISSING' ? undefined : status === 'MISPLACED' ? 'DEMO-BOX-099' : expectedBoxNo,
      expectedBoxNo,
      status,
      remark: '合成档案位置记录',
      createdAt: scan.uploadDate,
      updatedAt: scan.uploadDate,
    }
  })
  let nextId = records.length + 1

  function list(query: Record<string, unknown> = {}) {
    const keyword = clean(query.keyword)?.toLowerCase()
    const bah = code(query.bah)
    const sjh = code(query.sjh)
    const boxNo = clean(query.boxNo)
    const status = clean(query.status)?.toUpperCase()
    const sort = ['bah', 'sjh', 'boxNo', 'status', 'createdAt', 'updatedAt'].includes(String(query.sortBy))
      ? query.sortBy as keyof ArchiveBoxRecord
      : 'updatedAt'
    const direction = query.sortOrder === 'asc' ? 1 : -1
    return records.filter(row => (!keyword || [row.bah, row.sjh, row.boxNo, row.expectedBoxNo, row.remark].some(value => value?.toLowerCase().includes(keyword)))
      && (!bah || code(row.bah)?.includes(bah))
      && (!sjh || code(row.sjh)?.includes(sjh))
      && (!boxNo || row.boxNo === boxNo)
      && (!status || row.status === status))
      .sort((a, b) => direction * String(a[sort] ?? '').localeCompare(String(b[sort] ?? '')) || (b.id ?? 0) - (a.id ?? 0))
      .map(row => ({ ...row }))
  }

  function groups(keyword?: unknown) {
    const filter = clean(keyword)?.toLowerCase()
    const grouped = new Map<string, ArchiveBoxGroup>()
    for (const row of records) {
      if (!row.boxNo || (filter && !row.boxNo.toLowerCase().includes(filter))) { continue }
      const group = grouped.get(row.boxNo) ?? { boxNo: row.boxNo, recordCount: 0, abnormalCount: 0 }
      group.recordCount++
      if (row.status !== 'NORMAL') { group.abnormalCount++ }
      if (!group.updatedAt || (row.updatedAt ?? '') > group.updatedAt) { group.updatedAt = row.updatedAt }
      grouped.set(row.boxNo, group)
    }
    return [...grouped.values()].sort((a, b) => a.boxNo.localeCompare(b.boxNo))
  }

  function save(payload: ArchiveBoxRecordPayload, id?: number) {
    const current = id === undefined ? undefined : records.find(row => row.id === id)
    if (id !== undefined && !current) { throw new Error('未找到装箱记录') }
    const bah = code(payload?.bah)
    const sjh = code(payload?.sjh)
    const boxNo = clean(payload?.boxNo)
    const expectedBoxNo = clean(payload?.expectedBoxNo)
    const remark = clean(payload?.remark)
    const status = (clean(payload?.status)?.toUpperCase() ?? 'NORMAL') as ArchiveBoxStatus
    if (!statuses.includes(status)) { throw new Error('不支持的装箱状态') }
    if (!bah && !sjh) { throw new Error('病案号和上架号至少填写一项') }
    if (status !== 'MISSING' && !boxNo) { throw new Error('非缺失状态必须填写实际箱号') }
    if ([bah, sjh, boxNo, expectedBoxNo].some(value => (value?.length ?? 0) > 64) || (remark?.length ?? 0) > 1000) {
      throw new Error('装箱字段超出长度限制')
    }
    if (sjh && records.some(row => code(row.sjh) === sjh && row.id !== id)) { throw new Error('上架号已存在装箱记录') }
    const now = new Date().toISOString()
    const row = { id: id ?? nextId++, bah, sjh, boxNo, expectedBoxNo, remark, status, createdAt: current?.createdAt ?? now, updatedAt: now }
    if (current) { Object.assign(current, row) }
    else { records.push(row) }
    return { ...row }
  }

  return {
    list,
    groups,
    summary: () => ({
      totalRecords: records.length,
      totalBoxes: groups().length,
      missingCount: records.filter(row => row.status === 'MISSING').length,
      abnormalCount: records.filter(row => row.status !== 'NORMAL').length,
    }),
    get(id: number) {
      const row = records.find(item => item.id === id)
      return row ? { ...row } : undefined
    },
    byCode(value: string) {
      const normalized = code(value)
      return normalized ? list().filter(row => code(row.bah) === normalized || code(row.sjh) === normalized) : []
    },
    byBox(value: string) {
      return clean(value) ? list({ boxNo: value }) : []
    },
    save,
    remove(id: number) {
      const index = records.findIndex(row => row.id === id)
      if (index < 0) { return false }
      records.splice(index, 1)
      return true
    },
  }
}
