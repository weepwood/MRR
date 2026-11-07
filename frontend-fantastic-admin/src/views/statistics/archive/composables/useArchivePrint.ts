import type { Ref } from 'vue'
import type { GalleryImage } from '../types'
import { ElMessage } from 'element-plus'
import { getCurrentScope, onScopeDispose, ref } from 'vue'
import { ARCHIVE_PRINT_MAX_IMAGES } from '@/utils/archive-export-budget'
import { useArchivePdfExport } from './useArchivePdfExport'

export function useArchivePrint(selectedImages?: Readonly<Ref<readonly GalleryImage[]>>) {
  const printing = ref(false)
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

  if (getCurrentScope()) {
    onScopeDispose(() => {
      disposed = true
      cleanupPrint?.()
    })
  }
  return {
    printing,
    printSelected,
    ...useArchivePdfExport(selectedImages),
  }
}
