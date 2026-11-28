/** Display positions only: never use as a row identity or a database ID. */
export function pageRowOffset(page: number, size: number) {
  return (Math.max(1, page) - 1) * Math.max(0, size)
}

/** Native Element Plus index column, shared by all business lists. */
export function tableSequenceColumn(offset = 0, rowCount = 0) {
  return {
    type: 'index' as const,
    label: '序号',
    align: 'center' as const,
    className: 'mrr-table-sequence',
    width: Math.max(64, String(Math.max(1, offset + rowCount)).length * 8 + 24),
    index: (index: number) => offset + index + 1,
  }
}
