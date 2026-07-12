import { describe, expect, it } from 'vitest'

import {
  ARCHIVE_DEFAULT_PAGE_SIZE,
  getArchiveColumnCount,
  getArchivePageSize,
} from '../archive-layout'

describe('statistics detail archive layout', () => {
  it.each([
    { width: 575, columns: 1 },
    { width: 576, columns: 2 },
    { width: 871, columns: 2 },
    { width: 872, columns: 3 },
    { width: 1167, columns: 3 },
    { width: 1168, columns: 4 },
    { width: 1464, columns: 5 },
  ])('uses $columns columns at $width px', ({ width, columns }) => {
    expect(getArchiveColumnCount(width)).toBe(columns)
  })

  it.each([
    { width: 872, pageSize: 18 },
    { width: 1168, pageSize: 20 },
    { width: 1464, pageSize: 20 },
    { width: 1760, pageSize: 18 },
    { width: 2056, pageSize: 21 },
  ])('fills the last row at $width px with $pageSize records', ({ width, pageSize }) => {
    expect(getArchivePageSize(width)).toBe(pageSize)
  })

  it.each([0, -1, Number.NaN, Number.POSITIVE_INFINITY])(
    'keeps the default page size before a valid width is available (%s)',
    (width) => {
      expect(getArchivePageSize(width)).toBe(ARCHIVE_DEFAULT_PAGE_SIZE)
    },
  )

  it.each([280, 576, 872, 1168, 1464, 1760, 2056])(
    'always returns complete rows at %s px',
    (width) => {
      const columns = getArchiveColumnCount(width)
      const pageSize = getArchivePageSize(width)

      expect(pageSize).toBeGreaterThanOrEqual(ARCHIVE_DEFAULT_PAGE_SIZE)
      expect(pageSize % columns).toBe(0)
      expect(pageSize).toBeLessThan(ARCHIVE_DEFAULT_PAGE_SIZE + columns)
    },
  )
})
