// Demo-only helpers. Keep this outside src/mock so the fake-server plugin
// discovers route modules rather than importing a helper as a route list.
export function demoOk<T>(data: T, message = '模拟操作成功') {
  return { code: 200, message, data, timestamp: new Date().toISOString() }
}

export function demoError(code: number, message: string) {
  return { code, message, timestamp: new Date().toISOString() }
}

export function boundedInt(value: unknown, fallback: number, max: number) {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 1 ? Math.min(Math.floor(parsed), max) : fallback
}

export function demoPage<T>(items: T[], pageValue: unknown, sizeValue: unknown) {
  const page = boundedInt(pageValue, 1, Number.MAX_SAFE_INTEGER)
  const size = boundedInt(sizeValue, 20, 100)
  return { list: items.slice((page - 1) * size, page * size), total: items.length, page, size, totalPages: Math.ceil(items.length / size) }
}

export function hoursAgo(hours: number) {
  return new Date(Date.now() - hours * 3_600_000).toISOString()
}
