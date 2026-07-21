import type { GalleryImage } from '../../types'
import { effectScope } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useArchivePrint } from '../useArchivePrint'
import { ARCHIVE_SELECTION_CHANGED_EVENT } from '../useSelection'

const api = vi.hoisted(() => ({
  getSelectedImagesPdfExportPlan: vi.fn(),
  downloadSelectedImagesPdf: vi.fn(),
  createArchiveExportJob: vi.fn(),
  getArchiveExportJob: vi.fn(),
  listActiveArchiveExportJobs: vi.fn(),
  cancelArchiveExportJob: vi.fn(),
  downloadArchiveExportJob: vi.fn(),
}))

const clientPdf = vi.hoisted(() => ({
  createPdfFromImageUrls: vi.fn(),
}))

const message = vi.hoisted(() => ({
  error: vi.fn(),
  info: vi.fn(),
  warning: vi.fn(),
  success: vi.fn(),
}))

const permission = vi.hoisted(() => ({
  auth: vi.fn(() => true),
}))

vi.mock('@/api/modules/archive-export', () => api)
vi.mock('@/utils/composables/useAuth', () => ({
  default: () => permission,
}))
vi.mock('../../utils/client-pdf', () => clientPdf)
vi.mock('element-plus', () => ({ ElMessage: message }))

describe('useArchivePrint', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    permission.auth.mockReturnValue(true)
  })

  it('规划接口拒绝时不会继续在浏览器生成 PDF', async () => {
    api.getSelectedImagesPdfExportPlan.mockRejectedValue({
      message: 'No permission',
      response: { status: 403 },
    })
    const image: GalleryImage = {
      id: 1,
      bah: '00789508',
      sjh: '',
      imageUrl: '/api/v1/img/image/00789508/1/25.03.15/page.jpg',
    }

    const { exportSelectedPdf } = useArchivePrint()
    await exportSelectedPdf([image])

    expect(clientPdf.createPdfFromImageUrls).not.toHaveBeenCalled()
    expect(api.downloadSelectedImagesPdf).not.toHaveBeenCalled()
    expect(api.createArchiveExportJob).not.toHaveBeenCalled()
    expect(message.error).toHaveBeenCalledWith('No permission')
  })

  it('缺少 PDF 导出权限时不会请求规划接口', async () => {
    permission.auth.mockReturnValue(false)
    const image: GalleryImage = {
      id: 1,
      bah: '00789508',
      imageUrl: '/image.jpg',
    }

    const { exportSelectedPdf } = useArchivePrint()
    await exportSelectedPdf([image])

    expect(api.getSelectedImagesPdfExportPlan).not.toHaveBeenCalled()
    expect(message.warning).toHaveBeenCalledWith('当前账号没有病案 PDF 导出权限')
  })

  it('服务端规划为 BACKEND_JOB 时始终创建固定选中影像任务', async () => {
    api.getSelectedImagesPdfExportPlan.mockResolvedValue({
      data: {
        executionMode: 'BACKEND_JOB',
        wholeArchive: false,
      },
    })
    api.createArchiveExportJob.mockResolvedValue({
      data: {
        id: 'job-1',
        format: 'PDF',
        scope: 'SELECTED_IMAGES',
        status: 'PENDING',
        plannedCount: 2,
        processedCount: 0,
        failedCount: 0,
        estimatedBytes: 1024,
        outputBytes: 0,
        cancelRequested: false,
      },
    })
    const images: GalleryImage[] = [
      { id: 1, bah: '00789508', sjh: '', imageUrl: '/1.jpg' },
      { id: 2, bah: '00789508', sjh: '', imageUrl: '/2.jpg' },
    ]

    const { exportSelectedPdf, dismissPdfJob } = useArchivePrint()
    await exportSelectedPdf(images)

    expect(api.getSelectedImagesPdfExportPlan).toHaveBeenCalledWith([1, 2])
    expect(api.createArchiveExportJob).toHaveBeenCalledWith(expect.objectContaining({
      format: 'PDF',
      bah: '00789508',
      ids: [1, 2],
      idempotencyKey: expect.any(String),
    }))
    expect(api.downloadSelectedImagesPdf).not.toHaveBeenCalled()
    expect(clientPdf.createPdfFromImageUrls).not.toHaveBeenCalled()
    dismissPdfJob()
  })

  it('选中数量等于当前全部图片时仍提交固定 IDs', async () => {
    api.getSelectedImagesPdfExportPlan.mockResolvedValue({
      data: { executionMode: 'BACKEND_STREAM', wholeArchive: false },
    })
    api.downloadSelectedImagesPdf.mockResolvedValue(new Blob(['pdf']))
    const images: GalleryImage[] = [
      { id: 11, bah: '00789508', sjh: '', imageUrl: '/11.jpg' },
      { id: 12, bah: '00789508', sjh: '', imageUrl: '/12.jpg' },
    ]

    const { exportSelectedPdf } = useArchivePrint()
    await exportSelectedPdf(images)

    expect(api.getSelectedImagesPdfExportPlan).toHaveBeenCalledWith([11, 12])
    expect(api.downloadSelectedImagesPdf).toHaveBeenCalledWith([11, 12])
  })

  it('选中图片变化后不会继续显示或下载上一次 PDF 任务', async () => {
    api.getSelectedImagesPdfExportPlan.mockResolvedValue({
      data: {
        executionMode: 'BACKEND_JOB',
        wholeArchive: false,
      },
    })
    api.createArchiveExportJob.mockResolvedValue({
      data: {
        id: 'job-old-selection',
        format: 'PDF',
        scope: 'SELECTED_IMAGES',
        status: 'SUCCESS',
        plannedCount: 2,
        processedCount: 2,
        failedCount: 0,
        estimatedBytes: 1024,
        outputBytes: 900,
        cancelRequested: false,
      },
    })
    const scope = effectScope()
    const composable = scope.run(() => useArchivePrint())
    expect(composable).toBeDefined()
    if (!composable) return

    await composable.exportSelectedPdf([
      { id: 1, bah: '00789508', sjh: '', imageUrl: '/1.jpg' },
      { id: 2, bah: '00789508', sjh: '', imageUrl: '/2.jpg' },
    ])
    expect(composable.pdfJob.value?.id).toBe('job-old-selection')

    window.dispatchEvent(new CustomEvent(ARCHIVE_SELECTION_CHANGED_EVENT, {
      detail: { keys: ['3'] },
    }))

    expect(composable.pdfJob.value).toBeNull()
    expect(api.downloadArchiveExportJob).not.toHaveBeenCalled()
    scope.stop()
  })
})
