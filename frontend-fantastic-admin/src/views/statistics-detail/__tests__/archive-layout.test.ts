import { describe, expect, it } from 'vitest'

import {
  ARCHIVE_DEFAULT_PAGE_SIZE,
  ARCHIVE_GRID_GAP,
  getArchiveColumnCount,
  getArchivePageSize,
} from '../archive-layout'

describe('statistics detail archive layout', () => {
  it.each([
    { width: 695, columns: 1 },
    { width: 696, columns: 2 },
    { width: 1051, columns: 2 },
    { width: 1052, columns: 3 },
    { width: 1407, columns: 3 },
    { width: 1408, columns: 4 },
    { width: 1764, columns: 5 },
  ])('uses $columns columns at $width px', ({ width, columns }) => {
    expect(getArchiveColumnCount(width)).toBe(columns)
  })

  it.each([
    { width: 872, pageSize: 18 },
    { width: 1052, pageSize: 18 },
    { width: 1408, pageSize: 20 },
    { width: 1764, pageSize: 20 },
    { width: 2056, pageSize: 20 },
  ])('fills the last row at $width px with $pageSize records', ({ width, pageSize }) => {
    expect(getArchivePageSize(width)).toBe(pageSize)
  })

  it('uses the same gap as the archive grid stylesheet', async () => {
    const fs = await import('node:fs')
    const path = await import('node:path')
    const sfc = fs.readFileSync(path.resolve(__dirname, '../ArchiveDetailContent.vue'), 'utf8')
    const grid = sfc.match(/\.archive-grid\s*\{[^}]*\}/)?.[0] ?? ''

    expect(grid).toContain('minmax(340px, 1fr)')
    expect(grid).toContain('gap: var(--mrr-space-4)')
    expect(ARCHIVE_GRID_GAP).toBe(16)
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
