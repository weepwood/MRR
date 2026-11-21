export const NGINX_BROWSER_MAX_FILE_BYTES = 128 * 1024 * 1024
export const FILE_LIMIT_MESSAGE = '浏览器文件读取上限为 128 MiB。请缩小文件，或关闭已打开的文件标签页后重试。'

const openedFiles = new Map<Window, { url: string, size: number }>()
let cleanupTimer: number | undefined

function releaseClosedFiles() {
  for (const [tab, file] of openedFiles) {
    if (tab.closed) {
      URL.revokeObjectURL(file.url)
      openedFiles.delete(tab)
    }
  }
  if (!openedFiles.size && cleanupTimer !== undefined) {
    window.clearInterval(cleanupTimer)
    cleanupTimer = undefined
  }
}

export function availableFileTabBytes() {
  releaseClosedFiles()
  let bytes = NGINX_BROWSER_MAX_FILE_BYTES
  for (const { size } of openedFiles.values()) {
    bytes -= size
  }
  return bytes
}

export function showFileInTab(tab: Window, blob: Blob) {
  if (tab.closed) {
    return
  }
  const url = URL.createObjectURL(blob)
  try {
    tab.location.href = url
  }
  catch (error) {
    URL.revokeObjectURL(url)
    throw error
  }
  openedFiles.set(tab, { url, size: blob.size })
  cleanupTimer ??= window.setInterval(releaseClosedFiles, 1000)
}
