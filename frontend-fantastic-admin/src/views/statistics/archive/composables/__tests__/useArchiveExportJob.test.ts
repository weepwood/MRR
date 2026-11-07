import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, ref } from 'vue'
import { useArchiveExportJob } from '../useArchiveExportJob'
import { useArchiveSelectedZip } from '../useArchiveSelectedZip'

const api = vi.hoisted(() => ({
  createArchiveExportJob: vi.fn(),
  getArchiveExportJob: vi.fn(),
  cancelArchiveExportJob: vi.fn(),
  downloadArchiveExportJob: vi.fn(),
}))

const message = vi.hoisted(() => ({
  error: vi.fn(),
  info: vi.fn(),
  warning: vi.fn(),
  success: vi.fn(),
}))

const exportDownload = vi.hoisted(() => ({
  downloadExportJobToBrowser: vi.fn(),
  downloadExportJobWithResume: vi.fn(),
}))

vi.mock('@/api/modules/archive-export', () => api)
vi.mock('element-plus', () => ({ ElMessage: message }))
vi.mock('@/utils/composables/useAuth', () => ({ default: () => ({ auth: () => true }) }))
vi.mock('../../utils/resumable-export-download', () => exportDownload)

function job(id: string, status: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'CANCELLED') {
  return {
    id,
    format: 'PDF' as const,
    scope: 'SELECTED_IMAGES' as const,
    status,
    plannedCount: 2,
    processedCount: status === 'SUCCESS' || status === 'CANCELLED' ? 2 : 0,
    failedCount: 0,
    estimatedBytes: 1024,
    outputBytes: status === 'SUCCESS' ? 900 : 0,
    cancelRequested: status === 'CANCELLED',
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

describe('useArchiveExportJob', () => {
  it('选择变化后取消迟到创建的选中ZIP，不恢复旧任务或启动下载', async () => {
    const pending = deferred<{ data: ReturnType<typeof job> }>()
    api.createArchiveExportJob.mockReturnValue(pending.promise)
    api.cancelArchiveExportJob.mockResolvedValue({ data: job('old-selection', 'CANCELLED') })
    const selection = ref([{ id: 1, bah: '123' }])
    const scope = effectScope()
    const task = scope.run(() => useArchiveSelectedZip(selection))!
    const creating = task.start()
    selection.value = [{ id: 2, bah: '123' }]
    pending.resolve({ data: job('old-selection', 'PENDING') })
    await creating
    expect(task.job.value).toBeNull()
    expect(api.cancelArchiveExportJob).toHaveBeenCalledWith('old-selection')
    expect(api.getArchiveExportJob).not.toHaveBeenCalled()
    expect(exportDownload.downloadExportJobToBrowser).not.toHaveBeenCalled()
    scope.stop()
  })
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('丢弃任务后忽略已经发出的旧轮询响应', async () => {
    api.createArchiveExportJob.mockResolvedValue({ data: job('job-old', 'PROCESSING') })
    api.cancelArchiveExportJob.mockResolvedValue({ data: job('job-old', 'CANCELLED') })
    const pending = deferred<{ data: ReturnType<typeof job> }>()
    api.getArchiveExportJob.mockReturnValue(pending.promise)

    const scope = effectScope()
    const composable = scope.run(() => useArchiveExportJob('PDF'))
    expect(composable).toBeDefined()
    if (!composable) { return }

    await composable.start({ format: 'PDF', ids: [1, 2] })
    await vi.advanceTimersByTimeAsync(500)
    await composable.discard()

    pending.resolve({ data: job('job-old', 'SUCCESS') })
    await Promise.resolve()
    await Promise.resolve()

    expect(composable.job.value).toBeNull()
    scope.stop()
  })

  it('运行中任务取消后继续轮询到CANCELLED', async () => {
    api.createArchiveExportJob.mockResolvedValue({ data: job('job-cancel', 'PROCESSING') })
    api.cancelArchiveExportJob.mockResolvedValue({
      data: { ...job('job-cancel', 'PROCESSING'), cancelRequested: true },
    })
    api.getArchiveExportJob.mockResolvedValue({ data: job('job-cancel', 'CANCELLED') })

    const scope = effectScope()
    const composable = scope.run(() => useArchiveExportJob('PDF'))
    expect(composable).toBeDefined()
    if (!composable) { return }

    await composable.start({ format: 'PDF', ids: [1, 2] })
    await composable.cancel()
    expect(composable.job.value?.status).toBe('PROCESSING')
    expect(composable.job.value?.cancelRequested).toBe(true)
    expect(composable.cancelling.value).toBe(true)

    await vi.advanceTimersByTimeAsync(500)

    expect(composable.job.value?.status).toBe('CANCELLED')
    expect(composable.cancelling.value).toBe(false)
    scope.stop()
  })

  it('任务完成后自动下载完整的导出文件', async () => {
    const completedJob = job('job-complete', 'SUCCESS')
    api.createArchiveExportJob.mockResolvedValue({ data: job('job-complete', 'PROCESSING') })
    api.getArchiveExportJob.mockResolvedValue({ data: completedJob })
    exportDownload.downloadExportJobToBrowser.mockResolvedValue('blob')

    const scope = effectScope()
    const composable = scope.run(() => useArchiveExportJob('PDF'))
    expect(composable).toBeDefined()
    if (!composable) { return }

    await composable.start({ format: 'PDF', ids: [1, 2] })
    await vi.advanceTimersByTimeAsync(500)

    expect(exportDownload.downloadExportJobToBrowser).toHaveBeenCalledWith(completedJob)
    scope.stop()
  })

  it('下载预算拒绝时保留成功任务和重新下载入口，不报告导出生成失败', async () => {
    const completedJob = job('job-budget', 'SUCCESS')
    api.createArchiveExportJob.mockResolvedValue({ data: job('job-budget', 'PROCESSING') })
    api.getArchiveExportJob.mockResolvedValue({ data: completedJob })
    exportDownload.downloadExportJobToBrowser.mockRejectedValue(new Error('请分批导出'))
    const scope = effectScope()
    const composable = scope.run(() => useArchiveExportJob('PDF'))!
    await composable.start({ format: 'PDF', ids: [1, 2] })
    await vi.advanceTimersByTimeAsync(500)
    expect(composable.job.value).toEqual(completedJob)
    expect(message.error).toHaveBeenCalledWith(expect.stringContaining('已生成'))
    expect(message.success).not.toHaveBeenCalled()
    scope.stop()
  })
})
