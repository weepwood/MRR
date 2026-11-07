import type { GalleryImage } from '../types'
import type { ArchiveSelectionChangedDetail } from './useSelection'
import { ElMessage } from 'element-plus'
import { getCurrentScope, onScopeDispose, ref } from 'vue'
import { downloadSelectedImagesPdf, getSelectedImagesPdfExportPlan } from '@/api/modules/archive-export'
import useAuth from '@/utils/composables/useAuth'
import { createPdfFromImageUrls } from '../utils/client-pdf'
import { useArchiveExportJob } from './useArchiveExportJob'
import { ARCHIVE_SELECTION_CHANGED_EVENT } from './useSelection'

function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function selectionSignature(images: GalleryImage[]): string {
  return images.map(image => String(image.id || image.filename || '').trim()).filter(Boolean).sort().join(',')
}

/** 只编排选页 PDF；后台任务状态、轮询和取消继续由 useArchiveExportJob 管理。 */
export function useArchivePdfExport() {
  const exportingPdf = ref(false)
  const { auth } = useAuth()
  const exportJob = useArchiveExportJob('PDF')
  let boundSelection: string | null = null
  let generation = 0
  let disposed = false

  function invalidate() {
    generation++
    boundSelection = null
    exportingPdf.value = false
    // 即使创建响应尚未返回，也须让已有任务机制失效并取消迟到任务。
    void exportJob.discard()
  }

  function onSelectionChanged(event: Event) {
    const detail = (event as CustomEvent<ArchiveSelectionChangedDetail>).detail
    const nextSignature = Array.isArray(detail?.keys) ? detail.keys.filter(Boolean).slice().sort().join(',') : ''
    if (boundSelection !== null && boundSelection !== nextSignature) { invalidate() }
  }

  async function exportSelectedPdf(selection: GalleryImage[]): Promise<void> {
    if (disposed) { return }
    if (!auth('record:pdf:export')) {
      ElMessage.warning('当前账号没有病案 PDF 导出权限')
      return
    }
    if (!selection.length) {
      ElMessage.warning('请先选择要导出的影像')
      return
    }

    // 规划、执行与保存使用同一次选择快照，不读取等待期间被修改的原对象。
    const images = selection.map(image => ({ ...image }))
    const nextSignature = selectionSignature(images)
    if (boundSelection !== null && boundSelection !== nextSignature) { invalidate() }
    if (exportingPdf.value) { return }

    const archiveKey = (image: GalleryImage) => `${String(image.bah || '').trim()}|${String(image.sjh || '').trim()}`
    if (images.some(image => archiveKey(image) !== archiveKey(images[0]))) {
      ElMessage.warning('选中的影像不属于同一个档案袋')
      return
    }
    const ids = images.map(image => image.id).filter((id): id is number => id !== undefined)
    if (ids.length !== images.length) {
      throw new Error('部分影像缺少记录 ID，无法生成 PDF')
    }

    const bah = String(images[0].bah || '').trim()
    const sjh = String(images[0].sjh || '').trim()
    const fileName = `${bah || 'archive'}${sjh ? `-${sjh}` : ''}-selected.pdf`
    boundSelection = nextSignature
    const token = ++generation
    const isCurrent = () => !disposed && token === generation
    exportingPdf.value = true
    try {
      const response = await getSelectedImagesPdfExportPlan(ids)
      if (!isCurrent()) { return }
      const mode = response?.data?.executionMode

      if (mode === 'CLIENT_PDF') {
        exportJob.dismiss()
        const imageUrls = images.map(image => String(image.imageUrl || '').trim())
        if (imageUrls.some(url => !url)) {
          ElMessage.warning('部分影像缺少访问地址，无法导出 PDF')
          return
        }
        const pdfBlob = await createPdfFromImageUrls(imageUrls)
        if (!isCurrent()) { return }
        saveBlob(pdfBlob, fileName)
        ElMessage.success(`已在浏览器中合成并导出 ${images.length} 张影像`)
        return
      }

      if (mode === 'BACKEND_JOB') {
        await exportJob.start({ format: 'PDF', bah: bah || undefined, sjh: sjh || undefined, ids })
        return
      }

      exportJob.dismiss()
      const pdfBlob = await downloadSelectedImagesPdf(ids)
      if (!isCurrent()) { return }
      saveBlob(pdfBlob, fileName)
      ElMessage.success(`已由服务器生成并导出 ${images.length} 张影像`)
    }
    catch (err: unknown) {
      if (!isCurrent()) { return }
      const message = (err as { message?: string })?.message || 'PDF 导出失败'
      ElMessage.error(message.includes('Failed to fetch') ? '影像获取失败，请检查图片服务配置' : message)
    }
    finally {
      if (isCurrent()) { exportingPdf.value = false }
    }
  }

  if (getCurrentScope()) {
    if (typeof window !== 'undefined') { window.addEventListener(ARCHIVE_SELECTION_CHANGED_EVENT, onSelectionChanged) }
    onScopeDispose(() => {
      disposed = true
      invalidate()
      if (typeof window !== 'undefined') { window.removeEventListener(ARCHIVE_SELECTION_CHANGED_EVENT, onSelectionChanged) }
    })
  }

  return {
    exportingPdf,
    pdfJob: exportJob.job,
    pdfJobCancelling: exportJob.cancelling,
    pdfJobDownloading: exportJob.downloading,
    exportSelectedPdf,
    cancelPdfJob: exportJob.cancel,
    downloadPreparedPdf: exportJob.download,
    dismissPdfJob: exportJob.dismiss,
  }
}
