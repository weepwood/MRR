import type { EffectScope } from 'vue'
import type { GalleryImage } from '../../types'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, effectScope, ref } from 'vue'
import { useArchivePrint } from '../useArchivePrint'
import { useSelection } from '../useSelection'

const api = vi.hoisted(() => ({
  getSelectedImagesPdfExportPlan: vi.fn(),
  createArchiveExportJob: vi.fn(),
  cancelArchiveExportJob: vi.fn(),
  getArchiveExportJob: vi.fn(),
  downloadSelectedImagesPdf: vi.fn(),
}))
vi.mock('@/api/modules/archive-export', () => api)
vi.mock('@/utils/composables/useAuth', () => ({ default: () => ({ auth: () => true }) }))
vi.mock('element-plus', () => ({ ElMessage: { warning: vi.fn(), error: vi.fn(), info: vi.fn(), success: vi.fn() } }))

describe('档案袋选择与PDF实例隔离', () => {
  const scopes: EffectScope[] = []
  function page(id: number) {
    const scope = effectScope()
    scopes.push(scope)
    return scope.run(() => {
      const images = ref<GalleryImage[]>([{ id, bah: 'TEST', imageUrl: `/test-${id}.jpg` }])
      const selection = useSelection(computed(() => images.value))
      selection.toggleSelect(images.value[0])
      const pdf = useArchivePrint(selection.selectedItems)
      return { images, selection, pdf }
    })!
  }
  beforeEach(() => {
    vi.resetAllMocks()
    api.getSelectedImagesPdfExportPlan.mockResolvedValue({ data: { executionMode: 'BACKEND_JOB' } })
    api.createArchiveExportJob.mockImplementation(async ({ ids }) => ({
      data: { id: `job-${ids[0]}`, status: 'SUCCESS', format: 'PDF', outputBytes: 3 },
    }))
  })
  afterEach(() => {
    scopes.splice(0).forEach(scope => scope.stop())
    vi.restoreAllMocks()
  })

  it('b改选只使B失效，不清除A的可重新下载任务', async () => {
    const a = page(1)
    const b = page(2)
    await a.pdf.exportSelectedPdf(a.selection.selectedItems.value)
    await b.pdf.exportSelectedPdf(b.selection.selectedItems.value)
    expect(a.pdf.pdfJob.value?.id).toBe('job-1')
    b.selection.clear()
    expect(b.pdf.pdfJob.value).toBeNull()
    expect(a.pdf.pdfJob.value?.id).toBe('job-1')
  })

  it('新的B选择实例初始化不取消A正在运行的任务', async () => {
    api.createArchiveExportJob.mockResolvedValue({ data: { id: 'a-pending', status: 'PENDING', format: 'PDF' } })
    const a = page(1)
    await a.pdf.exportSelectedPdf(a.selection.selectedItems.value)
    page(2)
    expect(a.pdf.pdfJob.value?.id).toBe('a-pending')
    expect(api.cancelArchiveExportJob).not.toHaveBeenCalled()
  })

  it('所属影像集合失效使当前PDF立即失效', async () => {
    const a = page(1)
    await a.pdf.exportSelectedPdf(a.selection.selectedItems.value)
    a.images.value = []
    expect(a.pdf.pdfJob.value).toBeNull()
  })

  it('旧全局事件不再控制页面PDF任务', async () => {
    const a = page(1)
    await a.pdf.exportSelectedPdf(a.selection.selectedItems.value)
    window.dispatchEvent(new CustomEvent('mrr:archive-selection-changed', { detail: { keys: ['999'] } }))
    expect(a.pdf.pdfJob.value?.id).toBe('job-1')
  })

  it('选择操作不向window广播记录ID', () => {
    const dispatch = vi.spyOn(window, 'dispatchEvent')
    const a = page(1)
    a.selection.clear()
    expect(dispatch).not.toHaveBeenCalled()
  })
})
