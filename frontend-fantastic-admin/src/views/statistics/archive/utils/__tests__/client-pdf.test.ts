import { describe, expect, it, vi } from 'vitest'
import { buildPdfBlob, createPdfFromImageUrls } from '../client-pdf'

function readBlob(blob: Blob): Promise<ArrayBuffer> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.addEventListener('load', () => resolve(reader.result as ArrayBuffer), { once: true })
    reader.addEventListener('error', () => reject(reader.error || new Error('Blob 读取失败')), { once: true })
    reader.readAsArrayBuffer(blob)
  })
}

describe('client PDF writer', () => {
  it('rejects output beyond the shared budget before creating the final Blob', () => {
    const jpeg = new Blob(['synthetic'])
    Object.defineProperty(jpeg, 'size', { value: 128 * 1024 * 1024 })
    expect(() => buildPdfBlob([{ blob: jpeg, width: 1, height: 1 }])).toThrow('分批')
  })

  it('does not fetch images after cancellation', async () => {
    const controller = new AbortController()
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    try {
      controller.abort()
      await expect(createPdfFromImageUrls(['/first.jpg'], undefined, controller.signal)).rejects.toMatchObject({ name: 'AbortError' })
      expect(fetchMock).not.toHaveBeenCalled()
    }
    finally { vi.unstubAllGlobals() }
  })

  it('passes cancellation to fetch and stops before decoding or fetching another image', async () => {
    const controller = new AbortController()
    const body = new Blob(['synthetic'])
    const fetchMock = vi.fn(async () => {
      controller.abort()
      return { ok: true, blob: async () => body }
    })
    vi.stubGlobal('fetch', fetchMock)
    try {
      await expect(createPdfFromImageUrls(['/first.jpg', '/second.jpg'], undefined, controller.signal)).rejects.toMatchObject({ name: 'AbortError' })
      expect(fetchMock).toHaveBeenCalledTimes(1)
      expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/first.jpg'), expect.objectContaining({ signal: controller.signal }))
    }
    finally { vi.unstubAllGlobals() }
  })

  it('writes selected images as valid PDF pages', async () => {
    const jpeg = new Blob([new Uint8Array([0xFF, 0xD8, 0xFF, 0xD9])], { type: 'image/jpeg' })
    const pdf = buildPdfBlob([
      { blob: jpeg, width: 1000, height: 1400 },
      { blob: jpeg, width: 1400, height: 1000 },
    ])

    expect(pdf.type).toBe('application/pdf')
    const bytes = new Uint8Array(await readBlob(pdf))
    const text = new TextDecoder('latin1').decode(bytes)

    expect(text.startsWith('%PDF-1.4')).toBe(true)
    expect(text).toContain('/Type /Pages /Count 2')
    expect(text).toContain('/MediaBox [0 0 595.28 841.89]')
    expect(text).toContain('/MediaBox [0 0 841.89 595.28]')
    expect(text.endsWith('%%EOF\n')).toBe(true)

    const xrefOffset = Number(text.match(/startxref\n(\d+)\n%%EOF/)?.[1])
    expect(Number.isInteger(xrefOffset)).toBe(true)
    expect(text.slice(xrefOffset, xrefOffset + 4)).toBe('xref')
  })
})
