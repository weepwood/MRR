import type { GalleryImage } from '../types'
import { ElMessage } from 'element-plus'
import { ref } from 'vue'
import { createPdfFromImageUrls } from '../utils/client-pdf'

export function useArchivePrint() {
  const printing = ref(false)
  const exportingPdf = ref(false)

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

  function waitForImages(doc: Document): Promise<void> {
    return Promise.all(Array.from(doc.images).map((image) => {
      if (image.complete) {
        return Promise.resolve()
      }
      return new Promise<void>((resolve) => {
        image.addEventListener('load', () => resolve(), { once: true })
        image.addEventListener('error', () => resolve(), { once: true })
      })
    })).then(() => undefined)
  }

  async function printSelected(images: GalleryImage[]): Promise<void> {
    if (!images.length) {
      ElMessage.warning('请先选择要打印的影像')
      return
    }
    printing.value = true
    try {
      const iframe = document.createElement('iframe')
      iframe.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;'
      document.body.appendChild(iframe)
      const doc = iframe.contentWindow?.document
      const win = iframe.contentWindow
      if (!doc || !win) {
        document.body.removeChild(iframe)
        return
      }
      doc.open()
      doc.write(buildPrintHtml(images))
      doc.close()

      let printed = false
      const cleanup = () => {
        setTimeout(() => {
          if (iframe.parentNode) {
            document.body.removeChild(iframe)
          }
        }, 2000)
      }

      const print = () => {
        if (printed) {
          return
        }
        printed = true
        win.focus()
        win.print()
        cleanup()
      }

      await Promise.race([
        waitForImages(doc),
        new Promise<void>(resolve => setTimeout(resolve, 5000)),
      ])
      print()
    }
    catch (err: unknown) {
      ElMessage.error((err as { message?: string })?.message || '打印失败')
    }
    finally {
      printing.value = false
    }
  }

  function getArchiveKey(image: GalleryImage): string {
    return `${String(image.bah || '').trim()}|${String(image.sjh || '').trim()}`
  }

  async function exportSelectedPdf(images: GalleryImage[]): Promise<void> {
    if (!images.length) {
      ElMessage.warning('请先选择要导出的影像')
      return
    }

    const archiveKey = getArchiveKey(images[0])
    if (images.some(image => getArchiveKey(image) !== archiveKey)) {
      ElMessage.warning('选中的影像不属于同一个档案袋')
      return
    }

    const imageUrls = images.map(image => String(image.imageUrl || '').trim())
    if (imageUrls.some(url => !url)) {
      ElMessage.warning('部分影像缺少访问地址，无法导出 PDF')
      return
    }

    exportingPdf.value = true
    try {
      const pdfBlob = await createPdfFromImageUrls(imageUrls)
      const firstImage = images[0]
      const bah = String(firstImage?.bah || 'archive').trim() || 'archive'
      const sjh = String(firstImage?.sjh || '').trim()
      const fileName = `${bah}${sjh ? `-${sjh}` : ''}-selected.pdf`
      const url = URL.createObjectURL(pdfBlob)
      const link = document.createElement('a')
      link.href = url
      link.download = fileName
      document.body.appendChild(link)
      link.click()
      link.remove()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
      ElMessage.success(`已在浏览器中合成并导出 ${images.length} 张影像`)
    }
    catch (err: unknown) {
      const message = (err as { message?: string })?.message || 'PDF 导出失败'
      ElMessage.error(message.includes('Failed to fetch') ? '影像获取失败，请检查图片服务 CORS 配置' : message)
    }
    finally {
      exportingPdf.value = false
    }
  }

  return { printing, exportingPdf, printSelected, exportSelectedPdf }
}
