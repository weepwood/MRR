import type { ArchiveExportJob } from '@/api/modules/archive-export'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { downloadExportJobWithResume } from '../resumable-export-download'

const api = vi.hoisted(() => ({
  downloadArchiveExportJob: vi.fn(),
}))

vi.mock('@/api/modules/archive-export', () => api)

function job(): ArchiveExportJob {
  return {
    id: '12345678-abcd',
    format: 'ZIP',
    scope: 'WHOLE_ARCHIVE',
    status: 'SUCCESS',
    plannedCount: 2,
    processedCount: 2,
    failedCount: 0,
    estimatedBytes: 10,
    outputBytes: 10,
    cancelRequested: false,
    fileName: '00789508.zip',
  }
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

  it('restarts from zero when the selected file prefix does not match', async () => {
    const write = vi.fn().mockResolvedValue(undefined)
    const truncate = vi.fn().mockResolvedValue(undefined)
    const close = vi.fn().mockResolvedValue(undefined)
    const existing = new File([new Uint8Array([9, 9, 9, 9])], 'wrong.zip')
    ;(window as Window & { showSaveFilePicker?: unknown }).showSaveFilePicker = vi.fn().mockResolvedValue({
      getFile: vi.fn().mockResolvedValue(existing),
      createWritable: vi.fn().mockResolvedValue({ write, truncate, close }),
    })
    api.downloadArchiveExportJob
      .mockResolvedValueOnce(new Blob([new Uint8Array([1, 2, 3, 4])]))
      .mockResolvedValueOnce(new Blob([new Uint8Array(10)]))
    const currentJob = job()

    await downloadExportJobWithResume(currentJob)

    expect(truncate).toHaveBeenNthCalledWith(1, 0)
    expect(api.downloadArchiveExportJob).toHaveBeenNthCalledWith(2, currentJob.id, 'bytes=0-9')
    expect(write).toHaveBeenCalledWith(expect.objectContaining({ position: 0 }))
  })

  it('falls back to a full blob download when the proxy rejects Range requests', async () => {
    const write = vi.fn().mockResolvedValue(undefined)
    const truncate = vi.fn().mockResolvedValue(undefined)
    const close = vi.fn().mockResolvedValue(undefined)
    const existing = new File([], 'archive-job.zip')
    ;(window as Window & { showSaveFilePicker?: unknown }).showSaveFilePicker = vi.fn().mockResolvedValue({
      getFile: vi.fn().mockResolvedValue(existing),
      createWritable: vi.fn().mockResolvedValue({ write, truncate, close }),
    })
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:test'),
    })
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: vi.fn(),
    })
    api.downloadArchiveExportJob
      .mockRejectedValueOnce(new Error('Range unsupported'))
      .mockResolvedValueOnce(new Blob([new Uint8Array(10)]))
    const currentJob = job()

    await expect(downloadExportJobWithResume(currentJob)).resolves.toBe('blob')

    expect(api.downloadArchiveExportJob).toHaveBeenNthCalledWith(1, currentJob.id, 'bytes=0-9')
    expect(api.downloadArchiveExportJob).toHaveBeenNthCalledWith(2, currentJob.id)
    expect(close).toHaveBeenCalled()
  })
})
