import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AUTH_STORAGE_KEYS } from '@/utils/auth-storage'
import {
  addArchiveSearchHistory,
  ARCHIVE_SEARCH_HISTORY_STORAGE_KEY,
  clearArchiveSearchHistory,
  loadArchiveSearchHistory,
  readArchiveSearchHistory,
  removeArchiveSearchHistory,
  toggleArchiveSearchHistoryFavorite,
} from '../useArchiveSearchHistory'

const { user, api } = vi.hoisted(() => ({
  user: { token: 'synthetic-session-a', isSessionVerified: true, profile: { id: 1 } },
  api: { getArchiveSearchHistory: vi.fn(), createArchiveSearchHistory: vi.fn(), updateArchiveSearchHistoryFavorite: vi.fn() },
}))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => user }))
vi.mock('@/api/modules/archive-search-history', () => api)

beforeEach(() => {
  vi.clearAllMocks()
  Object.assign(user, { token: 'synthetic-session-a', isSessionVerified: true, profile: { id: 1 } })
  localStorage.setItem(AUTH_STORAGE_KEYS.token, user.token)
  api.getArchiveSearchHistory.mockResolvedValue({ data: [] })
  api.createArchiveSearchHistory.mockResolvedValue({ data: {} })
  api.updateArchiveSearchHistoryFavorite.mockResolvedValue({ data: '' })
  clearArchiveSearchHistory()
})

