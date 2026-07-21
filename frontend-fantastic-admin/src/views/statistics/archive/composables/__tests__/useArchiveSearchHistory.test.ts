import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  addArchiveSearchHistory,
  ARCHIVE_SEARCH_HISTORY_STORAGE_KEY,
  clearArchiveSearchHistory,
  readArchiveSearchHistory,
  removeArchiveSearchHistory,
  toggleArchiveSearchHistoryFavorite,
} from '../useArchiveSearchHistory'

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
    addArchiveSearchHistory({ bah: '12345678', status: 'failure', failureReason: '未查询到影像' })

    expect(readArchiveSearchHistory()).toMatchObject([
      { key: '12345678:none:failure', status: 'failure', failureReason: '未查询到影像' },
      { key: '12345678:none:success', status: 'success', imageCount: 12 },
    ])
  })

  it('keeps all valid records', () => {
    vi.spyOn(Date, 'now').mockImplementation(() => Number(localStorage.getItem('test-clock') || 0) + 1)

    for (let index = 0; index < 25; index += 1) {
      localStorage.setItem('test-clock', String(index))
      addArchiveSearchHistory({ bah: String(10000000 + index), imageCount: index })
    }

    const history = readArchiveSearchHistory()
    expect(history).toHaveLength(25)
    expect(history[0]?.bah).toBe('10000024')
    expect(history.at(-1)?.bah).toBe('10000000')
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
})
