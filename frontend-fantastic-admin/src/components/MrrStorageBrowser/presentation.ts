export interface StorageEntry {
  name: string
  directory: boolean
  size: number
  lastModified?: string | null
}

const imageExtensions = new Set(['jpg', 'jpeg', 'png', 'gif', 'bmp', 'tif', 'tiff', 'webp'])
const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

function fileExtension(entry: StorageEntry) {
  const index = entry.name.lastIndexOf('.')
  return index < 0 ? '' : entry.name.slice(index + 1).toLowerCase()
}

export function isImage(entry?: StorageEntry | null) {
  return Boolean(entry && !entry.directory && imageExtensions.has(fileExtension(entry)))
}

export function entryType(entry: StorageEntry) {
  return entry.directory ? '文件夹' : fileExtension(entry).toUpperCase() || '文件'
}

export function formatFileSize(bytes?: number | null) {
  if (bytes == null || !Number.isFinite(bytes) || bytes < 0) {
    return '—'
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = bytes
  let unit = 0
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024
    unit++
  }
  return `${size.toFixed(unit === 0 || size >= 100 ? 0 : size >= 10 ? 1 : 2)} ${units[unit]}`
}

export function formatDate(value?: string | null) {
  const timestamp = value ? Date.parse(value) : Number.NaN
  return Number.isNaN(timestamp) ? '—' : dateFormatter.format(timestamp)
}

export function accessDenied(error: unknown) {
  const failure = error as { response?: { status?: number }, code?: number } | null
  const status = failure?.response?.status ?? failure?.code
  return status === 401 || status === 403
}
