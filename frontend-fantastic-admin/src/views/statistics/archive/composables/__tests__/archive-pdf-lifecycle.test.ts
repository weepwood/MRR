import type { EffectScope } from 'vue'
import type { GalleryImage } from '../../types'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { useArchivePrint } from '../useArchivePrint'
import { ARCHIVE_SELECTION_CHANGED_EVENT } from '../useSelection'

const api = vi.hoisted(() => ({
  getSelectedImagesPdfExportPlan: vi.fn(),
  downloadSelectedImagesPdf: vi.fn(),
  createArchiveExportJob: vi.fn(),
  getArchiveExportJob: vi.fn(),
  cancelArchiveExportJob: vi.fn(),
}))
const clientPdf = vi.hoisted(() => ({ createPdfFromImageUrls: vi.fn() }))
const message = vi.hoisted(() => ({ error: vi.fn(), info: vi.fn(), warning: vi.fn(), success: vi.fn() }))
vi.mock('@/api/modules/archive-export', () => api)
vi.mock('@/utils/composables/useAuth', () => ({ default: () => ({ auth: () => true }) }))
vi.mock('../../utils/client-pdf', () => clientPdf)
vi.mock('element-plus', () => ({ ElMessage: message }))

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}

const images: GalleryImage[] = [{ id: 1, bah: 'TEST', imageUrl: '/test-1.jpg' }]
const otherImages: GalleryImage[] = [{ id: 2, bah: 'TEST', imageUrl: '/test-2.jpg' }]
const pendingJob = { id: 'old-pdf', format: 'PDF', scope: 'SELECTED_IMAGES', status: 'PENDING' }

