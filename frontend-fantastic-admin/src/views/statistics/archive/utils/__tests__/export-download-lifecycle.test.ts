import type { ArchiveExportJob } from '@/api/modules/archive-export'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { downloadExportJobWithResume } from '../resumable-export-download'

const api = vi.hoisted(() => ({ downloadArchiveExportJob: vi.fn() }))
vi.mock('@/api/modules/archive-export', () => api)

const pickerWindow = window as Window & { showSaveFilePicker?: ReturnType<typeof vi.fn> }
const job = { id: 'test-job', format: 'ZIP', status: 'SUCCESS', outputBytes: 3 } as ArchiveExportJob
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}

describe('导出文件写入边界的有效期检查', () => {
  let current = true
  const isCurrent = () => current
  let handle: { getFile: ReturnType<typeof vi.fn>, createWritable: ReturnType<typeof vi.fn> }
  let stream: { write: ReturnType<typeof vi.fn>, truncate: ReturnType<typeof vi.fn>, close: ReturnType<typeof vi.fn>, abort?: ReturnType<typeof vi.fn> }
  beforeEach(() => {
    vi.resetAllMocks()
    current = true
    stream = { write: vi.fn().mockResolvedValue(undefined), truncate: vi.fn().mockResolvedValue(undefined), close: vi.fn().mockResolvedValue(undefined) }
    handle = { getFile: vi.fn().mockResolvedValue(new File([], 'test.zip')), createWritable: vi.fn().mockResolvedValue(stream) }
    Object.defineProperty(window, 'showSaveFilePicker', { configurable: true, value: vi.fn().mockResolvedValue(handle) })
    vi.stubGlobal('URL', class extends URL {
      static createObjectURL = vi.fn(() => 'blob:test')
      static revokeObjectURL = vi.fn()
    })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    api.downloadArchiveExportJob.mockResolvedValue(new Blob(['zip']))
  })
  afterEach(() => {
    delete (window as Window & { showSaveFilePicker?: unknown }).showSaveFilePicker
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('失效操作不打开文件选择器或发起下载', async () => {
    current = false
    await expect(downloadExportJobWithResume(job, isCurrent)).rejects.toMatchObject({ name: 'AbortError' })
    expect(pickerWindow.showSaveFilePicker).not.toHaveBeenCalled()
    expect(api.downloadArchiveExportJob).not.toHaveBeenCalled()
  })

  it('文件选择器迟到时不读取、打开写入或回退Blob', async () => {
    const chosen = deferred<typeof handle>()
    vi.mocked(pickerWindow.showSaveFilePicker!).mockReturnValue(chosen.promise)
    const running = downloadExportJobWithResume(job, isCurrent)
    current = false
    chosen.resolve(handle)
    await expect(running).rejects.toMatchObject({ name: 'AbortError' })
    expect(handle.getFile).not.toHaveBeenCalled()
    expect(handle.createWritable).not.toHaveBeenCalled()
    expect(api.downloadArchiveExportJob).not.toHaveBeenCalled()
  })

  it('前缀校验等待期间失效不会打开写入或回退整包', async () => {
    handle.getFile.mockResolvedValue(new File(['z'], 'partial.zip'))
    const prefix = deferred<Blob>()
    api.downloadArchiveExportJob.mockReturnValue(prefix.promise)
    const running = downloadExportJobWithResume(job, isCurrent)
    await vi.waitFor(() => expect(api.downloadArchiveExportJob).toHaveBeenCalledTimes(1))
    current = false
    prefix.resolve(new Blob(['z']))
    await expect(running).rejects.toMatchObject({ name: 'AbortError' })
    expect(handle.createWritable).not.toHaveBeenCalled()
    expect(api.downloadArchiveExportJob).toHaveBeenCalledTimes(1)
  })

  it('range响应迟到时不写入，恢复原长度且不回退', async () => {
    const chunk = deferred<Blob>()
    api.downloadArchiveExportJob.mockReturnValue(chunk.promise)
    const running = downloadExportJobWithResume(job, isCurrent)
    await vi.waitFor(() => expect(api.downloadArchiveExportJob).toHaveBeenCalledTimes(1))
    current = false
    chunk.resolve(new Blob(['zip']))
    await expect(running).rejects.toMatchObject({ name: 'AbortError' })
    expect(stream.write).not.toHaveBeenCalled()
    expect(stream.truncate).toHaveBeenCalledWith(0)
    expect(stream.close).toHaveBeenCalledTimes(1)
    expect(api.downloadArchiveExportJob).toHaveBeenCalledTimes(1)
  })

  it.each(['write', 'truncate'])('%s等待时失效只恢复原文件，不提交完整结果', async (step) => {
    const pending = deferred<void>()
    stream[step as 'write' | 'truncate'].mockReturnValueOnce(pending.promise)
    const running = downloadExportJobWithResume(job, isCurrent)
    await vi.waitFor(() => expect(stream[step as 'write' | 'truncate']).toHaveBeenCalledTimes(1))
    current = false
    pending.resolve(undefined)
    await expect(running).rejects.toMatchObject({ name: 'AbortError' })
    expect(stream.truncate).toHaveBeenLastCalledWith(0)
    expect(stream.close).toHaveBeenCalledTimes(1)
    expect(api.downloadArchiveExportJob).toHaveBeenCalledTimes(1)
  })

  it('写入流创建迟到时优先abort未提交暂存，不覆盖原文件', async () => {
    stream.abort = vi.fn().mockResolvedValue(undefined)
    const opened = deferred<typeof stream>()
    handle.createWritable.mockReturnValue(opened.promise)
    const running = downloadExportJobWithResume(job, isCurrent)
    await vi.waitFor(() => expect(handle.createWritable).toHaveBeenCalledTimes(1))
    current = false
    opened.resolve(stream)
    await expect(running).rejects.toMatchObject({ name: 'AbortError' })
    expect(stream.abort).toHaveBeenCalledTimes(1)
    expect(stream.close).not.toHaveBeenCalled()
    expect(stream.write).not.toHaveBeenCalled()
    expect(api.downloadArchiveExportJob).not.toHaveBeenCalled()
  })

  it('读取所选文件期间失效不继续校验或打开写入', async () => {
    const file = deferred<File>()
    handle.getFile.mockReturnValue(file.promise)
    const running = downloadExportJobWithResume(job, isCurrent)
    await vi.waitFor(() => expect(handle.getFile).toHaveBeenCalledTimes(1))
    current = false
    file.resolve(new File(['z'], 'partial.zip'))
    await expect(running).rejects.toMatchObject({ name: 'AbortError' })
    expect(handle.createWritable).not.toHaveBeenCalled()
    expect(api.downloadArchiveExportJob).not.toHaveBeenCalled()
  })

  it('失效后Range以普通网络错误拒绝也不回退', async () => {
    api.downloadArchiveExportJob.mockImplementationOnce(async () => {
      current = false
      throw new Error('late network error')
    })
    await expect(downloadExportJobWithResume(job, isCurrent)).rejects.toMatchObject({ name: 'AbortError' })
    expect(stream.truncate).toHaveBeenCalledWith(0)
    expect(api.downloadArchiveExportJob).toHaveBeenCalledTimes(1)
  })

  it('恢复原长度等待期间失效不再启动普通下载', async () => {
    const restored = deferred<void>()
    api.downloadArchiveExportJob.mockRejectedValueOnce(new Error('Range unsupported'))
    stream.truncate.mockReturnValueOnce(restored.promise)
    const running = downloadExportJobWithResume(job, isCurrent)
    await vi.waitFor(() => expect(stream.truncate).toHaveBeenCalledWith(0))
    current = false
    restored.resolve(undefined)
    await expect(running).rejects.toMatchObject({ name: 'AbortError' })
    expect(api.downloadArchiveExportJob).toHaveBeenCalledTimes(1)
  })

  it('恢复失败时明确报错，不用普通下载成功掩盖文件状态', async () => {
    api.downloadArchiveExportJob.mockRejectedValueOnce(new Error('range failed'))
    stream.truncate.mockRejectedValueOnce(new Error('disk failed'))
    await expect(downloadExportJobWithResume(job, isCurrent)).rejects.toThrow('原文件恢复失败')
    expect(api.downloadArchiveExportJob).toHaveBeenCalledTimes(1)
    expect(stream.close).not.toHaveBeenCalled()
  })

  it('失效时abort失败仍恢复原长度，不发起整包下载', async () => {
    stream.abort = vi.fn().mockRejectedValue(new Error('abort failed'))
    api.downloadArchiveExportJob.mockImplementationOnce(async () => {
      current = false
      return new Blob(['zip'])
    })
    await expect(downloadExportJobWithResume(job, isCurrent)).rejects.toMatchObject({ name: 'AbortError' })
    expect(stream.truncate).toHaveBeenCalledWith(0)
    expect(stream.close).toHaveBeenCalledTimes(1)
    expect(api.downloadArchiveExportJob).toHaveBeenCalledTimes(1)
  })

  it('close已开始时失效不重复提交或恢复，只让调用方静默忽略结果', async () => {
    const committed = deferred<void>()
    stream.close.mockReturnValueOnce(committed.promise)
    const running = downloadExportJobWithResume(job, isCurrent)
    await vi.waitFor(() => expect(stream.close).toHaveBeenCalledTimes(1))
    current = false
    committed.resolve(undefined)
    await expect(running).rejects.toMatchObject({ name: 'AbortError' })
    expect(stream.close).toHaveBeenCalledTimes(1)
    expect(stream.truncate).toHaveBeenCalledTimes(1)
    expect(stream.truncate).toHaveBeenCalledWith(3)
    expect(api.downloadArchiveExportJob).toHaveBeenCalledTimes(1)
  })

  it('close提交失败时即使abort成功也不以整包下载掩盖未知状态', async () => {
    stream.close.mockRejectedValueOnce(new Error('commit failed'))
    stream.abort = vi.fn().mockResolvedValue(undefined)
    await expect(downloadExportJobWithResume(job, isCurrent)).rejects.toThrow('原文件恢复失败')
    expect(api.downloadArchiveExportJob).toHaveBeenCalledTimes(1)
    expect(stream.close).toHaveBeenCalledTimes(1)
    expect(stream.truncate).toHaveBeenCalledTimes(1)
    expect(stream.abort).toHaveBeenCalledTimes(1)
  })

  it('恢复原文件的close失败时abort成功也不回退整包', async () => {
    api.downloadArchiveExportJob.mockRejectedValueOnce(new Error('Range failed'))
    stream.close.mockRejectedValueOnce(new Error('restore commit failed'))
    stream.abort = vi.fn().mockResolvedValue(undefined)
    await expect(downloadExportJobWithResume(job, isCurrent)).rejects.toThrow('原文件恢复失败')
    expect(api.downloadArchiveExportJob).toHaveBeenCalledTimes(1)
    expect(stream.truncate).toHaveBeenCalledWith(0)
    expect(stream.close).toHaveBeenCalledTimes(1)
    expect(stream.abort).toHaveBeenCalledTimes(1)
  })
})
