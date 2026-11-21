import { describe, expect, it, vi } from 'vitest'
import { locateDirectoryEntry } from './locate-entry'

describe('locateDirectoryEntry', () => {
  it('到达单次扫描上限时保留继续定位的位置，并在续查时找到目标', async () => {
    const loadPage = vi.fn(async (cursor: number) => ({ cursor, next: cursor + 1 }))
    const signal = new AbortController().signal
    const find = (page: { cursor: number }) => page.cursor === 6 ? page.cursor : undefined
    const next = (page: { next: number }) => page.next

    const initial = await locateDirectoryEntry(1, loadPage, find, next, signal)
    expect(initial).toMatchObject({ cursor: 5, previousCursors: [1, 2, 3, 4], nextCursor: 6 })
    expect(loadPage).toHaveBeenCalledTimes(5)

    const continued = await locateDirectoryEntry(initial!.nextCursor!, loadPage, find, next, signal)
    expect(continued).toMatchObject({ cursor: 6, nextCursor: undefined, entry: 6 })
  })

  it('取消在途请求后丢弃旧响应，并停止翻页', async () => {
    const controller = new AbortController()
    const loadPage = vi.fn(async (cursor: number) => {
      controller.abort()
      return { cursor }
    })
    const result = await locateDirectoryEntry(
      1,
      loadPage,
      () => undefined,
      page => page.cursor + 1,
      controller.signal,
    )

    expect(result).toBeUndefined()
    expect(loadPage).toHaveBeenCalledTimes(1)
  })
})
