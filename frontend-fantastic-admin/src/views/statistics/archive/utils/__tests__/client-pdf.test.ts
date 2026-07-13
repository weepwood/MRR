import { describe, expect, it, vi } from 'vitest'

vi.mock('@/api', () => ({
  default: {
    get: vi.fn(),
  },
}))

import { buildPdfBlob } from '../client-pdf'

function readBlob(blob: Blob): Promise<ArrayBuffer> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.addEventListener('load', () => resolve(reader.result as ArrayBuffer), { once: true })
    reader.addEventListener('error', () => reject(reader.error || new Error('Blob 读取失败')), { once: true })
    reader.readAsArrayBuffer(blob)
  })
}

describe('client PDF writer', () => {
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
