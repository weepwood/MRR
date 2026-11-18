import type { EffectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { useArchiveExportJob } from '../useArchiveExportJob'

const api = vi.hoisted(() => ({
  createArchiveExportJob: vi.fn(),
  getArchiveExportJob: vi.fn(),
  cancelArchiveExportJob: vi.fn(),
  downloadArchiveExportJob: vi.fn(),
}))
const message = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn(), info: vi.fn() }))
vi.mock('@/api/modules/archive-export', () => api)
vi.mock('element-plus', () => ({ ElMessage: message }))

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}
function job(id: string, status = 'SUCCESS') {
  return { id, status, format: 'ZIP', outputBytes: 3, fileName: 'test.zip' }
}

describe('任务下载与当前页面操作绑定', () => {
  const scopes: EffectScope[] = []
  function setup() {
    const scope = effectScope()
    scopes.push(scope)
    return { scope, task: scope.run(() => useArchiveExportJob('ZIP'))! }
  }
  beforeEach(() => {
    vi.resetAllMocks()
    vi.useFakeTimers()
    vi.stubGlobal('URL', class extends URL {
      static createObjectURL = vi.fn(() => 'blob:test')
      static revokeObjectURL = vi.fn()
    })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  })
  afterEach(() => {
    scopes.splice(0).forEach(scope => scope.stop())
    vi.clearAllTimers()
    vi.useRealTimers()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it.each(['discard', 'dismiss', 'dispose', 'start'])('自动Blob下载期间%s后不保存、不提示', async (action) => {
    api.createArchiveExportJob.mockResolvedValue({ data: job('old', 'PENDING') })
    api.getArchiveExportJob.mockResolvedValue({ data: job('old') })
    const blob = deferred<Blob>()
    api.downloadArchiveExportJob.mockReturnValue(blob.promise)
    const { scope, task } = setup()
    await task.start({ format: 'ZIP', ids: [1] })
    await vi.advanceTimersByTimeAsync(500)
    expect(api.downloadArchiveExportJob).toHaveBeenCalledTimes(1)
    if (action === 'dispose') {
      scope.stop()
    }
    else if (action === 'start') {
      api.createArchiveExportJob.mockResolvedValue({ data: job('new', 'PENDING') })
      await task.start({ format: 'ZIP', ids: [2] })
    }
    else {
      await task[action as 'discard' | 'dismiss']()
    }
    blob.resolve(new Blob(['zip']))
    await vi.advanceTimersByTimeAsync(0)
    expect(URL.createObjectURL).not.toHaveBeenCalled()
    expect(message.success).not.toHaveBeenCalled()
    expect(message.error).not.toHaveBeenCalled()
    expect(task.downloading.value).toBe(false)
  })

  it('旧手动下载finally不清除新下载loading，只有新文件被保存', async () => {
    const oldBlob = deferred<Blob>()
    const newBlob = deferred<Blob>()
    api.downloadArchiveExportJob.mockReturnValueOnce(oldBlob.promise).mockReturnValueOnce(newBlob.promise)
    api.createArchiveExportJob.mockResolvedValueOnce({ data: job('old') }).mockResolvedValueOnce({ data: job('new') })
    const { task } = setup()
    await task.start({ format: 'ZIP', ids: [1] })
    const oldRun = task.download()
    await task.start({ format: 'ZIP', ids: [2] })
    const newRun = task.download()
    oldBlob.resolve(new Blob(['old']))
    await oldRun
    expect(task.downloading.value).toBe(true)
    expect(URL.createObjectURL).not.toHaveBeenCalled()
    newBlob.resolve(new Blob(['new']))
    await newRun
    expect(task.downloading.value).toBe(false)
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
    expect(message.success).toHaveBeenCalledTimes(1)
  })

  it('离页后的旧下载错误保持静默', async () => {
    const blob = deferred<Blob>()
    api.downloadArchiveExportJob.mockReturnValue(blob.promise)
    api.createArchiveExportJob.mockResolvedValue({ data: job('old') })
    const { scope, task } = setup()
    await task.start({ format: 'PDF', ids: [1] })
    const running = task.download()
    scope.stop()
    blob.reject(new Error('late error'))
    await running
    expect(message.error).not.toHaveBeenCalled()
  })

  it('相同任务下载期间重复点击不发出第二个请求', async () => {
    const blob = deferred<Blob>()
    api.downloadArchiveExportJob.mockReturnValue(blob.promise)
    api.createArchiveExportJob.mockResolvedValue({ data: job('current') })
    const { task } = setup()
    await task.start({ format: 'ZIP', ids: [1] })
    const first = task.download()
    const second = task.download()
    blob.resolve(new Blob(['zip']))
    await Promise.all([first, second])
    expect(api.downloadArchiveExportJob).toHaveBeenCalledTimes(1)
    expect(message.success).toHaveBeenCalledTimes(1)
  })

  it.each([true, false])('并发旧轮询在自动下载完成前后返回SUCCESS都不重复下载：%s', async (beforeDownload) => {
    const oldPoll = deferred<{ data: ReturnType<typeof job> }>()
    const blob = deferred<Blob>()
    api.createArchiveExportJob.mockResolvedValue({ data: job('current', 'PENDING') })
    api.getArchiveExportJob.mockReturnValueOnce(oldPoll.promise).mockResolvedValue({ data: job('current') })
    api.cancelArchiveExportJob.mockResolvedValue({ data: { ...job('current', 'PROCESSING'), cancelRequested: true } })
    api.downloadArchiveExportJob.mockReturnValue(blob.promise)
    const { task } = setup()
    await task.start({ format: 'ZIP', ids: [1] })
    await vi.advanceTimersByTimeAsync(500)
    await task.cancel()
    await vi.advanceTimersByTimeAsync(500)
    expect(api.downloadArchiveExportJob).toHaveBeenCalledTimes(1)
    const manual = task.download()
    if (beforeDownload) {
      oldPoll.resolve({ data: job('current') })
      await vi.advanceTimersByTimeAsync(0)
    }
    blob.resolve(new Blob(['zip']))
    await vi.advanceTimersByTimeAsync(0)
    if (!beforeDownload) {
      oldPoll.resolve({ data: job('current') })
      await vi.advanceTimersByTimeAsync(0)
    }
    await manual
    expect(api.downloadArchiveExportJob).toHaveBeenCalledTimes(1)
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
    expect(message.success).toHaveBeenCalledTimes(1)
    expect(task.downloading.value).toBe(false)
  })
})
