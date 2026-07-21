import type { ArchiveSearchHistoryRecord } from '../types'
import { getRequest, postRequest, putRequest } from '../index'

const BASE_URL = '/api/v1/archive-search-history'

export function getArchiveSearchHistory() {
  return getRequest<ArchiveSearchHistoryRecord[]>(BASE_URL, { skipGlobalError: true })
}

export function createArchiveSearchHistory(record: ArchiveSearchHistoryRecord) {
  return postRequest<ArchiveSearchHistoryRecord>(BASE_URL, record, { skipGlobalError: true })
}

export function updateArchiveSearchHistoryFavorite(id: number, favorite: boolean) {
  return putRequest<string>(`${BASE_URL}/${id}/favorite`, { favorite }, { skipGlobalError: true })
}
