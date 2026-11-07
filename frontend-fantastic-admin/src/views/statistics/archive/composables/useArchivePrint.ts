import type { GalleryImage } from '../types'
import type { ArchiveSelectionChangedDetail } from './useSelection'
import { ElMessage } from 'element-plus'
import { getCurrentScope, onScopeDispose, ref } from 'vue'
import {
  downloadSelectedImagesPdf,
  getSelectedImagesPdfExportPlan,
} from '@/api/modules/archive-export'
import { ARCHIVE_PRINT_MAX_IMAGES, requireArchiveExportBlob } from '@/utils/archive-export-budget'
import useAuth from '@/utils/composables/useAuth'
import { createPdfFromImageUrls } from '../utils/client-pdf'
import { useArchiveExportJob } from './useArchiveExportJob'
import { ARCHIVE_SELECTION_CHANGED_EVENT } from './useSelection'

function saveBlob(blob: Blob, fileName: string) {
  requireArchiveExportBlob(blob)
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
  return images
    .map(image => String(image.id || image.filename || '').trim())
    .filter(Boolean)
    .sort()
    .join(',')
}

export function useArchivePrint() {
  const printing = ref(false)
  const exportingPdf = ref(false)
  const { auth } = useAuth()
  const exportJob = useArchiveExportJob('PDF')
  const jobSelectionSignature = ref('')

  function escapeAttribute(value: string): string {
    return value.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;')
  }

  function buildPrintHtml(images: GalleryImage[]): string {
    const imagesHtml = images.map((img) => {
      const src = escapeAttribute(img.imageUrl || '')
      return `
        <div class="print-page">
          <img src="${src}" alt="" />
        </div>`
    }).join('')

    return `<!DOCTYPE html><html><head><meta charset="utf-8"><title>影像打印</title>
      <style>
        @page { margin: 0; }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { margin: 0; }
        .print-page { break-after: page; page-break-after: always; display: flex; align-items: center; justify-content: center; height: 100vh; }
        .print-page:last-child { break-after: auto; page-break-after: auto; }
        .print-page img { max-width: 100%; max-height: 100vh; object-fit: contain; }
      </style></head><body>${imagesHtml}</body></html>`
  }

  let disposed = false
  let stopWaiting: (() => void) | undefined
  let cleanupPrint: (() => void) | undefined

  function waitForImages(doc: Document): Promise<void> {
    return new Promise((resolve, reject) => {
      const images = Array.from(doc.images)
      const listeners: Array<() => void> = []
      let remaining = images.filter(image => !image.complete).length
      let settled = false
      let timer: ReturnType<typeof setTimeout> | undefined
      const finish = (error?: Error) => {
        if (settled) { return }
        settled = true
        clearTimeout(timer)
        listeners.forEach(remove => remove())
        stopWaiting = undefined
        if (error) { reject(error) }
        else { resolve() }
      }
      const failed = () => finish(new Error('影像未完整加载'))
      stopWaiting = () => finish(new DOMException('打印已取消', 'AbortError'))
      timer = setTimeout(failed, 5000)
      if (!images.length) { failed(); return }
      for (const image of images) {
        if (image.complete) {
          if (image.naturalWidth <= 0) { failed(); return }
          continue
        }
        const loaded = () => {
          if (image.naturalWidth <= 0) { failed(); return }
          remaining--
          if (remaining === 0) { finish() }
        }
        image.addEventListener('load', loaded, { once: true })
        image.addEventListener('error', failed, { once: true })
        listeners.push(() => {
          image.removeEventListener('load', loaded)
          image.removeEventListener('error', failed)
        })
      }
      if (remaining === 0) { finish() }
    })
  }

  async function printSelected(images: GalleryImage[]): Promise<void> {
    if (disposed || printing.value || cleanupPrint) { return }
    if (!images.length) {
      ElMessage.warning('请先选择要打印的影像')
      return
    }
    if (images.length > ARCHIVE_PRINT_MAX_IMAGES) {
      ElMessage.warning(`单次最多打印 ${ARCHIVE_PRINT_MAX_IMAGES} 张影像，请减少选页并分批打印`)
      return
    }
    if (images.some(image => !image.imageUrl?.trim())) {
      ElMessage.warning('部分影像缺少访问地址，请刷新后重试')
      return
    }
    printing.value = true
    try {
      const iframe = document.createElement('iframe')
      iframe.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;'
      let cleanupTimer: ReturnType<typeof setTimeout> | undefined
      let win: Window | null = null
      const cleanup = () => {
        clearTimeout(cleanupTimer)
        stopWaiting?.()
        win?.removeEventListener('afterprint', cleanup)
        iframe.remove()
        if (cleanupPrint === cleanup) { cleanupPrint = undefined }
      }
      cleanupPrint = cleanup
      document.body.appendChild(iframe)
      win = iframe.contentWindow
      const doc = win?.document
      if (!win || !doc) { throw new Error('无法创建打印窗口') }
      doc.open()
      doc.write(buildPrintHtml(images))
      doc.close()
      await waitForImages(doc)
      if (disposed) { cleanup(); return }
      win.addEventListener('afterprint', cleanup, { once: true })
      win.focus()
      win.print()
      // 保留原有打印返回后的2秒兜底；afterprint/离页/异常会提前清理。
      if (iframe.isConnected) { cleanupTimer = setTimeout(cleanup, 2000) }
    }
    catch {
      cleanupPrint?.()
      if (!disposed) { ElMessage.error('影像未完整加载或打印未完成，请重试或减少选页分批打印') }
    }
    finally {
      printing.value = false
    }
  }

  function getArchiveKey(image: GalleryImage): string {
    return `${String(image.bah || '').trim()}|${String(image.sjh || '').trim()}`
  }

  function invalidateMismatchedJob(nextSignature: string) {
    if (exportJob.job.value && jobSelectionSignature.value !== nextSignature) {
      void exportJob.discard()
      jobSelectionSignature.value = ''
    }
  }

  function bindSelection(images: GalleryImage[]) {
    const nextSignature = selectionSignature(images)
    invalidateMismatchedJob(nextSignature)
    return nextSignature
  }

  function onSelectionChanged(event: Event) {
    const detail = (event as CustomEvent<ArchiveSelectionChangedDetail>).detail
    const nextSignature = Array.isArray(detail?.keys)
      ? detail.keys.filter(Boolean).slice().sort().join(',')
      : ''
    invalidateMismatchedJob(nextSignature)
  }

  async function exportSelectedPdf(images: GalleryImage[]): Promise<void> {
    if (!auth('record:pdf:export')) {
      ElMessage.warning('当前账号没有病案 PDF 导出权限')
      return
    }
    if (!images.length) {
      ElMessage.warning('请先选择要导出的影像')
      return
    }

    const nextSelectionSignature = bindSelection(images)
    const archiveKey = getArchiveKey(images[0])
    if (images.some(image => getArchiveKey(image) !== archiveKey)) {
      ElMessage.warning('选中的影像不属于同一个档案袋')
      return
    }

    const ids = images
      .map(image => image.id)
      .filter((id): id is number => id !== undefined)
    if (ids.length !== images.length) {
      throw new Error('部分影像缺少记录 ID，无法生成 PDF')
    }

    const firstImage = images[0]
    const bah = String(firstImage?.bah || '').trim()
    const sjh = String(firstImage?.sjh || '').trim()
    const fileStem = bah || 'archive'
    const fileName = `${fileStem}${sjh ? `-${sjh}` : ''}-selected.pdf`

    exportingPdf.value = true
    try {
      // 规划和执行均绑定相同的 ID 集合，哪怕用户恰好选中了当前全部图片。
      const response = await getSelectedImagesPdfExportPlan(ids)
      const mode = response?.data?.executionMode

      if (mode === 'CLIENT_PDF') {
        exportJob.dismiss()
        jobSelectionSignature.value = ''
        const imageUrls = images.map(image => String(image.imageUrl || '').trim())
        if (imageUrls.some(url => !url)) {
          ElMessage.warning('部分影像缺少访问地址，无法导出 PDF')
          return
        }
        const pdfBlob = await createPdfFromImageUrls(imageUrls)
        saveBlob(pdfBlob, fileName)
        ElMessage.success(`已在浏览器中合成并导出 ${images.length} 张影像`)
        return
      }

      if (mode === 'BACKEND_JOB') {
        jobSelectionSignature.value = nextSelectionSignature
        await exportJob.start({
          format: 'PDF',
          bah: bah || undefined,
          sjh: sjh || undefined,
          ids,
        })
        return
      }

      exportJob.dismiss()
      jobSelectionSignature.value = ''
      const pdfBlob = await downloadSelectedImagesPdf(ids)
      saveBlob(pdfBlob, fileName)
      ElMessage.success(`已由服务器生成并导出 ${images.length} 张影像`)
    }
    catch (err: unknown) {
      const message = (err as { message?: string })?.message || 'PDF 导出失败'
      ElMessage.error(message.includes('Failed to fetch') ? '影像获取失败，请检查图片服务配置' : message)
    }
    finally {
      exportingPdf.value = false
    }
  }

  if (typeof window !== 'undefined' && getCurrentScope()) {
    window.addEventListener(ARCHIVE_SELECTION_CHANGED_EVENT, onSelectionChanged)
    onScopeDispose(() => {
      disposed = true
      cleanupPrint?.()
      window.removeEventListener(ARCHIVE_SELECTION_CHANGED_EVENT, onSelectionChanged)
    })
  }

  return {
    printing,
    exportingPdf,
    pdfJob: exportJob.job,
    pdfJobCancelling: exportJob.cancelling,
    pdfJobDownloading: exportJob.downloading,
    printSelected,
    exportSelectedPdf,
    cancelPdfJob: exportJob.cancel,
    downloadPreparedPdf: exportJob.download,
    dismissPdfJob: exportJob.dismiss,
  }
}