describe('选页 PDF 异步生命周期', () => {
  const scopes: EffectScope[] = []
  function setup() {
    const scope = effectScope()
    scopes.push(scope)
    return { scope, pdf: scope.run(() => useArchivePrint())! }
  }
  function changeSelection() {
    window.dispatchEvent(new CustomEvent(ARCHIVE_SELECTION_CHANGED_EVENT, { detail: { keys: ['2'] } }))
  }

  beforeEach(() => {
    vi.resetAllMocks()
    vi.useFakeTimers()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    vi.stubGlobal('URL', class extends URL {
      static createObjectURL = vi.fn(() => 'blob:test-pdf')
      static revokeObjectURL = vi.fn()
    })
    api.cancelArchiveExportJob.mockResolvedValue({ data: { ...pendingJob, status: 'CANCELLED' } })
  })
  afterEach(() => {
    scopes.splice(0).forEach(scope => scope.stop())
    vi.clearAllTimers()
    vi.useRealTimers()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it.each(['BACKEND_JOB', 'CLIENT_PDF', 'BACKEND_STREAM'])('规划返回前切页不再执行旧 %s', async (mode) => {
    const plan = deferred<{ data: { executionMode: string } }>()
    api.getSelectedImagesPdfExportPlan.mockReturnValue(plan.promise)
    api.createArchiveExportJob.mockResolvedValue({ data: pendingJob })
    api.downloadSelectedImagesPdf.mockResolvedValue(new Blob(['pdf']))
    clientPdf.createPdfFromImageUrls.mockResolvedValue(new Blob(['pdf']))
    const { pdf } = setup()
    const running = pdf.exportSelectedPdf(images)
    changeSelection()
    plan.resolve({ data: { executionMode: mode } })
    await running
    expect(api.createArchiveExportJob).not.toHaveBeenCalled()
    expect(api.downloadSelectedImagesPdf).not.toHaveBeenCalled()
    expect(clientPdf.createPdfFromImageUrls).not.toHaveBeenCalled()
    expect(message.success).not.toHaveBeenCalled()
  })

  it('创建请求未返回时切页会丢弃旧任务并取消迟到任务', async () => {
    api.getSelectedImagesPdfExportPlan.mockResolvedValue({ data: { executionMode: 'BACKEND_JOB' } })
    const created = deferred<{ data: typeof pendingJob }>()
    api.createArchiveExportJob.mockReturnValue(created.promise)
    const { pdf } = setup()
    const running = pdf.exportSelectedPdf(images)
    await Promise.resolve()
    expect(api.createArchiveExportJob).toHaveBeenCalledTimes(1)
    changeSelection()
    created.resolve({ data: pendingJob })
    await running
    expect(pdf.pdfJob.value).toBeNull()
    expect(api.cancelArchiveExportJob).toHaveBeenCalledWith('old-pdf')
    expect(message.info).not.toHaveBeenCalled()
  })

  it('创建请求未返回时离页会取消迟到任务', async () => {
    api.getSelectedImagesPdfExportPlan.mockResolvedValue({ data: { executionMode: 'BACKEND_JOB' } })
    const created = deferred<{ data: typeof pendingJob }>()
    api.createArchiveExportJob.mockReturnValue(created.promise)
    const { scope, pdf } = setup()
    const running = pdf.exportSelectedPdf(images)
    await Promise.resolve()
    scope.stop()
    created.resolve({ data: pendingJob })
    await running
    expect(api.cancelArchiveExportJob).toHaveBeenCalledWith('old-pdf')
    expect(pdf.pdfJob.value).toBeNull()
    expect(message.info).not.toHaveBeenCalled()
  })

  it('旧创建迟到只取消旧任务，不清除新请求的 loading 和任务', async () => {
    api.getSelectedImagesPdfExportPlan.mockResolvedValue({ data: { executionMode: 'BACKEND_JOB' } })
    const oldCreated = deferred<{ data: typeof pendingJob }>()
    const newCreated = deferred<{ data: typeof pendingJob }>()
    api.createArchiveExportJob.mockReturnValueOnce(oldCreated.promise).mockReturnValueOnce(newCreated.promise)
    const { pdf } = setup()
    const oldRun = pdf.exportSelectedPdf(images)
    await Promise.resolve()
    changeSelection()
    const newRun = pdf.exportSelectedPdf(otherImages)
    await Promise.resolve()
    oldCreated.resolve({ data: pendingJob })
    await oldRun
    expect(pdf.exportingPdf.value).toBe(true)
    expect(pdf.pdfJob.value).toBeNull()
    newCreated.resolve({ data: { ...pendingJob, id: 'new-pdf' } })
    await newRun
    expect(pdf.exportingPdf.value).toBe(false)
    expect(pdf.pdfJob.value?.id).toBe('new-pdf')
    expect(api.cancelArchiveExportJob).toHaveBeenCalledTimes(1)
    expect(api.cancelArchiveExportJob).toHaveBeenCalledWith('old-pdf')
  })

  it.each(['CLIENT_PDF', 'BACKEND_STREAM'])('%s 返回前切页不会保存旧文件', async (mode) => {
    api.getSelectedImagesPdfExportPlan.mockResolvedValue({ data: { executionMode: mode } })
    const blob = deferred<Blob>()
    clientPdf.createPdfFromImageUrls.mockReturnValue(blob.promise)
    api.downloadSelectedImagesPdf.mockReturnValue(blob.promise)
    const { pdf } = setup()
    const running = pdf.exportSelectedPdf(images)
    await Promise.resolve()
    changeSelection()
    blob.resolve(new Blob(['old-pdf']))
    await running
    expect(URL.createObjectURL).not.toHaveBeenCalled()
    expect(message.success).not.toHaveBeenCalled()
  })

  it.each(['PLAN', 'CLIENT_PDF', 'BACKEND_STREAM'])('%s 等待期间离页不会执行或保存旧结果', async (mode) => {
    const plan = deferred<{ data: { executionMode: string } }>()
    const blob = deferred<Blob>()
    api.getSelectedImagesPdfExportPlan.mockReturnValue(mode === 'PLAN'
      ? plan.promise
      : Promise.resolve({ data: { executionMode: mode } }))
    clientPdf.createPdfFromImageUrls.mockReturnValue(blob.promise)
    api.downloadSelectedImagesPdf.mockReturnValue(blob.promise)
    api.createArchiveExportJob.mockResolvedValue({ data: pendingJob })
    const { scope, pdf } = setup()
    const running = pdf.exportSelectedPdf(images)
    await Promise.resolve()
    scope.stop()
    plan.resolve({ data: { executionMode: 'BACKEND_JOB' } })
    blob.resolve(new Blob(['old-pdf']))
    await running
    expect(api.createArchiveExportJob).not.toHaveBeenCalled()
    expect(URL.createObjectURL).not.toHaveBeenCalled()
    expect(message.success).not.toHaveBeenCalled()
  })

  it('旧请求报错和 finally 不影响切页后启动的新请求', async () => {
    const oldPlan = deferred<never>()
    const newPlan = deferred<{ data: { executionMode: string } }>()
    api.getSelectedImagesPdfExportPlan.mockReturnValueOnce(oldPlan.promise).mockReturnValueOnce(newPlan.promise)
    api.downloadSelectedImagesPdf.mockResolvedValue(new Blob(['new-pdf']))
    const { pdf } = setup()
    const oldRun = pdf.exportSelectedPdf(images)
    changeSelection()
    const newRun = pdf.exportSelectedPdf(otherImages)
    oldPlan.reject(new Error('过期请求失败'))
    await oldRun
    expect(message.error).not.toHaveBeenCalled()
    expect(pdf.exportingPdf.value).toBe(true)
    newPlan.resolve({ data: { executionMode: 'BACKEND_STREAM' } })
    await newRun
    expect(pdf.exportingPdf.value).toBe(false)
    expect(api.downloadSelectedImagesPdf).toHaveBeenCalledWith([2])
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
  })

  it('重复点击相同选择只请求一次规划', async () => {
    const plan = deferred<{ data: { executionMode: string } }>()
    api.getSelectedImagesPdfExportPlan.mockReturnValue(plan.promise)
    api.downloadSelectedImagesPdf.mockResolvedValue(new Blob(['pdf']))
    const { pdf } = setup()
    const first = pdf.exportSelectedPdf(images)
    const second = pdf.exportSelectedPdf(images)
    plan.resolve({ data: { executionMode: 'BACKEND_STREAM' } })
    await Promise.all([first, second])
    expect(api.getSelectedImagesPdfExportPlan).toHaveBeenCalledTimes(1)
  })

  it('相同选页通知不会中断有效导出，规划使用开始时的影像快照', async () => {
    const plan = deferred<{ data: { executionMode: string } }>()
    api.getSelectedImagesPdfExportPlan.mockReturnValue(plan.promise)
    clientPdf.createPdfFromImageUrls.mockResolvedValue(new Blob(['pdf']))
    const { pdf } = setup()
    const mutable = images.map(image => ({ ...image }))
    const running = pdf.exportSelectedPdf(mutable)
    window.dispatchEvent(new CustomEvent(ARCHIVE_SELECTION_CHANGED_EVENT, { detail: { keys: ['1'] } }))
    mutable[0].imageUrl = '/changed-after-start.jpg'
    plan.resolve({ data: { executionMode: 'CLIENT_PDF' } })
    await running
    expect(clientPdf.createPdfFromImageUrls).toHaveBeenCalledWith(['/test-1.jpg'])
    expect(message.success).toHaveBeenCalledTimes(1)
  })
})
