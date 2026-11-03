import { useUserStore } from '@/store/modules/user'
import { AUTH_STORAGE_KEYS } from '@/utils/auth-storage'
import { normalizeMedicalRecordCode } from '@/utils/medical-record-code'

/** Legacy shared storage is deleted, never read or migrated to an arbitrary user. */
export const ARCHIVE_SEARCH_HISTORY_STORAGE_KEY = 'MRR-ADMIN:archive-search-history:v1'
export const ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT = 'mrr:archive-search-history-updated'
export const ARCHIVE_SEARCH_HISTORY_DISPLAY_LIMIT = 20
const MEMORY_LIMIT = 100
const MEMORY_MAX_AGE = 30 * 24 * 60 * 60 * 1000
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

interface HistorySession {
  userId: number
  token: string
  items: ArchiveSearchHistoryItem[]
  revision: number
  favoriteRequests: Map<string, symbol>
}
let activeSession: HistorySession | null = null

export function captureArchiveSearchHistorySession(): HistorySession | null {
  let storedToken: string | null | undefined
  try {
    localStorage.removeItem(ARCHIVE_SEARCH_HISTORY_STORAGE_KEY)
    storedToken = localStorage.getItem(AUTH_STORAGE_KEYS.token)
  }
  catch {
    // Verified in-memory sessions remain usable when browser storage is blocked.
  }
  const user = useUserStore()
  const userId = Number(user.profile.id)
  if (!user.isSessionVerified || !user.token || !Number.isSafeInteger(userId) || userId <= 0
    || (storedToken !== undefined && storedToken !== user.token)) {
    activeSession = null
    return null
  }
  if (activeSession?.userId !== userId || activeSession.token !== user.token) {
    activeSession = { userId, token: user.token, items: [], revision: 0, favoriteRequests: new Map() }
  }
  return activeSession
}

function isCurrent(session: HistorySession | null): session is HistorySession {
  return session !== null && captureArchiveSearchHistorySession() === session
}

function createHistoryKey(bah: string, sjh: string, status: ArchiveSearchHistoryStatus): string {
  return `${bah || 'none'}:${sjh || 'none'}:${status}`
}

function normalizeHistoryItem(value: unknown): ArchiveSearchHistoryItem | null {
  if (!value || typeof value !== 'object') { return null }
  const source = value as Record<string, unknown>
  const bah = normalizeMedicalRecordCode(source.bah)
  const sjh = normalizeMedicalRecordCode(source.sjh)
  const searchedAt = Number(source.searchedAt)
  const imageCount = Number(source.imageCount)
  const queryCount = Number(source.queryCount)
  const status = source.status === 'failure' ? 'failure' : 'success'
  if ((!bah && !sjh) || !Number.isSafeInteger(searchedAt) || searchedAt <= 0
    || searchedAt <= Date.now() - MEMORY_MAX_AGE || searchedAt > Date.now()) {
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
    failureReason: status === 'failure' ? '查询失败' : '',
    favorite: source.favorite === true,
  }
}

function normalizeItems(items: ArchiveSearchHistoryItem[]): ArchiveSearchHistoryItem[] {
  const seen = new Set<string>()
  return items.map(normalizeHistoryItem).filter((item): item is ArchiveSearchHistoryItem => {
    if (!item || seen.has(item.key)) { return false }
    seen.add(item.key)
    return true
  }).slice(0, MEMORY_LIMIT)
}

function notifyUpdated() {
  // Consumers re-read the current session; events never carry another session's data.
  if (typeof window !== 'undefined') { window.dispatchEvent(new Event(ARCHIVE_SEARCH_HISTORY_UPDATED_EVENT)) }
}

function writeHistory(session: HistorySession, items: ArchiveSearchHistoryItem[]): ArchiveSearchHistoryItem[] {
  if (!isCurrent(session)) { return [] }
  session.items = normalizeItems(items)
  session.revision++
  notifyUpdated()
  return session.items.map(item => ({ ...item }))
}

export function readArchiveSearchHistory(): ArchiveSearchHistoryItem[] {
  const session = captureArchiveSearchHistorySession()
  if (!session) { return [] }
  session.items = normalizeItems(session.items)
  return session.items.map(item => ({ ...item }))
}

