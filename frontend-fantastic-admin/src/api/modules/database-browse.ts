import { getRequestErrorMessage } from '@/utils/request-error-message'
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

export interface BrowseErrorView {
  message: string
  featureDisabled: boolean
}

/**
 * 把请求层抛出的错误归一为面向用户的提示与「功能未开启」标记。
 *
 * <p>HTTP 非 2xx 时 api/index.ts 原样抛出 AxiosError，业务码只存在于
 * {@code error.response.data.code}；2xx 响应体里的业务错误则是带数字 code 的
 * BusinessRequestError。两种形态都必须识别，否则 3007 会被当成普通 404。</p>
 */
export function describeBrowseError(error: unknown): BrowseErrorView {
  const code = extractBrowseBusinessCode(error)
  return {
    message: browseErrorMessage(code, error),
    featureDisabled: code === 3007,
  }
}

function extractBrowseBusinessCode(error: unknown): number | undefined {
  if (typeof error !== 'object' || error === null) { return undefined }
  const candidate = error as {
    code?: unknown
    response?: { data?: { code?: unknown } }
    payload?: { code?: unknown }
  }
  const fromResponse = candidate.response?.data?.code
  if (typeof fromResponse === 'number') { return fromResponse }
  const fromPayload = candidate.payload?.code
  if (typeof fromPayload === 'number') { return fromPayload }
  return typeof candidate.code === 'number' ? candidate.code : undefined
}

/** 未命中的错误沿用项目统一的中文提示，不再把 Axios 原生英文报错直接抛给用户。 */
function browseErrorMessage(code: number | undefined, error: unknown): string {
  switch (code) {
    case 3003: return '该表必须提供过滤条件后才会返回数据'
    case 3004: return '查询超时，请缩小过滤范围后重试'
    case 3006: return '分页游标已失效，请从第一页重新查询'
    case 3007: return '该功能未开启，请联系管理员'
    default: return getRequestErrorMessage(error)
  }
}
