import type { ArchiveExportJob } from '@/api/modules/archive-export'
import { downloadArchiveExportJob } from '@/api/modules/archive-export'

const DOWNLOAD_CHUNK_BYTES = 8 * 1024 * 1024
const PREFIX_VERIFY_BYTES = 64 * 1024

interface FileSystemWritableFileStreamLike {
  write: (data: Blob | { type: 'write', position: number, data: Blob }) => Promise<void>
  truncate: (size: number) => Promise<void>
  close: () => Promise<void>
}

interface FileSystemFileHandleLike {
  getFile: () => Promise<File>
  createWritable: (options?: { keepExistingData?: boolean }) => Promise<FileSystemWritableFileStreamLike>
}

interface FilePickerWindow extends Window {
  showSaveFilePicker?: (options: {
    suggestedName: string
    types?: Array<{ description: string, accept: Record<string, string[]> }>
  }) => Promise<FileSystemFileHandleLike>
}

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

function uniqueFileName(job: ArchiveExportJob): string {
  const fallback = `archive-export.${job.format.toLowerCase()}`
  const source = job.fileName || fallback
  const extensionIndex = source.lastIndexOf('.')
  const suffix = job.id.slice(0, 8)
  if (extensionIndex <= 0) {
    return `${source}-${suffix}`
  }
  return `${source.slice(0, extensionIndex)}-${suffix}${source.slice(extensionIndex)}`
}

function readBlobAsArrayBuffer(blob: Blob): Promise<ArrayBuffer> {
  if (typeof blob.arrayBuffer === 'function') {
    return blob.arrayBuffer()
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.addEventListener('load', () => resolve(reader.result as ArrayBuffer), { once: true })
    reader.addEventListener('error', () => reject(reader.error || new Error('读取文件内容失败')), { once: true })
    reader.readAsArrayBuffer(blob)
  })
}

async function hasMatchingPrefix(jobId: string, existingFile: File): Promise<boolean> {
  if (existingFile.size <= 0) {
    return true
  }
  const verifyLength = Math.min(existingFile.size, PREFIX_VERIFY_BYTES)
  const [localBuffer, remoteBlob] = await Promise.all([
    readBlobAsArrayBuffer(existingFile.slice(0, verifyLength)),
    downloadArchiveExportJob(jobId, `bytes=0-${verifyLength - 1}`),
  ])
  if (remoteBlob.size !== verifyLength) {
    return false
  }
  const local = new Uint8Array(localBuffer)
  const remote = new Uint8Array(await readBlobAsArrayBuffer(remoteBlob))
  if (local.length !== remote.length) {
    return false
  }
  for (let index = 0; index < local.length; index++) {
    if (local[index] !== remote[index]) {
      return false
    }
  }
  return true
}

async function downloadAsBlob(job: ArchiveExportJob, fileName: string): Promise<'blob'> {
  const blob = await downloadArchiveExportJob(job.id)
  if (!(blob instanceof Blob) || blob.size <= 0) {
    throw new Error('服务器返回的导出文件为空')
  }
  const expectedBytes = Number(job.outputBytes || 0)
  if (expectedBytes > 0 && blob.size !== expectedBytes) {
    throw new Error(`导出文件长度不一致：期望 ${expectedBytes}，实际 ${blob.size}`)
  }
  saveBlob(blob, fileName)
  return 'blob'
}

/** 后台任务完成后直接交给浏览器下载，避免额外弹出文件保存对话框。 */
export function downloadExportJobToBrowser(job: ArchiveExportJob): Promise<'blob'> {
  return downloadAsBlob(job, uniqueFileName(job))
}

export async function downloadExportJobWithResume(job: ArchiveExportJob): Promise<'resumable' | 'blob'> {
  const fileName = uniqueFileName(job)
  const totalBytes = Number(job.outputBytes || 0)
  const picker = (window as FilePickerWindow).showSaveFilePicker
  if (!picker || totalBytes <= 0) {
    return downloadAsBlob(job, fileName)
  }

  let writable: FileSystemWritableFileStreamLike | undefined
  let originalSize = 0
  let completed = false
  try {
    const mimeType = job.format === 'PDF' ? 'application/pdf' : 'application/zip'
    const extension = job.format === 'PDF' ? '.pdf' : '.zip'
    const handle = await picker({
      suggestedName: fileName,
      types: [{
        description: `${job.format} 病案导出文件`,
        accept: { [mimeType]: [extension] },
      }],
    })
    const existingFile = await handle.getFile()
    originalSize = existingFile.size

    // 文件不是当前任务的有效前缀时，不修改用户选择的原文件，直接走普通下载。
    if (originalSize > totalBytes || !(await hasMatchingPrefix(job.id, existingFile))) {
      return downloadAsBlob(job, fileName)
    }

    let offset = originalSize
    writable = await handle.createWritable({ keepExistingData: true })
    while (offset < totalBytes) {
      const end = Math.min(totalBytes - 1, offset + DOWNLOAD_CHUNK_BYTES - 1)
      const expectedLength = end - offset + 1
      const chunk = await downloadArchiveExportJob(job.id, `bytes=${offset}-${end}`)
      if (!(chunk instanceof Blob) || chunk.size !== expectedLength) {
        throw new Error(`断点下载区间长度不一致：期望 ${expectedLength}，实际 ${chunk instanceof Blob ? chunk.size : 0}`)
      }
      await writable.write({ type: 'write', position: offset, data: chunk })
      offset += chunk.size
    }
    await writable.truncate(totalBytes)
    await writable.close()
    writable = undefined
    completed = true
    return 'resumable'
  }
  catch (error: unknown) {
    if ((error as { name?: string })?.name === 'AbortError') {
      throw error
    }
    if (writable) {
      try {
        // 只会在已验证的远端前缀后追加，失败时恢复到原始长度即可避免残缺文件。
        await writable.truncate(originalSize)
        await writable.close()
      }
      catch {
        // 恢复失败不覆盖后续普通下载结果，但调用方仍会得到完整文件校验。
      }
      writable = undefined
    }
    return downloadAsBlob(job, fileName)
  }
  finally {
    if (writable && !completed) {
      try {
        await writable.truncate(originalSize)
        await writable.close()
      }
      catch {
        // 页面退出或浏览器写入异常时尽力恢复原文件长度。
      }
    }
  }
}
