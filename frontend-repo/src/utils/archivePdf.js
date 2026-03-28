const PAGE_WIDTH_MM = 210
const PAGE_HEIGHT_MM = 297

const escapeHtml = (value) =>
  String(value ?? '').replace(/[&<>"']/g, (char) => {
    const replacements = {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    }
    return replacements[char] || char
  })

const sanitizeFileName = (value) => String(value || 'archive').replace(/[\\/:*?"<>|]+/g, '_').trim()

const waitForImage = (imageEl) =>
  new Promise((resolve, reject) => {
    if (!imageEl) {
      resolve()
      return
    }

    if (imageEl.complete && imageEl.naturalWidth > 0) {
      resolve()
      return
    }

    imageEl.onload = () => resolve()
    imageEl.onerror = () => reject(new Error('图片加载失败'))
  })

const createStyle = () => {
  const style = document.createElement('style')
  style.textContent = `
    .archive-pdf-root {
      position: fixed;
      left: -10000px;
      top: 0;
      width: ${PAGE_WIDTH_MM}mm;
      min-height: ${PAGE_HEIGHT_MM}mm;
      color: #1f2937;
      background: #f4efe7;
      z-index: -1;
      overflow: hidden;
    }

    .archive-pdf-page {
      width: ${PAGE_WIDTH_MM}mm;
      height: ${PAGE_HEIGHT_MM}mm;
      box-sizing: border-box;
      padding: 16mm 15mm 14mm;
      background:
        radial-gradient(circle at top right, rgba(255, 255, 255, 0.68), transparent 30%),
        linear-gradient(180deg, #fffaf2 0%, #f3ede3 100%);
      page-break-after: always;
      break-after: page;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      gap: 10mm;
    }

    .archive-pdf-page:last-child {
      page-break-after: auto;
      break-after: auto;
    }

    .archive-pdf-cover {
      justify-content: space-between;
    }

    .archive-pdf-brand {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 8px 14px;
      border-radius: 999px;
      background: rgba(15, 23, 42, 0.08);
      width: fit-content;
      font-size: 13px;
      font-weight: 700;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }

    .archive-pdf-cover h1 {
      margin: 0;
      font-size: 28px;
      line-height: 1.2;
      letter-spacing: 0.04em;
    }

    .archive-pdf-cover p {
      margin: 0;
      color: #475569;
      line-height: 1.6;
    }

    .archive-pdf-cover-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 10px;
    }

    .archive-pdf-meta {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 14px 16px;
      border-radius: 18px;
      background: rgba(255, 255, 255, 0.8);
      border: 1px solid rgba(148, 163, 184, 0.22);
    }

    .archive-pdf-meta span:first-child {
      font-size: 12px;
      color: #64748b;
    }

    .archive-pdf-meta span:last-child {
      font-size: 15px;
      font-weight: 700;
      color: #0f172a;
      word-break: break-all;
    }

    .archive-pdf-cover-footer {
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
      gap: 16px;
      padding-top: 8px;
      border-top: 1px solid rgba(148, 163, 184, 0.18);
      color: #334155;
      font-size: 13px;
    }

    .archive-pdf-badge {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 10px 14px;
      border-radius: 999px;
      background: linear-gradient(135deg, #0f172a, #1f2937);
      color: #fff;
      font-weight: 700;
      letter-spacing: 0.06em;
    }

    .archive-pdf-image-page {
      justify-content: space-between;
    }

    .archive-pdf-page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 16px;
    }

    .archive-pdf-page-no {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 8px 14px;
      border-radius: 999px;
      background: rgba(15, 23, 42, 0.08);
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.08em;
    }

    .archive-pdf-page-title {
      margin-top: 8px;
      font-size: 18px;
      font-weight: 800;
      color: #0f172a;
    }

    .archive-pdf-page-subtitle {
      margin-top: 6px;
      font-size: 13px;
      color: #475569;
      line-height: 1.5;
    }

    .archive-pdf-frame {
      flex: 1;
      min-height: 0;
      border-radius: 24px;
      padding: 12px;
      background:
        linear-gradient(180deg, rgba(255,255,255,0.92), rgba(255,255,255,0.68)),
        linear-gradient(135deg, rgba(12, 74, 110, 0.08), rgba(15, 23, 42, 0.05));
      border: 1px solid rgba(148, 163, 184, 0.2);
      box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .archive-pdf-image {
      width: 100%;
      height: 100%;
      object-fit: contain;
      border-radius: 18px;
      background: #fff;
    }

    .archive-pdf-page-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      padding-top: 6px;
      border-top: 1px solid rgba(148, 163, 184, 0.18);
      color: #475569;
      font-size: 12px;
    }
  `
  return style
}

const buildRoot = ({ title, record, images }) => {
  const root = document.createElement('div')
  root.className = 'archive-pdf-root'

  const style = createStyle()
  root.appendChild(style)

  const cover = document.createElement('section')
  cover.className = 'archive-pdf-page archive-pdf-cover'
  cover.innerHTML = `
    <div>
      <div class="archive-pdf-brand">Archive Export</div>
      <h1>${escapeHtml(title)}</h1>
      <p>本文件由档案图片页生成，用于打印和归档留存。</p>
    </div>

    <div class="archive-pdf-cover-grid">
      <div class="archive-pdf-meta">
        <span>病案号</span>
        <span>${escapeHtml(record.bah || '-')}</span>
      </div>
      <div class="archive-pdf-meta">
        <span>档案类型</span>
        <span>${escapeHtml(record.type || '-')}</span>
      </div>
      <div class="archive-pdf-meta">
        <span>归档日期</span>
        <span>${escapeHtml(record.date || '-')}</span>
      </div>
      <div class="archive-pdf-meta">
        <span>图片数量</span>
        <span>${escapeHtml(images.length)}</span>
      </div>
      <div class="archive-pdf-meta">
        <span>扫描设备</span>
        <span>${escapeHtml(record.cid || '-')}</span>
      </div>
      <div class="archive-pdf-meta">
        <span>扫描人员</span>
        <span>${escapeHtml(record.openerNo || '-')}</span>
      </div>
    </div>

    <div class="archive-pdf-cover-footer">
      <div>
        <div class="archive-pdf-badge">${escapeHtml(record.bah || '未指定病案')}</div>
        <p style="margin-top: 10px;">导出时间：${escapeHtml(new Date().toLocaleString('zh-CN'))}</p>
      </div>
      <div style="text-align: right;">
        <p>请在打印对话框中选择“保存为 PDF”即可留存。</p>
      </div>
    </div>
  `
  root.appendChild(cover)

  images.forEach((img, index) => {
    const page = document.createElement('section')
    page.className = 'archive-pdf-page archive-pdf-image-page'
    page.innerHTML = `
      <div class="archive-pdf-page-header">
        <div>
          <div class="archive-pdf-page-no">PAGE ${String(index + 1).padStart(2, '0')}</div>
          <div class="archive-pdf-page-title">P${escapeHtml(img.pages ?? index + 1)} - ${escapeHtml(img.typeName || img.typeLabel || img.btypeLabel || img.btype || '未分类')}</div>
          <div class="archive-pdf-page-subtitle">
            病案号 ${escapeHtml(record.bah || '-')} · 归档日期 ${escapeHtml(record.date || '-')}
          </div>
        </div>
      </div>

      <div class="archive-pdf-frame">
        <img class="archive-pdf-image" alt="图片 ${index + 1}" />
      </div>

      <div class="archive-pdf-page-footer">
        <span>扫描设备：${escapeHtml(record.cid || '-')}</span>
        <span>扫描人员：${escapeHtml(record.openerNo || '-')}</span>
        <span>图片 ${index + 1} / ${images.length}</span>
      </div>
    `

    const imageEl = page.querySelector('img')
    if (imageEl) {
      imageEl.crossOrigin = 'anonymous'
      imageEl.src = img.blobUrl || img.renderUrl || img.displayUrl || img.cx || ''
    }

    root.appendChild(page)
  })

  return root
}

export async function exportArchiveImagesToPdf({ images = [], record = {}, title = '病案档案图片', fileName }) {
  if (!Array.isArray(images) || images.length === 0) {
    throw new Error('没有可导出的图片')
  }

  const [{ default: html2canvas }, { jsPDF }] = await Promise.all([
    import('html2canvas'),
    import('jspdf')
  ])

  const root = buildRoot({
    title,
    record,
    images
  })

  document.body.appendChild(root)

  try {
    const imageEls = Array.from(root.querySelectorAll('img'))
    await Promise.all(imageEls.map(waitForImage))

    const pdf = new jsPDF({
      orientation: 'portrait',
      unit: 'mm',
      format: 'a4',
      compress: true
    })

    const pages = Array.from(root.querySelectorAll('.archive-pdf-page'))

    for (let index = 0; index < pages.length; index += 1) {
      const canvas = await html2canvas(pages[index], {
        scale: 2,
        useCORS: true,
        backgroundColor: '#f4efe7'
      })

      const imageData = canvas.toDataURL('image/png')
      if (index > 0) {
        pdf.addPage()
      }
      pdf.addImage(imageData, 'PNG', 0, 0, PAGE_WIDTH_MM, PAGE_HEIGHT_MM)
    }

    const resolvedFileName = sanitizeFileName(
      fileName || `${record.bah || 'archive'}-images.pdf`
    )
    pdf.save(resolvedFileName)
  } finally {
    if (root.parentNode) {
      root.parentNode.removeChild(root)
    }
  }
}
