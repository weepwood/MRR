import type { GalleryImage, PatientInfo } from '../types'
import { ElMessage } from 'element-plus'
import { ref } from 'vue'
import { getTypeLabel } from '../constants'

export function useArchivePrint() {
  const printing = ref(false)

  function buildPrintHtml(images: GalleryImage[], patient: PatientInfo | undefined, bah: string): string {
    const headerHtml = `
      <div class="print-header">
        <h1>影像档案袋</h1>
        <div class="patient-info">
          ${patient ? `<span>姓名：${patient.name || '-'}</span>` : ''}
          <span>病案号：${bah}</span>
          ${patient?.department ? `<span>科室：${patient.department}</span>` : ''}
          ${patient?.admissionTime ? `<span>入院：${patient.admissionTime}</span>` : ''}
          <span>共 ${images.length} 张</span>
          <span>打印：${new Date().toLocaleString('zh-CN')}</span>
        </div>
      </div>`

    const imagesHtml = images.map((img) => {
      const src = img.imageUrl || ''
      return `
        <div class="print-page">
          <div class="print-img-meta">
            <span>P${img.pages ?? '-'}</span>
            <span>${getTypeLabel(img.btype)}</span>
          </div>
          <img src="${src}" alt="" />
        </div>`
    }).join('')

    return `<!DOCTYPE html><html><head><meta charset="utf-8"><title>影像打印 - ${bah}</title>
      <style>
        @page { margin: 8mm; }
        * { box-sizing: border-box; }
        body { margin: 0; font-family: -apple-system, "Microsoft YaHei", sans-serif; color: #1e293b; }
        .print-header { text-align: center; padding: 6px 0 10px; border-bottom: 2px solid #1e293b; margin-bottom: 8px; }
        .print-header h1 { font-size: 16px; margin: 0 0 4px; }
        .patient-info { display: flex; flex-wrap: wrap; justify-content: center; gap: 4px 14px; font-size: 11px; color: #64748b; }
        .print-page { page-break-after: always; text-align: center; }
        .print-page:last-child { page-break-after: auto; }
        .print-img-meta { display: flex; justify-content: space-between; font-size: 10px; color: #94a3b8; padding: 2px 4px; }
        .print-page img { max-width: 100%; max-height: 88vh; object-fit: contain; }
      </style></head><body>${headerHtml}${imagesHtml}</body></html>`
  }

  async function printSelected(images: GalleryImage[], patient: PatientInfo | undefined, bah: string): Promise<void> {
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
      doc.write(buildPrintHtml(images, patient, bah))
      doc.close()

      const cleanup = () => {
        setTimeout(() => {
          if (iframe.parentNode) {
            document.body.removeChild(iframe)
          }
        }, 2000)
      }
      iframe.onload = () => {
        win.focus()
        win.print()
        cleanup()
      }
      setTimeout(() => {
        if (iframe.parentNode) {
          win.focus()
          win.print()
          cleanup()
        }
      }, 1500)
    }
    catch (err: unknown) {
      ElMessage.error((err as { message?: string })?.message || '打印失败')
    }
    finally {
      printing.value = false
    }
  }

  return { printing, printSelected }
}
