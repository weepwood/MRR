import { getRequest } from '../index'

export interface DbBrowseColumnView {
  name: string
  label: string
  type: 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'DATETIME' | 'DATE' | 'JSON' | 'BINARY'
  filterable: boolean
  orderable: boolean
  mask: string
  allowedOps: string[]
}

export interface DbBrowseTableSummary {
  tableKey: string
  label: string
  description: string
  personalData: boolean
  requireFilter: boolean
  estimatedRows: number | null
  estimated: boolean
  columnCount: number
}

export interface DbBrowseTableSchema {
  tableKey: string
  label: string
  personalData: boolean
  requireFilter: boolean
  keyColumns: string[]
  maxPageSize: number
  columns: DbBrowseColumnView[]
  omittedColumnCount: number
}

export interface DbBrowsePage {
  tableKey: string
  columns: DbBrowseColumnView[]
  rows: Array<Record<string, unknown>>
  page: number
  size: number
  hasMore: boolean
  nextCursor: string | null
  estimatedRows: number | null
  estimated: boolean
  omittedColumnCount: number
  truncatedColumns: string[]
  tookMs: number
}

export interface DbBrowseRowsParams {
  filter?: string
  orderBy?: string
  order?: 'asc' | 'desc'
  page: number
  size: number
  cursor?: string
}

const BASE = '/api/v1/system/database/browse'

export async function getBrowseTables(): Promise<DbBrowseTableSummary[]> {
  const result = await getRequest<DbBrowseTableSummary[]>(`${BASE}/tables`)
  return result.data ?? []
}

export async function getBrowseSchema(tableKey: string): Promise<DbBrowseTableSchema | undefined> {
  const result = await getRequest<DbBrowseTableSchema>(`${BASE}/tables/${encodeURIComponent(tableKey)}`)
  return result.data
}

export async function getBrowseRows(tableKey: string, params: DbBrowseRowsParams): Promise<DbBrowsePage | undefined> {
  const result = await getRequest<DbBrowsePage>(`${BASE}/tables/${encodeURIComponent(tableKey)}/rows`, { params })
  return result.data
}
