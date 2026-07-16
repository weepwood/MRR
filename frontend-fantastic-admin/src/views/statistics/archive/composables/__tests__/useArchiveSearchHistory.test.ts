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
  vi.restoreAllMocks()
})

describe('archive local search history', () => {
  it('stores successful archive lookups locally and moves repeated items to the front', () => {
    vi.spyOn(Date, 'now')
      .mockReturnValueOnce(1000)
      .mockReturnValueOnce(2000)
      .mockReturnValueOnce(3000)

    addArchiveSearchHistory({ bah: '1234567', sjh: '88', imageCount: 12 })
    addArchiveSearchHistory({ bah: '7654321', sjh: '99', imageCount: 8 })
    addArchiveSearchHistory({ bah: '1234567', sjh: '88', imageCount: 15 })

    expect(readArchiveSearchHistory()).toEqual([
      {
        key: '1234567:88',
        bah: '1234567',
        sjh: '88',
        imageCount: 15,
        searchedAt: 3000,
      },
      {
        key: '7654321:99',
        bah: '7654321',
        sjh: '99',
        imageCount: 8,
        searchedAt: 2000,
      },
    ])
  })

  it('keeps at most twenty valid records', () => {
    vi.spyOn(Date, 'now').mockImplementation(() => Number(localStorage.getItem('test-clock') || 0) + 1)

    for (let index = 0; index < ARCHIVE_SEARCH_HISTORY_LIMIT + 5; index += 1) {
      localStorage.setItem('test-clock', String(index))
      addArchiveSearchHistory({ bah: String(1000000 + index), imageCount: index })
    }

    const history = readArchiveSearchHistory()
    expect(history).toHaveLength(ARCHIVE_SEARCH_HISTORY_LIMIT)
    expect(history[0]?.bah).toBe('1000024')
    expect(history.at(-1)?.bah).toBe('1000005')
  })

  it('removes one record or clears all records', () => {
    addArchiveSearchHistory({ bah: '1234567', sjh: '88', imageCount: 12 })
    addArchiveSearchHistory({ bah: '7654321', sjh: '99', imageCount: 8 })

    const [first] = readArchiveSearchHistory()
    removeArchiveSearchHistory(first.key)
    expect(readArchiveSearchHistory()).toHaveLength(1)

    clearArchiveSearchHistory()
    expect(readArchiveSearchHistory()).toEqual([])
    expect(localStorage.getItem(ARCHIVE_SEARCH_HISTORY_STORAGE_KEY)).toBeNull()
  })
})
