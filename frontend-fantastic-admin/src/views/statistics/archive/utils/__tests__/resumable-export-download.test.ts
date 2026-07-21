import type { ArchiveExportJob } from '@/api/modules/archive-export'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { downloadExportJobWithResume } from '../resumable-export-download'

const api = vi.hoisted(() => ({
  downloadArchiveExportJob: vi.fn(),
}))

vi.mock('@/api/modules/archive-export', () => api)

describe('resumable archive export download', () => {
  afterEach(() => {
    vi.clearAllMocks()
    delete (window as Window & { showSaveFilePicker?: unknown }).showSaveFilePicker
  })

  it('continues from the existing file length using a Range request', async () => {
    const write = vi.fn().mockResolvedValue(undefined)
    const truncate = vi.fn().mockResolvedValue(undefined)
    const close = vi.fn().mockResolvedValue(undefined)
    const existing = new File([new Uint8Array(4)], 'archive-job.zip')
    ;(window as Window & { showSaveFilePicker?: unknown }).showSaveFilePicker = vi.fn().mockResolvedValue({
      getFile: vi.fn().mockResolvedValue(existing),
      createWritable: vi.fn().mockResolvedValue({ write, truncate, close }),
    })
    api.downloadArchiveExportJob.mockResolvedValue(new Blob([new Uint8Array(6)]))
    const job: ArchiveExportJob = {
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

    await expect(downloadExportJobWithResume(job)).resolves.toBe('resumable')

    expect(api.downloadArchiveExportJob).toHaveBeenCalledWith(job.id, 'bytes=4-9')
    expect(write).toHaveBeenCalledWith(expect.objectContaining({
      type: 'write',
      position: 4,
    }))
    expect(truncate).toHaveBeenCalledWith(10)
    expect(close).toHaveBeenCalled()
  })
})
