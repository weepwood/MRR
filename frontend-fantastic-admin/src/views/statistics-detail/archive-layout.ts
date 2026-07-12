export const ARCHIVE_DEFAULT_PAGE_SIZE = 18

const ARCHIVE_CARD_MIN_WIDTH = 280
const ARCHIVE_GRID_GAP = 16

/** 与 .archive-grid 的 minmax(280px, 1fr) 和 16px gap 保持一致。 */
export function getArchiveColumnCount(containerWidth: number) {
  return Math.max(
    1,
    Math.floor((containerWidth + ARCHIVE_GRID_GAP) / (ARCHIVE_CARD_MIN_WIDTH + ARCHIVE_GRID_GAP)),
  )
}

/** 以 18 条为基准向上补齐整行，避免非末页出现残缺行。 */
export function getArchivePageSize(containerWidth: number) {
  if (!Number.isFinite(containerWidth) || containerWidth <= 0) {
    return ARCHIVE_DEFAULT_PAGE_SIZE
  }

  const columns = getArchiveColumnCount(containerWidth)
  return Math.ceil(ARCHIVE_DEFAULT_PAGE_SIZE / columns) * columns
}
