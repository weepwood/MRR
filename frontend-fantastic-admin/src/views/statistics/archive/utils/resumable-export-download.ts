import type { ArchiveExportJob } from '@/api/modules/archive-export'
import { downloadArchiveExportJob } from '@/api/modules/archive-export'
import { ArchiveExportBudgetError, requireArchiveExportSize } from '@/utils/archive-export-budget'
import { isRequestCanceled } from '@/utils/request-retry'

const DOWNLOAD_CHUNK_BYTES = 8 * 1024 * 1024
const PREFIX_VERIFY_BYTES = 64 * 1024

interface FileSystemWritableFileStreamLike {
  write: (data: Blob | { type: 'write', position: number, data: Blob }) => Promise<void>
  truncate: (size: number) => Promise<void>
  close: () => Promise<void>
  abort?: () => Promise<void>
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

function assertCurrent(isCurrent: () => boolean) {
  if (!isCurrent()) {
    throw new DOMException('导出下载已失效', 'AbortError')
  }
}

async function hasMatchingPrefix(jobId: string, existingFile: File, isCurrent: () => boolean): Promise<boolean> {
  assertCurrent(isCurrent)
  if (existingFile.size <= 0) {
    return true
  }
  const verifyLength = Math.min(existingFile.size, PREFIX_VERIFY_BYTES)
  const [localBuffer, remoteBlob] = await Promise.all([
    readBlobAsArrayBuffer(existingFile.slice(0, verifyLength)),
    downloadArchiveExportJob(jobId, `bytes=0-${verifyLength - 1}`),
  ])
  assertCurrent(isCurrent)
  if (remoteBlob.size !== verifyLength) {
    return false
  }
  const local = new Uint8Array(localBuffer)
  const remote = new Uint8Array(await readBlobAsArrayBuffer(remoteBlob))
  assertCurrent(isCurrent)
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

async function downloadAsBlob(job: ArchiveExportJob, fileName: string, isCurrent: () => boolean): Promise<'blob'> {
  assertCurrent(isCurrent)
  const expectedBytes = requireArchiveExportSize(job.outputBytes)
  const blob = await downloadArchiveExportJob(job.id)
  assertCurrent(isCurrent)
  if (!(blob instanceof Blob) || blob.size <= 0) {
    throw new Error('服务器返回的导出文件为空')
  }
  if (blob.size !== expectedBytes) {
    throw new Error(`导出文件长度不一致：期望 ${expectedBytes}，实际 ${blob.size}`)
  }
  saveBlob(blob, fileName)
  return 'blob'
}

/** 后台任务完成后直接交给浏览器下载，避免额外弹出文件保存对话框。 */
export function downloadExportJobToBrowser(job: ArchiveExportJob, isCurrent: () => boolean = () => true): Promise<'blob'> {
  return downloadAsBlob(job, uniqueFileName(job), isCurrent)
}

export async function downloadExportJobWithResume(job: ArchiveExportJob, isCurrent: () => boolean = () => true): Promise<'resumable' | 'blob'> {
  assertCurrent(isCurrent)
  const fileName = uniqueFileName(job)
  const totalBytes = requireArchiveExportSize(job.outputBytes)
  const picker = (window as FilePickerWindow).showSaveFilePicker
  if (!picker) {
    return downloadAsBlob(job, fileName, isCurrent)
  }

  let writable: FileSystemWritableFileStreamLike | undefined
  let originalSize = 0
  let closeStarted = false

  async function restoreWritable(): Promise<boolean> {
    if (!writable) { return true }
    const stream = writable
    writable = undefined
    // close 已开始提交后，abort 只能尽力释放，不能证明提交失败已恢复。
    if (closeStarted) {
      try { await stream.abort?.() }
      catch { /* 文件提交结果不明，禁止普通下载回退掩盖失败。 */ }
      return false
    }
    if (!isCurrent() && stream.abort) {
      try {
        await stream.abort()
        return true
      }
      catch { /* 暂存放弃失败时，继续尝试恢复原长度。 */ }
    }
    let restoreCloseStarted = false
    try {
      await stream.truncate(originalSize)
      restoreCloseStarted = true
      await stream.close()
      return true
    }
    catch {
      try {
        if (stream.abort) {
          await stream.abort()
          return !restoreCloseStarted
        }
      }
      catch { /* 交由调用方报告恢复失败；禁止以普通下载成功掩盖。 */ }
      return false
    }
  }
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
    assertCurrent(isCurrent)
    const existingFile = await handle.getFile()
    assertCurrent(isCurrent)
    originalSize = existingFile.size

    // 文件不是当前任务的有效前缀时，不修改用户选择的原文件，直接走普通下载。
    if (originalSize > totalBytes || !(await hasMatchingPrefix(job.id, existingFile, isCurrent))) {
      return downloadAsBlob(job, fileName, isCurrent)
    }

    assertCurrent(isCurrent)
    let offset = originalSize
    writable = await handle.createWritable({ keepExistingData: true })
    assertCurrent(isCurrent)
    while (offset < totalBytes) {
      assertCurrent(isCurrent)
      const end = Math.min(totalBytes - 1, offset + DOWNLOAD_CHUNK_BYTES - 1)
      const expectedLength = end - offset + 1
      const chunk = await downloadArchiveExportJob(job.id, `bytes=${offset}-${end}`)
      assertCurrent(isCurrent)
      if (!(chunk instanceof Blob) || chunk.size !== expectedLength) {
        throw new Error(`断点下载区间长度不一致：期望 ${expectedLength}，实际 ${chunk instanceof Blob ? chunk.size : 0}`)
      }
      await writable.write({ type: 'write', position: offset, data: chunk })
      assertCurrent(isCurrent)
      offset += chunk.size
    }
    await writable.truncate(totalBytes)
    assertCurrent(isCurrent)
    closeStarted = true
    await writable.close()
    writable = undefined
    assertCurrent(isCurrent)
    return 'resumable'
  }
  catch (error: unknown) {
    const restored = await restoreWritable()
    // 普通网络错误也可能在操作失效后返回；恢复等待期间同样可能切页。
    assertCurrent(isCurrent)
    if (error instanceof ArchiveExportBudgetError || (error as { name?: string })?.name === 'AbortError' || isRequestCanceled(error)
      || (error as { response?: { status?: number } })?.response?.status !== undefined) {
      throw error
    }
    if (!restored) {
      throw new Error('断点下载中断且原文件恢复失败，请检查所选文件后重试')
    }
    return downloadAsBlob(job, fileName, isCurrent)
  }
  finally {
    await restoreWritable()
  }
}
