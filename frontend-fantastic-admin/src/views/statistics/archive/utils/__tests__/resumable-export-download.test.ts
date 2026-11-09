import type { ArchiveExportJob } from '@/api/modules/archive-export'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { downloadExportJobToBrowser, downloadExportJobWithResume } from '../resumable-export-download'

const api = vi.hoisted(() => ({
  downloadArchiveExportJob: vi.fn(),
}))

vi.mock('@/api/modules/archive-export', () => api)

function job(outputBytes = 10): ArchiveExportJob {
  return {
    id: '12345678-abcd',
    format: 'ZIP',
    scope: 'WHOLE_ARCHIVE',
    status: 'SUCCESS',
    plannedCount: 2,
    processedCount: 2,
    failedCount: 0,
    estimatedBytes: outputBytes,
    outputBytes,
    cancelRequested: false,
    fileName: '00789508.zip',
  }
}

function mockBlobDownloadDom() {
  Object.defineProperty(URL, 'createObjectURL', {
    configurable: true,
    value: vi.fn(() => 'blob:test'),
  })
  Object.defineProperty(URL, 'revokeObjectURL', {
    configurable: true,
    value: vi.fn(),
  })
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
}

describe('resumable archive export download', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.clearAllMocks()
    delete (window as Window & { showSaveFilePicker?: unknown }).showSaveFilePicker
  })

  it.each([0, -1, Number.NaN, Number.POSITIVE_INFINITY, 1.5, 128 * 1024 * 1024 + 1])('refuses automatic downloads with unknown or over-budget size %s before fetching', async (size) => {
    mockBlobDownloadDom()
    api.downloadArchiveExportJob.mockResolvedValue(new Blob(['synthetic']))
    await expect(downloadExportJobToBrowser(job(size))).rejects.toThrow()
    expect(api.downloadArchiveExportJob).not.toHaveBeenCalled()
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })

  it('does not open the picker or fall back to Blob for an over-budget job', async () => {
    const picker = vi.fn()
    ;(window as Window & { showSaveFilePicker?: unknown }).showSaveFilePicker = picker
    await expect(downloadExportJobWithResume(job(128 * 1024 * 1024 + 1))).rejects.toThrow('分批')
    expect(picker).not.toHaveBeenCalled()
    expect(api.downloadArchiveExportJob).not.toHaveBeenCalled()
  })

  it('keeps small downloads usable without the file system API', async () => {
    mockBlobDownloadDom()
    api.downloadArchiveExportJob.mockResolvedValue(new Blob([new Uint8Array(10)]))
    await expect(downloadExportJobWithResume(job())).resolves.toBe('blob')
    expect(api.downloadArchiveExportJob).toHaveBeenCalledOnce()
  })

  it('accepts the exact 128 MiB boundary but rejects a mismatching response', async () => {
    mockBlobDownloadDom()
    const boundary = 128 * 1024 * 1024
    const blob = new Blob(['synthetic'])
    Object.defineProperty(blob, 'size', { value: boundary })
    api.downloadArchiveExportJob.mockResolvedValueOnce(blob)
    await expect(downloadExportJobToBrowser(job(boundary))).resolves.toBe('blob')
    api.downloadArchiveExportJob.mockResolvedValueOnce(new Blob(['short']))
    await expect(downloadExportJobToBrowser(job(10))).rejects.toThrow('长度不一致')
    expect(URL.createObjectURL).toHaveBeenCalledOnce()
  })

  it.each([401, 403, 404, 410])('does not retry a rejected Range request as a full Blob download (HTTP %s)', async (status) => {
    const truncate = vi.fn().mockResolvedValue(undefined)
    const close = vi.fn().mockResolvedValue(undefined)
    ;(window as Window & { showSaveFilePicker?: unknown }).showSaveFilePicker = vi.fn().mockResolvedValue({
      getFile: vi.fn().mockResolvedValue(new File([], 'synthetic.zip')),
      createWritable: vi.fn().mockResolvedValue({ write: vi.fn(), truncate, close }),
    })
    const error = Object.assign(new Error('rejected'), { response: { status } })
    api.downloadArchiveExportJob.mockRejectedValue(error)
    await expect(downloadExportJobWithResume(job())).rejects.toBe(error)
    expect(api.downloadArchiveExportJob).toHaveBeenCalledOnce()
    expect(truncate).toHaveBeenCalledWith(0)
    expect(close).toHaveBeenCalledOnce()
  })

  it('continues from the existing file length after the prefix matches', async () => {
    const write = vi.fn().mockResolvedValue(undefined)
    const truncate = vi.fn().mockResolvedValue(undefined)
    const close = vi.fn().mockResolvedValue(undefined)
    const existingBytes = new Uint8Array([1, 2, 3, 4])
    const existing = new File([existingBytes], 'archive-job.zip')
    ;(window as Window & { showSaveFilePicker?: unknown }).showSaveFilePicker = vi.fn().mockResolvedValue({
      getFile: vi.fn().mockResolvedValue(existing),
      createWritable: vi.fn().mockResolvedValue({ write, truncate, close }),
    })
    api.downloadArchiveExportJob
      .mockResolvedValueOnce(new Blob([existingBytes]))
      .mockResolvedValueOnce(new Blob([new Uint8Array(6)]))
    const currentJob = job()

    await expect(downloadExportJobWithResume(currentJob)).resolves.toBe('resumable')

    expect(api.downloadArchiveExportJob).toHaveBeenNthCalledWith(1, currentJob.id, 'bytes=0-3')
    expect(api.downloadArchiveExportJob).toHaveBeenNthCalledWith(2, currentJob.id, 'bytes=4-9')
    expect(write).toHaveBeenCalledWith(expect.objectContaining({
      type: 'write',
      position: 4,
    }))
    expect(truncate).toHaveBeenLastCalledWith(10)
    expect(close).toHaveBeenCalled()
  })

  it('does not modify a selected file whose prefix belongs to another export', async () => {
    mockBlobDownloadDom()
    const write = vi.fn().mockResolvedValue(undefined)
    const truncate = vi.fn().mockResolvedValue(undefined)
    const close = vi.fn().mockResolvedValue(undefined)
    const createWritable = vi.fn().mockResolvedValue({ write, truncate, close })
    const existing = new File([new Uint8Array([9, 9, 9, 9])], 'wrong.zip')
    ;(window as Window & { showSaveFilePicker?: unknown }).showSaveFilePicker = vi.fn().mockResolvedValue({
      getFile: vi.fn().mockResolvedValue(existing),
      createWritable,
    })
    api.downloadArchiveExportJob
      .mockResolvedValueOnce(new Blob([new Uint8Array([1, 2, 3, 4])]))
      .mockResolvedValueOnce(new Blob([new Uint8Array(10)]))
    const currentJob = job()

    await expect(downloadExportJobWithResume(currentJob)).resolves.toBe('blob')

    expect(createWritable).not.toHaveBeenCalled()
    expect(truncate).not.toHaveBeenCalled()
    expect(write).not.toHaveBeenCalled()
    expect(api.downloadArchiveExportJob).toHaveBeenNthCalledWith(2, currentJob.id)
  })

  it('restores an empty target when the proxy rejects the first Range request', async () => {
    mockBlobDownloadDom()
    const write = vi.fn().mockResolvedValue(undefined)
    const truncate = vi.fn().mockResolvedValue(undefined)
    const close = vi.fn().mockResolvedValue(undefined)
    const existing = new File([], 'archive-job.zip')
    ;(window as Window & { showSaveFilePicker?: unknown }).showSaveFilePicker = vi.fn().mockResolvedValue({
      getFile: vi.fn().mockResolvedValue(existing),
      createWritable: vi.fn().mockResolvedValue({ write, truncate, close }),
    })
    api.downloadArchiveExportJob
      .mockRejectedValueOnce(new Error('Range unsupported'))
      .mockResolvedValueOnce(new Blob([new Uint8Array(10)]))
    const currentJob = job()

    await expect(downloadExportJobWithResume(currentJob)).resolves.toBe('blob')

    expect(api.downloadArchiveExportJob).toHaveBeenNthCalledWith(1, currentJob.id, 'bytes=0-9')
    expect(api.downloadArchiveExportJob).toHaveBeenNthCalledWith(2, currentJob.id)
    expect(truncate).toHaveBeenCalledWith(0)
    expect(close).toHaveBeenCalled()
  })

  it('restores the original valid prefix when a later Range chunk fails', async () => {
    mockBlobDownloadDom()
    const chunkSize = 8 * 1024 * 1024
    const totalBytes = chunkSize + 10
    const write = vi.fn().mockResolvedValue(undefined)
    const truncate = vi.fn().mockResolvedValue(undefined)
    const close = vi.fn().mockResolvedValue(undefined)
    const existingBytes = new Uint8Array([1, 2, 3, 4])
    const existing = new File([existingBytes], 'partial.zip')
    ;(window as Window & { showSaveFilePicker?: unknown }).showSaveFilePicker = vi.fn().mockResolvedValue({
      getFile: vi.fn().mockResolvedValue(existing),
      createWritable: vi.fn().mockResolvedValue({ write, truncate, close }),
    })
    api.downloadArchiveExportJob
      .mockResolvedValueOnce(new Blob([existingBytes]))
      .mockResolvedValueOnce(new Blob([new Uint8Array(chunkSize)]))
      .mockRejectedValueOnce(new Error('second range failed'))
      .mockResolvedValueOnce(new Blob([new Uint8Array(totalBytes)]))

    await expect(downloadExportJobWithResume(job(totalBytes))).resolves.toBe('blob')

    expect(truncate).toHaveBeenCalledWith(existing.size)
    expect(close).toHaveBeenCalled()
    expect(api.downloadArchiveExportJob).toHaveBeenLastCalledWith('12345678-abcd')
  })
})
