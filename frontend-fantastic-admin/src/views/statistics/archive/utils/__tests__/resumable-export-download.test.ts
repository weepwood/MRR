import type { ArchiveExportJob } from '@/api/modules/archive-export'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { downloadExportJobWithResume } from '../resumable-export-download'

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
}

describe('resumable archive export download', () => {
  afterEach(() => {
    vi.clearAllMocks()
    delete (window as Window & { showSaveFilePicker?: unknown }).showSaveFilePicker
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
