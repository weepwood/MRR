import { afterEach, describe, expect, it, vi } from 'vitest'
import { downloadArchiveExportJob, downloadArchivePdf, downloadArchiveZip, downloadSelectedImagesPdf } from '../archive-export'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('@/api/index', () => ({ default: api, getRequest: vi.fn(), postRequest: vi.fn() }))

const limit = 128 * 1024 * 1024
const downloads = [
  () => downloadArchiveExportJob('synthetic-job'),
  () => downloadArchiveZip('123', '456'),
  () => downloadArchivePdf('123', '456'),
  () => downloadSelectedImagesPdf([1]),
]

describe('archive export transport budget', () => {
  afterEach(() => vi.resetAllMocks())

  it.each(downloads)('cancels an oversized transfer through the existing Axios client', async (download) => {
    const request = async (...args: unknown[]) => {
      const config = args.at(-1) as { signal?: AbortSignal, onDownloadProgress?: (event: { loaded: number, total: number }) => void }
      config.onDownloadProgress?.({ loaded: 1, total: limit + 1 })
      expect(config.signal?.aborted).toBe(true)
      throw new Error('synthetic cancelled transport')
    }
    api.get.mockImplementation(request)
    api.post.mockImplementation(request)
    await expect(download()).rejects.toThrow('分批')
  })

  it('also stops chunked transfers with no Content-Length once loaded bytes exceed the budget', async () => {
    api.get.mockImplementation(async (_url, config) => {
      config.onDownloadProgress({ loaded: limit + 1 })
      expect(config.signal.aborted).toBe(true)
      throw new Error('synthetic cancelled transport')
    })
    await expect(downloadArchiveExportJob('synthetic-job')).rejects.toThrow('分批')
  })

  it('checks actual bytes even if progress was unavailable', async () => {
    const blob = new Blob(['synthetic'])
    Object.defineProperty(blob, 'size', { value: limit + 1 })
    api.get.mockResolvedValue(blob)
    await expect(downloadArchiveZip()).rejects.toThrow('分批')
  })

  it('preserves permission errors without retrying', async () => {
    const error = Object.assign(new Error('forbidden'), { response: { status: 403 } })
    api.get.mockRejectedValue(error)
    await expect(downloadArchiveExportJob('synthetic-job')).rejects.toBe(error)
    expect(api.get).toHaveBeenCalledOnce()
  })
})