function persistHistory(session: HistorySession, item: ArchiveSearchHistoryItem): void {
  void import('@/api/modules/archive-search-history').then(async ({ createArchiveSearchHistory }) => {
    if (!isCurrent(session)) { return }
    const { data } = await createArchiveSearchHistory({
      bah: item.bah,
      sjh: item.sjh,
      success: item.status === 'success',
      imageCount: item.imageCount,
      queryCount: item.queryCount,
      failureReason: item.failureReason,
      favorite: item.favorite,
      searchedAt: new Date(item.searchedAt).toISOString().slice(0, 19),
    }, session.token)
    const id = Number(data?.id)
    if (isCurrent(session) && Number.isSafeInteger(id) && id > 0) {
      writeHistory(session, session.items.map(history => history.key === item.key && history.searchedAt === item.searchedAt
        ? { ...history, id }
        : history))
    }
  }).catch(() => { /* History failures must not interrupt record queries. */ })
}

export async function loadArchiveSearchHistory(): Promise<ArchiveSearchHistoryItem[]> {
  const session = captureArchiveSearchHistorySession()
  if (!session) { return [] }
  const revision = session.revision
  try {
    const { getArchiveSearchHistory } = await import('@/api/modules/archive-search-history')
    if (!isCurrent(session)) { return [] }
    const { data } = await getArchiveSearchHistory(session.token)
    if (!isCurrent(session)) { return [] }
    // Keep newer local edits when a server snapshot began before them.
    if (session.revision !== revision || !Array.isArray(data)) { return readArchiveSearchHistory() }
    const serverHistory = data.map(record => normalizeHistoryItem({
      ...record,
      status: record.success === false ? 'failure' : 'success',
      searchedAt: new Date(String(record.searchedAt || '')).getTime(),
    })).filter((item): item is ArchiveSearchHistoryItem => Boolean(item))
    return writeHistory(session, serverHistory.sort((left, right) => right.searchedAt - left.searchedAt))
  }
  catch {
    return isCurrent(session) ? readArchiveSearchHistory() : []
  }
}

export function addArchiveSearchHistory(input: AddArchiveSearchHistoryInput, session = captureArchiveSearchHistorySession()): ArchiveSearchHistoryItem[] {
  if (!isCurrent(session)) { return [] }
  const bah = normalizeMedicalRecordCode(input.bah)
  const sjh = normalizeMedicalRecordCode(input.sjh)
  if (!bah && !sjh) { return readArchiveSearchHistory() }
  const status = input.status === 'failure' ? 'failure' : 'success'
  const key = createHistoryKey(bah, sjh, status)
  const existing = session.items.find(item => item.key === key)
  const item: ArchiveSearchHistoryItem = {
    ...(existing?.id ? { id: existing.id } : {}),
    key,
    bah,
    sjh,
    status,
    searchedAt: Date.now(),
    imageCount: Number.isFinite(Number(input.imageCount)) ? Math.max(0, Math.floor(Number(input.imageCount))) : 0,
    queryCount: status === 'success' ? (existing?.queryCount ?? 0) + 1 : 0,
    failureReason: status === 'failure' ? '查询失败' : '',
    favorite: existing?.favorite ?? false,
  }
  const items = writeHistory(session, [item, ...session.items.filter(history => history.key !== key)])
  persistHistory(session, item)
  return items
}

export function removeArchiveSearchHistory(key: string): ArchiveSearchHistoryItem[] {
  const session = captureArchiveSearchHistorySession()
  return session ? writeHistory(session, session.items.filter(item => item.key !== key)) : []
}

export function toggleArchiveSearchHistoryFavorite(key: string): ArchiveSearchHistoryItem[] {
  const session = captureArchiveSearchHistorySession()
  if (!session) { return [] }
  const previous = readArchiveSearchHistory()
  const items = writeHistory(session, previous.map(item => item.key === key ? { ...item, favorite: !item.favorite } : item))
  const changed = items.find(item => item.key === key)
  if (!changed) { return items }
  if (!changed.id) {
    persistHistory(session, changed)
    return items
  }
  const operation = Symbol(key)
  session.favoriteRequests.set(key, operation)
  void import('@/api/modules/archive-search-history').then(async ({ updateArchiveSearchHistoryFavorite }) => {
    if (isCurrent(session)) { await updateArchiveSearchHistoryFavorite(changed.id!, changed.favorite, session.token) }
  }).catch(() => {
    if (isCurrent(session) && session.favoriteRequests.get(key) === operation) {
      const favorite = previous.find(item => item.key === key)?.favorite ?? false
      writeHistory(session, session.items.map(item => item.key === key ? { ...item, favorite } : item))
    }
  }).finally(() => {
    if (session.favoriteRequests.get(key) === operation) { session.favoriteRequests.delete(key) }
  })
  return items
}

export function clearArchiveSearchHistory(): void {
  activeSession = null
  try { localStorage.removeItem(ARCHIVE_SEARCH_HISTORY_STORAGE_KEY) }
  catch { /* Storage may be unavailable. */ }
  notifyUpdated()
}