afterEach(() => {
  localStorage.clear()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('archive local search history', () => {
  it('stores successful archive lookups locally and moves repeated items to the front', () => {
    vi.useFakeTimers()

    vi.setSystemTime(1000)
    addArchiveSearchHistory({ bah: '12345678', sjh: '00000088', imageCount: 12 })
    vi.setSystemTime(2000)
    addArchiveSearchHistory({ bah: '87654321', sjh: '00000099', imageCount: 8 })
    vi.setSystemTime(3000)
    addArchiveSearchHistory({ bah: '12345678', sjh: '00000088', imageCount: 15 })

    expect(readArchiveSearchHistory()).toEqual([
      {
        key: '12345678:00000088:success',
        bah: '12345678',
        sjh: '00000088',
        imageCount: 15,
        queryCount: 2,
        searchedAt: 3000,
        status: 'success',
        failureReason: '',
        favorite: false,
      },
      {
        key: '87654321:00000099:success',
        bah: '87654321',
        sjh: '00000099',
        imageCount: 8,
        queryCount: 1,
        searchedAt: 2000,
        status: 'success',
        failureReason: '',
        favorite: false,
      },
    ])
  })

  it('keeps failed lookups separately from successful lookups', () => {
    addArchiveSearchHistory({ bah: '12345678', status: 'success', imageCount: 12 })
    addArchiveSearchHistory({ bah: '12345678', status: 'failure', failureReason: 'SYNTHETIC_PRIVATE_ERROR' })

    expect(readArchiveSearchHistory()).toMatchObject([
      { key: '12345678:none:failure', status: 'failure', failureReason: '查询失败' },
      { key: '12345678:none:success', status: 'success', imageCount: 12 },
    ])
  })

  it('caps the in-memory recent history at 100 records', () => {
    vi.spyOn(Date, 'now').mockImplementation(() => Number(localStorage.getItem('test-clock') || 0) + 1)

    for (let index = 0; index < 105; index += 1) {
      localStorage.setItem('test-clock', String(index))
      addArchiveSearchHistory({ bah: String(10000000 + index), imageCount: index })
    }

    const history = readArchiveSearchHistory()
    expect(history).toHaveLength(100)
    expect(history[0]?.bah).toBe('10000104')
    expect(history.at(-1)?.bah).toBe('10000005')
  })

  it('removes one record or clears all records', () => {
    addArchiveSearchHistory({ bah: '12345678', sjh: '00000088', imageCount: 12 })
    addArchiveSearchHistory({ bah: '87654321', sjh: '00000099', imageCount: 8 })

    const [first] = readArchiveSearchHistory()
    removeArchiveSearchHistory(first.key)
    expect(readArchiveSearchHistory()).toHaveLength(1)

    clearArchiveSearchHistory()
    expect(readArchiveSearchHistory()).toEqual([])
    expect(localStorage.getItem(ARCHIVE_SEARCH_HISTORY_STORAGE_KEY)).toBeNull()
  })

  it('persists a record favorite state', () => {
    addArchiveSearchHistory({ bah: '12345678', imageCount: 12 })
    const [historyItem] = readArchiveSearchHistory()

    toggleArchiveSearchHistoryFavorite(historyItem.key)
    expect(readArchiveSearchHistory()[0]?.favorite).toBe(true)

    toggleArchiveSearchHistoryFavorite(historyItem.key)
    expect(readArchiveSearchHistory()[0]?.favorite).toBe(false)
  })

  it('removes legacy shared storage without reading or adopting it', () => {
    localStorage.setItem(ARCHIVE_SEARCH_HISTORY_STORAGE_KEY, JSON.stringify([{ bah: 'SYNTHETIC-LEGACY', searchedAt: Date.now() }]))
    expect(readArchiveSearchHistory()).toEqual([])
    expect(localStorage.getItem(ARCHIVE_SEARCH_HISTORY_STORAGE_KEY)).toBeNull()
  })

  it('never stores medical history in localStorage and sanitizes API failure text', async () => {
    addArchiveSearchHistory({ bah: 'SYNTHETIC-A', status: 'failure', failureReason: 'SYNTHETIC_PRIVATE_ERROR' })
    await flushPromises()
    expect(localStorage.getItem(ARCHIVE_SEARCH_HISTORY_STORAGE_KEY)).toBeNull()
    expect(JSON.stringify(api.createArchiveSearchHistory.mock.calls)).not.toContain('SYNTHETIC_PRIVATE_ERROR')
  })

  it('does not reuse A history when B has no server records', async () => {
    addArchiveSearchHistory({ bah: 'SYNTHETIC-A' })
    user.token = 'synthetic-session-b'
    user.profile = { id: 2 }
    localStorage.setItem(AUTH_STORAGE_KEYS.token, user.token)
    expect(await loadArchiveSearchHistory()).toEqual([])
  })

  it('rejects unverified or missing identities without sending history requests', async () => {
    user.isSessionVerified = false
    addArchiveSearchHistory({ bah: 'SYNTHETIC-UNVERIFIED' })
    expect(await loadArchiveSearchHistory()).toEqual([])
    await flushPromises()
    expect(api.createArchiveSearchHistory).not.toHaveBeenCalled()
    expect(api.getArchiveSearchHistory).not.toHaveBeenCalled()
    user.isSessionVerified = true
    user.profile = { id: 0 }
    addArchiveSearchHistory({ bah: 'SYNTHETIC-NO-ID' })
    expect(readArchiveSearchHistory()).toEqual([])
  })

  it('invalidates cached records when the same user receives a new token', () => {
    addArchiveSearchHistory({ bah: 'SYNTHETIC-A' })
    user.token = 'synthetic-session-a-next'
    localStorage.setItem(AUTH_STORAGE_KEYS.token, user.token)
    expect(readArchiveSearchHistory()).toEqual([])
  })

  it('ignores a late server response from a previous session', async () => {
    let finish: (value: unknown) => void = () => {}
    api.getArchiveSearchHistory.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
    const old = loadArchiveSearchHistory()
    await flushPromises()
    user.token = 'synthetic-session-b'
    user.profile = { id: 2 }
    localStorage.setItem(AUTH_STORAGE_KEYS.token, user.token)
    finish({ data: [{ id: 1, bah: 'SYNTHETIC-A', searchedAt: new Date().toISOString() }] })
    expect(await old).toEqual([])
    expect(readArchiveSearchHistory()).toEqual([])
  })

  it('does not send a queued A creation using the next session token', async () => {
    addArchiveSearchHistory({ bah: 'SYNTHETIC-A' })
    user.token = 'synthetic-session-b'
    user.profile = { id: 2 }
    localStorage.setItem(AUTH_STORAGE_KEYS.token, user.token)
    await flushPromises()
    expect(api.createArchiveSearchHistory).not.toHaveBeenCalled()
  })

  it('does not roll back B history when an A favorite update fails late', async () => {
    api.getArchiveSearchHistory.mockResolvedValueOnce({ data: [{ id: 1, bah: 'SYNTHETIC-A', searchedAt: new Date().toISOString() }] })
    await loadArchiveSearchHistory()
    let reject: (error: Error) => void = () => {}
    api.updateArchiveSearchHistoryFavorite.mockImplementationOnce(() => new Promise((_, fail) => { reject = fail }))
    toggleArchiveSearchHistoryFavorite(readArchiveSearchHistory()[0].key)
    await flushPromises()
    user.token = 'synthetic-session-b'
    user.profile = { id: 2 }
    localStorage.setItem(AUTH_STORAGE_KEYS.token, user.token)
    addArchiveSearchHistory({ bah: 'SYNTHETIC-B' })
    reject(new Error('synthetic failure'))
    await flushPromises()
    expect(readArchiveSearchHistory().map(item => item.bah)).toEqual(['SYNTHETIC-B'])
  })

  it('expires memory records at 30 days and tolerates unavailable localStorage', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-01T00:00:00Z'))
    addArchiveSearchHistory({ bah: 'SYNTHETIC-A' })
    vi.setSystemTime(new Date('2026-10-31T00:00:00Z'))
    expect(readArchiveSearchHistory()).toEqual([])
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new Error('blocked') })
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked') })
    addArchiveSearchHistory({ bah: 'SYNTHETIC-MEMORY' })
    expect(readArchiveSearchHistory()[0]?.bah).toBe('SYNTHETIC-MEMORY')
  })

  it('rolls back only the failed favorite even when unrelated history was added', async () => {
    api.getArchiveSearchHistory.mockResolvedValueOnce({ data: [{ id: 1, bah: 'SYNTHETIC-A', searchedAt: new Date().toISOString() }] })
    await loadArchiveSearchHistory()
    let reject: (error: Error) => void = () => {}
    api.updateArchiveSearchHistoryFavorite.mockImplementationOnce(() => new Promise((_, fail) => { reject = fail }))
    toggleArchiveSearchHistoryFavorite(readArchiveSearchHistory()[0].key)
    await flushPromises()
    addArchiveSearchHistory({ bah: 'SYNTHETIC-NEW' })
    reject(new Error('synthetic failure'))
    await flushPromises()
    expect(readArchiveSearchHistory().map(item => item.bah)).toEqual(['SYNTHETIC-NEW', 'SYNTHETIC-A'])
    expect(readArchiveSearchHistory().find(item => item.bah === 'SYNTHETIC-A')?.favorite).toBe(false)
  })

  it('drops old history when another tab changes or removes the stored auth token', () => {
    addArchiveSearchHistory({ bah: 'SYNTHETIC-A' })
    localStorage.removeItem(AUTH_STORAGE_KEYS.token)
    expect(readArchiveSearchHistory()).toEqual([])
  })
})
