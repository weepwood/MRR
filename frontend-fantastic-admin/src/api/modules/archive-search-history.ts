import type { ArchiveSearchHistoryRecord } from '../types'
import { getRequest, postRequest, putRequest } from '../index'

const BASE_URL = '/api/v1/archive-search-history'

export function getArchiveSearchHistory(authSessionToken?: string) {
  return getRequest<ArchiveSearchHistoryRecord[]>(BASE_URL, { skipGlobalError: true, authSessionToken })
}

export function createArchiveSearchHistory(record: ArchiveSearchHistoryRecord, authSessionToken?: string) {
  return postRequest<ArchiveSearchHistoryRecord>(BASE_URL, record, { skipGlobalError: true, authSessionToken })
}

export function updateArchiveSearchHistoryFavorite(id: number, favorite: boolean, authSessionToken?: string) {
  return putRequest<string>(`${BASE_URL}/${id}/favorite`, { favorite }, { skipGlobalError: true, authSessionToken })
}
