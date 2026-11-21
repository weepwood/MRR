const MAX_PAGES_PER_LOOKUP = 5

/** Scan a bounded number of pages without keeping their entries in memory. */
export async function locateDirectoryEntry<Cursor, Page, Entry>(
  initialCursor: Cursor,
  loadPage: (cursor: Cursor) => Promise<Page>,
  findEntry: (page: Page) => Entry | undefined,
  getNextCursor: (page: Page) => Cursor | undefined,
  signal: AbortSignal,
) {
  const previousCursors: Cursor[] = []
  let cursor = initialCursor

  while (!signal.aborted) {
    const page = await loadPage(cursor)
    if (signal.aborted) {
      return
    }
    const entry = findEntry(page)
    const nextCursor = entry ? undefined : getNextCursor(page)
    if (nextCursor !== undefined && (nextCursor === cursor || previousCursors.includes(nextCursor))) {
      throw new Error('目录返回了重复的分页位置')
    }
    if (nextCursor === undefined || previousCursors.length + 1 === MAX_PAGES_PER_LOOKUP) {
      return { page, cursor, previousCursors, entry, nextCursor }
    }
    previousCursors.push(cursor)
    cursor = nextCursor
  }
}
