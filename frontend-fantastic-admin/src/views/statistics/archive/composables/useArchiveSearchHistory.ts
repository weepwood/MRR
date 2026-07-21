import { normalizeMedicalRecordCode } from '@/utils/medical-record-code'

export const ARCHIVE_SEARCH_HISTORY_STORAGE_KEY = 'MRR-ADMIN:archive-search-history:v1'
export const ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT = 'mrr:archive-search-history-updated'
export const ARCHIVE_SEARCH_HISTORY_DISPLAY_LIMIT = 20
export type ArchiveSearchHistoryStatus = 'success' | 'failure'

export interface ArchiveSearchHistoryItem {
  id?: number
  key: string
  bah: string
  sjh: string
  imageCount: number
  queryCount: number
  searchedAt: number
  status: ArchiveSearchHistoryStatus
  failureReason: string
  favorite: boolean
}

export interface AddArchiveSearchHistoryInput {
  bah?: string | null
  sjh?: string | null
  imageCount?: number | null
  status?: ArchiveSearchHistoryStatus
  failureReason?: string | null
}

function createHistoryKey(bah: string, sjh: string, status: ArchiveSearchHistoryStatus): string {
  return `${bah || 'none'}:${sjh || 'none'}:${status}`
}

function normalizeHistoryItem(value: unknown): ArchiveSearchHistoryItem | null {
  if (!value || typeof value !== 'object') {
    return null
  }

  const source = value as Record<string, unknown>
  const bah = normalizeMedicalRecordCode(source.bah)
  const sjh = normalizeMedicalRecordCode(source.sjh)
  const searchedAt = Number(source.searchedAt)
  const imageCount = Number(source.imageCount)
  const queryCount = Number(source.queryCount)
  const status = source.status === 'failure' ? 'failure' : 'success'

  if ((!bah && !sjh) || !Number.isSafeInteger(searchedAt) || searchedAt <= 0) {
    return null
  }

  const id = Number(source.id)
  return {
    ...(Number.isSafeInteger(id) && id > 0 ? { id } : {}),
    key: createHistoryKey(bah, sjh, status),
    bah,
    sjh,
    imageCount: Number.isFinite(imageCount) ? Math.max(0, Math.floor(imageCount)) : 0,
    queryCount: status === 'success' && Number.isFinite(queryCount) ? Math.max(1, Math.floor(queryCount)) : 0,
    searchedAt,
    status,
    failureReason: status === 'failure' ? String(source.failureReason || '查询失败') : '',
    favorite: source.favorite === true,
  }
}

function notifyArchiveSearchHistoryUpdated(items: ArchiveSearchHistoryItem[]): void {
  if (typeof window === 'undefined') {
    return
  }
  window.dispatchEvent(new CustomEvent(ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT, { detail: items }))
}

function writeArchiveSearchHistory(items: ArchiveSearchHistoryItem[]): ArchiveSearchHistoryItem[] {
  const seen = new Set<string>()
  const normalized = items
    .map(normalizeHistoryItem)
    .filter((item): item is ArchiveSearchHistoryItem => {
      if (!item || seen.has(item.key)) {
        return false
      }
      seen.add(item.key)
      return true
    })

  try {
    localStorage.setItem(ARCHIVE_SEARCH_HISTORY_STORAGE_KEY, JSON.stringify(normalized))
  }
  catch {
    // 本地存储不可用时不影响档案查询。
  }

  notifyArchiveSearchHistoryUpdated(normalized)
  return normalized
}

function persistArchiveSearchHistory(item: ArchiveSearchHistoryItem): void {
  void import('@/api/modules/archive-search-history')
    .then(({ createArchiveSearchHistory }) => createArchiveSearchHistory({
      bah: item.bah,
      sjh: item.sjh,
      success: item.status === 'success',
      imageCount: item.imageCount,
      queryCount: item.queryCount,
      failureReason: item.failureReason,
      favorite: item.favorite,
      searchedAt: new Date(item.searchedAt).toISOString().slice(0, 19),
    }).then(({ data }) => {
      const id = Number(data?.id)
      if (Number.isSafeInteger(id) && id > 0) {
        writeArchiveSearchHistory(readArchiveSearchHistory().map(history => history.key === item.key
          ? { ...history, id }
          : history))
      }
    }))
    .catch(() => {
      // 服务端暂不可用时保留本地缓存，避免影响病案查询。
    })
}

function toLocalHistoryItem(record: Record<string, unknown>): ArchiveSearchHistoryItem | null {
  const searchedAt = new Date(String(record.searchedAt || '')).getTime()
  return normalizeHistoryItem({
    ...record,
    status: record.success === false ? 'failure' : 'success',
    searchedAt,
  })
}

