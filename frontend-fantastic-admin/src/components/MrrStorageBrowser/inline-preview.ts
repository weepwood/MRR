import type { StorageEntry } from './presentation'
import { entryType, isImage } from './presentation'

export type PreviewKind = 'image' | 'text'

const textTypes = new Set(['TXT', 'CSV', 'TSV', 'LOG', 'JSON', 'XML', 'MD', 'INI', 'YAML', 'YML'])

export const inlinePreviewLimit: Record<PreviewKind, number> = {
  image: 12 * 1024 * 1024,
  text: 128 * 1024,
}

export function inlinePreviewKind(entry: StorageEntry): PreviewKind | undefined {
  if (entry.directory) {
    return undefined
  }
  return isImage(entry) ? 'image' : textTypes.has(entryType(entry)) ? 'text' : undefined
}

export function decodePreviewText(bytes: Uint8Array<ArrayBuffer>) {
  let text: string
  try {
    text = new TextDecoder('utf-8', { fatal: true }).decode(bytes)
  }
  catch {
    text = new TextDecoder('gb18030', { fatal: true }).decode(bytes)
  }
  // Reject binary content even when its filename has a text extension.
  if (text.includes('\0')) {
    throw new Error('Binary preview content')
  }
  return text
}

export async function readSignedPreviewText(url: URL, signal: AbortSignal) {
  const response = await fetch(url, { signal, credentials: 'omit', cache: 'no-store', referrerPolicy: 'no-referrer' })
  if (!response.ok) {
    throw new Error('Preview request failed')
  }
  const limit = inlinePreviewLimit.text
  if (Number(response.headers.get('Content-Length')) > limit) {
    await response.body?.cancel()
    throw new RangeError('Preview size exceeded')
  }
  if (!response.body) {
    if (response.status === 204) {
      return ''
    }
    throw new Error('Preview stream unavailable')
  }
  const reader = response.body.getReader()
  const bytes = new Uint8Array(limit)
  let length = 0
  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) {
        return decodePreviewText(bytes.subarray(0, length))
      }
      if (length + value.byteLength > limit) {
        void reader.cancel().catch(() => {})
        throw new RangeError('Preview size exceeded')
      }
      bytes.set(value, length)
      length += value.byteLength
    }
  }
  finally {
    reader.releaseLock()
  }
}
