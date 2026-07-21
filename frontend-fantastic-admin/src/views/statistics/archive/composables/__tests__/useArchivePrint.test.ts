import type { GalleryImage } from '../../types'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useArchivePrint } from '../useArchivePrint'

const api = vi.hoisted(() => ({
  getArchivePdfExportPlan: vi.fn(),
  downloadArchivePdf: vi.fn(),
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
    api.listActiveArchiveExportJobs.mockResolvedValue({ data: [] })
  })

  it('规划接口拒绝时不会继续在浏览器生成 PDF', async () => {
    api.getArchivePdfExportPlan.mockRejectedValue({
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
    expect(api.downloadArchivePdf).not.toHaveBeenCalled()
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

    expect(api.getArchivePdfExportPlan).not.toHaveBeenCalled()
    expect(message.warning).toHaveBeenCalledWith('当前账号没有病案 PDF 导出权限')
  })

  it('服务端规划为 BACKEND_JOB 时创建选中影像异步任务', async () => {
    api.getArchivePdfExportPlan.mockResolvedValue({
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
})
