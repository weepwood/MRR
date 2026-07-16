import { normalizeMedicalRecordCode } from '@/utils/medical-record-code'

export const ARCHIVE_SEARCH_HISTORY_STORAGE_KEY = 'MRR-ADMIN:archive-search-history:v1'
export const ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT = 'mrr:archive-search-history-updated'
export const ARCHIVE_SEARCH_HISTORY_LIMIT = 20

export interface ArchiveSearchHistoryItem {
  key: string
  bah: string
  sjh: string
  imageCount: number
  searchedAt: number
}

export interface AddArchiveSearchHistoryInput {
  bah?: string | null
  sjh?: string | null
  imageCount?: number | null
}

function createHistoryKey(bah: string, sjh: string): string {
  return `${bah || 'none'}:${sjh || 'none'}`
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

  if ((!bah && !sjh) || !Number.isSafeInteger(searchedAt) || searchedAt <= 0) {
    return null
  }

  return {
    key: createHistoryKey(bah, sjh),
    bah,
    sjh,
    imageCount: Number.isFinite(imageCount) ? Math.max(0, Math.floor(imageCount)) : 0,
    searchedAt,
  }
}

function notifyArchiveSearchHistoryUpdated(items: ArchiveSearchHistoryItem[]): void {
  if (typeof window === 'undefined') {
    return
  }
  window.dispatchEvent(new CustomEvent(ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT, { detail: items }))
}

function writeArchiveSearchHistory(items: ArchiveSearchHistoryItem[]): ArchiveSearchHistoryItem[] {
  const normalized = items
    .map(normalizeHistoryItem)
    .filter((item): item is ArchiveSearchHistoryItem => Boolean(item))
    .slice(0, ARCHIVE_SEARCH_HISTORY_LIMIT)

  try {
    localStorage.setItem(ARCHIVE_SEARCH_HISTORY_STORAGE_KEY, JSON.stringify(normalized))
  }
  catch {
    // 本地存储不可用时不影响档案查询。
  }

  notifyArchiveSearchHistoryUpdated(normalized)
  return normalized
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
      .slice(0, ARCHIVE_SEARCH_HISTORY_LIMIT)
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

  const key = createHistoryKey(bah, sjh)
  const nextItem: ArchiveSearchHistoryItem = {
    key,
    bah,
    sjh,
    imageCount: Number.isFinite(Number(input.imageCount))
      ? Math.max(0, Math.floor(Number(input.imageCount)))
      : 0,
    searchedAt: Date.now(),
  }

  return writeArchiveSearchHistory([
    nextItem,
    ...readArchiveSearchHistory().filter(item => item.key !== key),
  ])
}

export function removeArchiveSearchHistory(key: string): ArchiveSearchHistoryItem[] {
  return writeArchiveSearchHistory(readArchiveSearchHistory().filter(item => item.key !== key))
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
