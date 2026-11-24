import type { MockScanRecord } from './records'
import type { OssBrowserEntry } from '@/api/modules/oss'

const root = 'medical-records/'

function safeSegment(value: string) {
  const segment = value.trim()
  return segment && segment !== '.' && segment !== '..'
    && !segment.includes('/') && !segment.includes('\\')
    && !segment.includes(':') && !segment.includes(String.fromCharCode(0))
    ? segment : undefined
}

// Mirrors OssMigrationRoutePolicy; independent from the legacy browser fixtures.
export function sharedObjectKey(scan: MockScanRecord) {
  const sjh = safeSegment(scan.sjh)
  const bah = safeSegment(scan.bah)
  const filename = safeSegment(scan.filename)
  if (!sjh || !/^\d{4,}$/.test(sjh) || !bah || !filename) { return undefined }
  return `${root}${sjh.slice(0, 4)}/${sjh}-${bah}/${filename}`
}

export function sharedObjectEntries(scans: readonly MockScanRecord[], prefix: string) {
  if (!prefix.startsWith(root) || !prefix.endsWith('/')) { return [] }
  const entries = new Map<string, OssBrowserEntry>()
  for (const scan of scans) {
    if (!scan.uploadFlag || !scan.ossUrl) { continue }
    const key = sharedObjectKey(scan)
    if (!key?.startsWith(prefix)) { continue }
    const remaining = key.slice(prefix.length)
    const slash = remaining.indexOf('/')
    if (slash >= 0) {
      const name = remaining.slice(0, slash)
      const directoryKey = `${prefix}${name}/`
      entries.set(directoryKey, { name, key: directoryKey, directory: true, size: 0 })
    }
    else {
      entries.set(key, {
        name: remaining,
        key,
        directory: false,
        size: scan.fileSize,
        lastModified: scan.migratedAt ?? scan.uploadDate,
        etag: scan.checksumMd5,
        storageClass: 'STANDARD',
      })
    }
  }
  return [...entries.values()].sort((left, right) => left.key.localeCompare(right.key))
}

export function sharedObjectUrl(scans: readonly MockScanRecord[], key: string) {
  return scans.find(scan => scan.uploadFlag && scan.ossUrl && sharedObjectKey(scan) === key)?.ossUrl
}
