import JSZip from 'jszip'

export interface ClientZipImage {
  imageUrl?: string
  filename?: string
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

function toArrayBuffer(blob: Blob): Promise<ArrayBuffer> {
  if (typeof blob.arrayBuffer === 'function') {
    return blob.arrayBuffer()
  }

  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.addEventListener('load', () => resolve(reader.result as ArrayBuffer), { once: true })
    reader.addEventListener('error', () => reject(reader.error || new Error('影像读取失败')), { once: true })
    reader.readAsArrayBuffer(blob)
  })
}

function getFileName(image: ClientZipImage, index: number, usedNames: Set<string>): string {
  const originalName = String(image.filename || '').trim().split(/[\\/]/).at(-1)
  const fallbackName = `image-${String(index + 1).padStart(3, '0')}.jpg`
  const fileName = originalName || fallbackName
  const extensionIndex = fileName.lastIndexOf('.')
  const baseName = extensionIndex > 0 ? fileName.slice(0, extensionIndex) : fileName
  const extension = extensionIndex > 0 ? fileName.slice(extensionIndex) : ''

  let candidate = fileName
  let duplicateIndex = 2
  while (usedNames.has(candidate)) {
    candidate = `${baseName}-${duplicateIndex}${extension}`
    duplicateIndex += 1
  }
  usedNames.add(candidate)
  return candidate
}

export async function createArchiveZip(
  images: ClientZipImage[],
  onProgress?: (completed: number, total: number) => void,
): Promise<Blob> {
  const downloadableImages = images.filter(image => String(image.imageUrl || '').trim())
  if (!downloadableImages.length) {
    throw new Error('当前档案没有可下载的影像')
  }

  const zip = new JSZip()
  const usedNames = new Set<string>()
  for (let index = 0; index < downloadableImages.length; index += 1) {
    const image = downloadableImages[index]
    const blob = await fetchImageBlob(String(image.imageUrl).trim())
    zip.file(getFileName(image, index, usedNames), new Uint8Array(await toArrayBuffer(blob)))
    onProgress?.(index + 1, downloadableImages.length)
  }

  return zip.generateAsync({
    type: 'blob',
    compression: 'DEFLATE',
    compressionOptions: { level: 6 },
  })
}
