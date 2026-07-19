export interface ClientPdfImage {
  blob: Blob
  width: number
  height: number
}

const A4_PORTRAIT = { width: 595.28, height: 841.89 }
const A4_LANDSCAPE = { width: 841.89, height: 595.28 }
const PAGE_MARGIN = 18
const MAX_CANVAS_EDGE = 8192
const MAX_CANVAS_PIXELS = 40_000_000

function encodeText(value: string): Uint8Array {
  return new TextEncoder().encode(value)
}

function formatNumber(value: number): string {
  return Number(value.toFixed(2)).toString()
}

async function fetchImageBlob(imageUrl: string): Promise<Blob> {
  const resolvedUrl = new URL(imageUrl, window.location.href)
  const response = await fetch(resolvedUrl.toString(), {
    method: 'GET',
    mode: 'cors',
    credentials: 'omit',
  })

  if (!response.ok) {
    throw new Error(`影像获取失败（HTTP ${response.status}）`)
  }

  const blob = await response.blob()
  if (!blob.size) {
    throw new Error('影像文件为空')
  }
  return blob
}

async function loadImageSource(blob: Blob): Promise<{
  source: CanvasImageSource
  width: number
  height: number
  cleanup: () => void
}> {
  if (typeof createImageBitmap === 'function') {
    const bitmap = await createImageBitmap(blob)
    return {
      source: bitmap,
      width: bitmap.width,
      height: bitmap.height,
      cleanup: () => bitmap.close(),
    }
  }

  const objectUrl = URL.createObjectURL(blob)
  const image = new Image()
  image.src = objectUrl
  await image.decode()
  return {
    source: image,
    width: image.naturalWidth,
    height: image.naturalHeight,
    cleanup: () => URL.revokeObjectURL(objectUrl),
  }
}

function calculateCanvasSize(width: number, height: number) {
  const edgeScale = Math.min(1, MAX_CANVAS_EDGE / width, MAX_CANVAS_EDGE / height)
  const pixelScale = Math.min(1, Math.sqrt(MAX_CANVAS_PIXELS / (width * height)))
  const scale = Math.min(edgeScale, pixelScale)
  return {
    width: Math.max(1, Math.round(width * scale)),
    height: Math.max(1, Math.round(height * scale)),
  }
}

async function convertToJpeg(blob: Blob): Promise<ClientPdfImage> {
  const loaded = await loadImageSource(blob)
  try {
    if (!loaded.width || !loaded.height) {
      throw new Error('无法识别影像尺寸')
    }

    const size = calculateCanvasSize(loaded.width, loaded.height)
    const canvas = document.createElement('canvas')
    canvas.width = size.width
    canvas.height = size.height
    const context = canvas.getContext('2d', { alpha: false })
    if (!context) {
      throw new Error('浏览器无法创建影像画布')
    }

    context.fillStyle = '#fff'
    context.fillRect(0, 0, size.width, size.height)
    context.drawImage(loaded.source, 0, 0, size.width, size.height)

    const jpegBlob = await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob(
        result => result ? resolve(result) : reject(new Error('影像转换失败')),
        'image/jpeg',
        0.92,
      )
    })

    return { blob: jpegBlob, width: size.width, height: size.height }
  }
  finally {
    loaded.cleanup()
  }
}

export function buildPdfBlob(images: ClientPdfImage[]): Blob {
  if (!images.length) {
    throw new Error('没有可写入 PDF 的影像')
  }

  const objectCount = 2 + images.length * 3
  const offsets = Array.from<number>({ length: objectCount + 1 }).fill(0)
  const parts: BlobPart[] = []
  let byteLength = 0

  const appendBytes = (bytes: Uint8Array) => {
    const copy = new Uint8Array(bytes.byteLength)
    copy.set(bytes)
    parts.push(copy.buffer)
    byteLength += copy.byteLength
  }
  const appendText = (text: string) => appendBytes(encodeText(text))
  const appendBlob = (blob: Blob) => {
    parts.push(blob)
    byteLength += blob.size
  }
  const beginObject = (id: number) => {
    offsets[id] = byteLength
    appendText(`${id} 0 obj\n`)
  }
  const endObject = () => appendText('endobj\n')

  appendBytes(new Uint8Array([
    0x25,
    0x50,
    0x44,
    0x46,
    0x2D,
    0x31,
    0x2E,
    0x34,
    0x0A,
    0x25,
    0xE2,
    0xE3,
    0xCF,
    0xD3,
    0x0A,
  ]))

  beginObject(1)
  appendText('<< /Type /Catalog /Pages 2 0 R >>\n')
  endObject()

  const pageRefs = images.map((_, index) => `${3 + index * 3} 0 R`).join(' ')
  beginObject(2)
  appendText(`<< /Type /Pages /Count ${images.length} /Kids [${pageRefs}] >>\n`)
  endObject()

  images.forEach((image, index) => {
    const pageObjectId = 3 + index * 3
    const imageObjectId = pageObjectId + 1
    const contentObjectId = pageObjectId + 2
    const page = image.width > image.height ? A4_LANDSCAPE : A4_PORTRAIT
    const scale = Math.min(
      (page.width - PAGE_MARGIN * 2) / image.width,
      (page.height - PAGE_MARGIN * 2) / image.height,
    )
    const drawWidth = image.width * scale
    const drawHeight = image.height * scale
    const x = (page.width - drawWidth) / 2
    const y = (page.height - drawHeight) / 2
    const content = `q\n${formatNumber(drawWidth)} 0 0 ${formatNumber(drawHeight)} ${formatNumber(x)} ${formatNumber(y)} cm\n/Im0 Do\nQ\n`
    const contentBytes = encodeText(content)

    beginObject(pageObjectId)
    appendText(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${formatNumber(page.width)} ${formatNumber(page.height)}] /Resources << /ProcSet [/PDF /ImageC] /XObject << /Im0 ${imageObjectId} 0 R >> >> /Contents ${contentObjectId} 0 R >>\n`)
    endObject()

    beginObject(imageObjectId)
    appendText(`<< /Type /XObject /Subtype /Image /Width ${image.width} /Height ${image.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Interpolate true /Length ${image.blob.size} >>\nstream\n`)
    appendBlob(image.blob)
    appendText('\nendstream\n')
    endObject()

    beginObject(contentObjectId)
    appendText(`<< /Length ${contentBytes.byteLength} >>\nstream\n`)
    appendBytes(contentBytes)
    appendText('endstream\n')
    endObject()
  })

  const xrefOffset = byteLength
  appendText(`xref\n0 ${objectCount + 1}\n`)
  appendText('0000000000 65535 f \n')
  for (let id = 1; id <= objectCount; id += 1) {
    appendText(`${String(offsets[id]).padStart(10, '0')} 00000 n \n`)
  }
  appendText(`trailer\n<< /Size ${objectCount + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`)

  return new Blob(parts, { type: 'application/pdf' })
}

export async function createPdfFromImageUrls(
  imageUrls: string[],
  onProgress?: (completed: number, total: number) => void,
): Promise<Blob> {
  const preparedImages: ClientPdfImage[] = []
  for (let index = 0; index < imageUrls.length; index += 1) {
    const imageBlob = await fetchImageBlob(imageUrls[index])
    preparedImages.push(await convertToJpeg(imageBlob))
    onProgress?.(index + 1, imageUrls.length)
  }
  return buildPdfBlob(preparedImages)
}
