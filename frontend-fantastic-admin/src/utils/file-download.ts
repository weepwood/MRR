type HeaderBag = Record<string, unknown> & {
  get?: (name: string) => unknown
}

export function getResponseHeader(headers: unknown, name: string) {
  if (!headers || typeof headers !== 'object') {
    return undefined
  }

  const bag = headers as HeaderBag
  if (typeof bag.get === 'function') {
    const value = bag.get(name)
    if (value !== undefined && value !== null) {
      return String(value)
    }
  }

  const directValue = bag[name] ?? bag[name.toLowerCase()]
  return directValue === undefined || directValue === null ? undefined : String(directValue)
}

export function resolveDownloadFileName(headers: unknown, fallbackFileName: string) {
  const contentDisposition = getResponseHeader(headers, 'content-disposition')
  if (!contentDisposition) {
    return sanitizeFileName(fallbackFileName)
  }

  const encodedMatch = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(contentDisposition)
  if (encodedMatch?.[1]) {
    try {
      return sanitizeFileName(decodeURIComponent(stripQuotes(encodedMatch[1].trim())))
    }
    catch {
      // 回退到普通 filename 或调用方提供的文件名。
    }
  }

  const plainMatch = /filename\s*=\s*(?:"([^"]+)"|([^;]+))/i.exec(contentDisposition)
  const plainFileName = plainMatch?.[1] ?? plainMatch?.[2]
  return sanitizeFileName(plainFileName?.trim() || fallbackFileName)
}

export function downloadResponseBlob(
  response: { data: Blob, headers?: unknown },
  fallbackFileName: string,
) {
  const fileName = resolveDownloadFileName(response.headers, fallbackFileName)
  downloadBlob(response.data, fileName)
  return fileName
}

export function downloadBlob(blobValue: Blob | BlobPart, fileName: string) {
  const blob = blobValue instanceof Blob ? blobValue : new Blob([blobValue])
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = sanitizeFileName(fileName)
  anchor.rel = 'noopener'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()

  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}

export function createCsvBlob(
  headers: string[],
  rows: Array<Array<string | number | null | undefined>>,
) {
  const lines = [headers, ...rows]
    .map(row => row.map(escapeCsvCell).join(','))
    .join('\r\n')
  return new Blob([`\uFEFF${lines}\r\n`], { type: 'text/csv;charset=utf-8' })
}

function escapeCsvCell(value: string | number | null | undefined) {
  if (value === null || value === undefined) {
    return ''
  }

  const text = protectSpreadsheetValue(String(value))
  if (!/[",\r\n]/.test(text)) {
    return text
  }
  return `"${text.replace(/"/g, '""')}"`
}

function protectSpreadsheetValue(value: string) {
  const startsSpreadsheetFormula = /^[=+\-@\t\r]/.test(value)
  const hasLeadingZero = /^0\d+$/.test(value)
  const isLongInteger = /^\d{16,}$/.test(value)
  return startsSpreadsheetFormula || hasLeadingZero || isLongInteger ? `'${value}` : value
}

function stripQuotes(value: string) {
  if (value.startsWith('"') && value.endsWith('"')) {
    return value.slice(1, -1)
  }
  return value
}

function sanitizeFileName(fileName: string) {
  const sanitized = fileName.replace(/[\\/:*?"<>|]+/g, '-').trim()
  return sanitized || 'download.csv'
}
