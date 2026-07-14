import JSZip from 'jszip'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createArchiveZip } from '../client-zip'

function imageResponse(content: string): Response {
  const bytes = new TextEncoder().encode(content)
  return {
    ok: true,
    status: 200,
    blob: () => Promise.resolve({
      size: bytes.byteLength,
      arrayBuffer: () => Promise.resolve(bytes.buffer.slice(0)),
    }),
  } as unknown as Response
}

describe('client ZIP writer', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('downloads archive images and writes them to a ZIP using unique file names', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(imageResponse('first image'))
      .mockResolvedValueOnce(imageResponse('second image'))
    vi.stubGlobal('fetch', fetchMock)

    const archiveBlob = await createArchiveZip([
      { imageUrl: '/images/first.jpg', filename: 'record.jpg' },
      { imageUrl: '/images/second.jpg', filename: 'record.jpg' },
    ])
    const archive = await JSZip.loadAsync(archiveBlob)

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(Object.keys(archive.files)).toEqual(['record.jpg', 'record-2.jpg'])
    await expect(archive.file('record.jpg')?.async('text')).resolves.toBe('first image')
    await expect(archive.file('record-2.jpg')?.async('text')).resolves.toBe('second image')
  })
})
