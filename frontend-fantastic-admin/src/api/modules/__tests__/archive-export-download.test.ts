import { afterEach, describe, expect, it, vi } from 'vitest'
import { downloadExportJobWithResume } from '@/views/statistics/archive/utils/resumable-export-download'
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
  afterEach(() => {
    vi.resetAllMocks()
    delete (window as Window & { showSaveFilePicker?: unknown }).showSaveFilePicker
  })

  it('does not fall back to FULL after the real Range transport rejects its resource budget', async () => {
    const truncate = vi.fn().mockResolvedValue(undefined)
    const close = vi.fn().mockResolvedValue(undefined)
    ;(window as Window & { showSaveFilePicker?: unknown }).showSaveFilePicker = vi.fn().mockResolvedValue({
      getFile: vi.fn().mockResolvedValue(new File([], 'synthetic.zip')),
      createWritable: vi.fn().mockResolvedValue({ write: vi.fn(), truncate, close }),
    })
    api.get.mockImplementation(async (_url, config) => {
      config.onDownloadProgress({ loaded: 1, total: limit + 1 })
      throw new Error('transport cancelled')
    })
    await expect(downloadExportJobWithResume({
      id: 'synthetic-job',
      format: 'ZIP',
      scope: 'WHOLE_ARCHIVE',
      status: 'SUCCESS',
      plannedCount: 1,
      processedCount: 1,
      failedCount: 0,
      estimatedBytes: 10,
      outputBytes: 10,
      cancelRequested: false,
    })).rejects.toThrow('分批')
    expect(api.get).toHaveBeenCalledOnce()
    expect(api.get.mock.calls[0][1].headers.Range).toBe('bytes=0-9')
    expect(truncate).toHaveBeenCalledWith(0)
    expect(close).toHaveBeenCalledOnce()
  })

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