export async function loadArchiveSearchHistory(): Promise<ArchiveSearchHistoryItem[]> {
  try {
    const { getArchiveSearchHistory } = await import('@/api/modules/archive-search-history')
    const { data } = await getArchiveSearchHistory()
    if (!Array.isArray(data) || data.length === 0) {
      return readArchiveSearchHistory()
    }
    const localHistory = readArchiveSearchHistory()
    const serverHistory = data
      .map(item => toLocalHistoryItem(item as unknown as Record<string, unknown>))
      .filter((item): item is ArchiveSearchHistoryItem => Boolean(item))
    const localByKey = new Map(localHistory.map(item => [item.key, item]))
    const serverKeys = new Set(serverHistory.map(item => item.key))
    const merged = serverHistory.map((serverItem) => {
      const localItem = localByKey.get(serverItem.key)
      if (!localItem) {
        return serverItem
      }
      return {
        ...serverItem,
        ...(localItem.queryCount > serverItem.queryCount ? localItem : {}),
        id: serverItem.id ?? localItem.id,
        favorite: serverItem.favorite || localItem.favorite,
        queryCount: Math.max(serverItem.queryCount, localItem.queryCount),
      }
    })
    return writeArchiveSearchHistory([
      ...merged,
      ...localHistory.filter(item => !serverKeys.has(item.key)),
    ].sort((left, right) => right.searchedAt - left.searchedAt))
  }
  catch {
    return readArchiveSearchHistory()
  }
}

export function readArchiveSearchHistory(): ArchiveSearchHistoryItem[] {
  try {
    const raw = localStorage.getItem(ARCHIVE_SEARCH_HISTORY_STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    if (!Array.isArray(parsed)) {
      return []
    }

    const seen = new Set<string>()
    return parsed
      .map(normalizeHistoryItem)
      .filter((item): item is ArchiveSearchHistoryItem => {
        if (!item || seen.has(item.key)) {
          return false
        }
        seen.add(item.key)
        return true
      })
  }
  catch {
    return []
  }
}

export function addArchiveSearchHistory(input: AddArchiveSearchHistoryInput): ArchiveSearchHistoryItem[] {
  const bah = normalizeMedicalRecordCode(input.bah)
  const sjh = normalizeMedicalRecordCode(input.sjh)
  if (!bah && !sjh) {
    return readArchiveSearchHistory()
  }

  const status = input.status === 'failure' ? 'failure' : 'success'
  const key = createHistoryKey(bah, sjh, status)
  const existingItem = readArchiveSearchHistory().find(item => item.key === key)
  const nextItem: ArchiveSearchHistoryItem = {
    ...(existingItem?.id ? { id: existingItem.id } : {}),
    key,
    bah,
    sjh,
    imageCount: Number.isFinite(Number(input.imageCount))
      ? Math.max(0, Math.floor(Number(input.imageCount)))
      : 0,
    queryCount: status === 'success' ? (existingItem?.queryCount ?? 0) + 1 : 0,
    searchedAt: Date.now(),
    status,
    failureReason: status === 'failure' ? String(input.failureReason || '查询失败') : '',
    favorite: existingItem?.favorite ?? false,
  }

  const history = writeArchiveSearchHistory([
    nextItem,
    ...readArchiveSearchHistory().filter(item => item.key !== key),
  ])
  persistArchiveSearchHistory(nextItem)
  return history
}

export function removeArchiveSearchHistory(key: string): ArchiveSearchHistoryItem[] {
  return writeArchiveSearchHistory(readArchiveSearchHistory().filter(item => item.key !== key))
}

export function toggleArchiveSearchHistoryFavorite(key: string): ArchiveSearchHistoryItem[] {
  const previousHistory = readArchiveSearchHistory()
  const history = writeArchiveSearchHistory(previousHistory.map(item => item.key === key
    ? { ...item, favorite: !item.favorite }
    : item))
  const changedItem = history.find(item => item.key === key)
  if (changedItem) {
    if (!changedItem.id) {
      persistArchiveSearchHistory(changedItem)
    }
    else {
      void import('@/api/modules/archive-search-history')
        .then(({ updateArchiveSearchHistoryFavorite }) => updateArchiveSearchHistoryFavorite(changedItem.id!, changedItem.favorite))
        .catch(() => {
          writeArchiveSearchHistory(previousHistory)
        })
    }
  }
  return history
}

export function clearArchiveSearchHistory(): void {
  try {
    localStorage.removeItem(ARCHIVE_SEARCH_HISTORY_STORAGE_KEY)
  }
  catch {
    // 忽略受限浏览器环境中的本地存储异常。
  }
  notifyArchiveSearchHistoryUpdated([])
}
