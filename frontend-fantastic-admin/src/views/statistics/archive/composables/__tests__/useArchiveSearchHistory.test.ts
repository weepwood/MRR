import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ARCHIVE_SEARCH_HISTORY_LIMIT,
  ARCHIVE_SEARCH_HISTORY_STORAGE_KEY,
  addArchiveSearchHistory,
  clearArchiveSearchHistory,
  readArchiveSearchHistory,
  removeArchiveSearchHistory,
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
        key: '12345678:00000088',
        bah: '12345678',
        sjh: '00000088',
        imageCount: 15,
        searchedAt: 3000,
      },
      {
        key: '87654321:00000099',
        bah: '87654321',
        sjh: '00000099',
        imageCount: 8,
        searchedAt: 2000,
      },
    ])
  })

  it('keeps at most twenty valid records', () => {
    vi.spyOn(Date, 'now').mockImplementation(() => Number(localStorage.getItem('test-clock') || 0) + 1)

    for (let index = 0; index < ARCHIVE_SEARCH_HISTORY_LIMIT + 5; index += 1) {
      localStorage.setItem('test-clock', String(index))
      addArchiveSearchHistory({ bah: String(10000000 + index), imageCount: index })
    }

    const history = readArchiveSearchHistory()
    expect(history).toHaveLength(ARCHIVE_SEARCH_HISTORY_LIMIT)
    expect(history[0]?.bah).toBe('10000024')
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
})
