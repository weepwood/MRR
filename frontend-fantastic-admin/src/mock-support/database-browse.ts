import type { createDemoRecords } from './records'
import type { DbBrowseColumnView, DbBrowsePage, DbBrowseTableSchema, DbBrowseTableSummary } from '@/api/modules/database-browse'
import { demoError, demoOk } from './response'

function column(name: string, label: string, type: DbBrowseColumnView['type'], filterable = false, mask = 'NONE'): DbBrowseColumnView {
  return { name, label, type, filterable, orderable: name === 'id', mask, allowedOps: filterable ? ['EQ'] : [] }
}

const id = column('id', '主键', 'NUMBER')
const bah = column('bah', '病案号', 'TEXT', true, 'CODE')
const schemas: Record<string, DbBrowseColumnView[]> = {
  patient: [id, bah, column('name', '姓名', 'TEXT', true, 'NAME'), column('department', '科室', 'TEXT', true)],
  scan: [id, bah, column('sjh', '上架号', 'TEXT', true, 'CODE'), column('btype', '业务类型', 'NUMBER', true), column('pages', '页数', 'NUMBER'), column('uploadflag', '上传标记', 'NUMBER', true), column('migration_status', '迁移状态', 'TEXT', true)],
}
const names: Record<string, string> = { patient: '病案主数据（合成）', scan: '影像记录（合成）' }
const omitted: Record<string, number> = { patient: 5, scan: 14 }
type Row = Record<string, string | number>
type Records = ReturnType<typeof createDemoRecords>
function maskCode(value: string) {
  return value.length > 4 ? value.slice(0, 2) + '*'.repeat(value.length - 4) + value.slice(-2) : '****'
}
export function createDemoBrowse(records: Records) {
  const cursors = new Map<string, string>()
  let sequence = 0
  function source(key: string): Row[] {
    if (key === 'patient') { return records.patients.map(row => ({ id: row.id, bah: row.bah, name: row.name, department: row.department })) }
    return records.scans.map(row => ({ id: row.id, bah: row.bah, sjh: row.sjh, btype: row.btype, pages: row.pages, uploadflag: row.uploadFlag, migration_status: row.migrationStatus }))
  }
  function schema(key: string): DbBrowseTableSchema | undefined {
    if (!Object.hasOwn(schemas, key)) { return undefined }
    return { tableKey: key, label: names[key], personalData: true, requireFilter: false, keyColumns: ['id'], maxPageSize: 50, columns: schemas[key], omittedColumnCount: omitted[key] }
  }
  function tables() {
    return demoOk(Object.keys(schemas).map((key): DbBrowseTableSummary => ({
      tableKey: key,
      label: names[key],
      description: '仅浏览共享合成样本，不连接实际数据库',
      personalData: true,
      requireFilter: false,
      estimatedRows: source(key).length,
      estimated: false,
      columnCount: schemas[key].length,
    })))
  }
  function rows(key: string, query: Record<string, unknown>) {
    const table = schema(key)
    if (!table) { return demoError(404, '未登记的演示数据表') }
    const size = Number(query.size ?? 50)
    const page = Number(query.page ?? 1)
    const order = String(query.order ?? 'desc')
    const filter = String(query.filter ?? '')
    if (!Number.isInteger(size) || size < 1 || size > 50 || !Number.isInteger(page) || page < 1 || page > 65
      || !['asc', 'desc'].includes(order) || (query.orderBy && query.orderBy !== 'id') || filter.length > 1024) { return demoError(400, '无效查询参数') }
    const clauses = filter ? filter.split(',') : []
    if (clauses.length > 5) { return demoError(400, '过滤条件过多') }
    const parsed: [string, string][] = []
    for (const clause of clauses) {
      const match = /^([a-z_]+):EQ:(.{1,128})$/.exec(clause)
      if (!match || !table.columns.some(col => col.name === match[1] && col.filterable)) { return demoError(400, '不支持的过滤条件') }
      parsed.push([match[1], match[2].replace(/%2C/gi, ',')])
    }
    const all = source(key)
    const matching = all.filter(row => parsed.every(([field, value]) => String(row[field]) === value))
      .sort((a, b) => order === 'asc' ? Number(a.id) - Number(b.id) : Number(b.id) - Number(a.id))
    // Demo cursor is scoped to the matching synthetic IDs, sort, size and previous last ID, not a production token.
    const cursorFor = (last: number, next: number) => JSON.stringify([key, order, size, matching.map(row => row.id), last, next])
    let offset = 0
    if (page > 1) {
      const preceding = (page - 1) * size
      const previous = matching[preceding - 1]
      if (!previous || typeof query.cursor !== 'string' || cursors.get(query.cursor) !== cursorFor(Number(previous.id), page)) { return demoError(3006, '演示分页游标无效，请重新查询') }
      offset = preceding
    }
    else if (query.cursor) { return demoError(3006, '演示分页游标无效') }
    const selected = matching.slice(offset, offset + size)
    const projected = selected.map((row) => {
      const safe: Record<string, unknown> = {}
      for (const col of table.columns) {
        const value = row[col.name]
        safe[col.name] = col.mask === 'CODE' ? maskCode(String(value)) : col.mask === 'NAME' ? '演示***' : value
      }
      return safe
    })
    const hasMore = offset + size < matching.length
    let nextCursor: string | null = null
    if (hasMore) {
      nextCursor = `demo-browse-${++sequence}`
      cursors.set(nextCursor, cursorFor(Number(selected[selected.length - 1].id), page + 1))
      if (cursors.size > 128) { cursors.delete(cursors.keys().next().value!) }
    }
    return demoOk<DbBrowsePage>({ tableKey: key, columns: table.columns, rows: projected, page, size, hasMore, nextCursor, estimatedRows: all.length, estimated: false, omittedColumnCount: table.omittedColumnCount, truncatedColumns: [], tookMs: 0 })
  }
  return { tables, schema: (key: string) => { const value = schema(key); return value ? demoOk(value) : demoError(404, '未登记的演示数据表') }, rows }
}
