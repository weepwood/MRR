import type { ArchiveExportJob } from '@/api/modules/archive-export'
import { downloadArchiveExportJob } from '@/api/modules/archive-export'

const DOWNLOAD_CHUNK_BYTES = 8 * 1024 * 1024
const PREFIX_VERIFY_BYTES = 64 * 1024

interface FileSystemWritableFileStreamLike {
  write(data: Blob | { type: 'write', position: number, data: Blob }): Promise<void>
  truncate(size: number): Promise<void>
  close(): Promise<void>
}

interface FileSystemFileHandleLike {
  getFile(): Promise<File>
  createWritable(options?: { keepExistingData?: boolean }): Promise<FileSystemWritableFileStreamLike>
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
  if (extensionIndex <= 0) return `${source}-${suffix}`
  return `${source.slice(0, extensionIndex)}-${suffix}${source.slice(extensionIndex)}`
}

async function hasMatchingPrefix(jobId: string, existingFile: File): Promise<boolean> {
  if (existingFile.size <= 0) return true
  const verifyLength = Math.min(existingFile.size, PREFIX_VERIFY_BYTES)
  const [localBuffer, remoteBlob] = await Promise.all([
    existingFile.slice(0, verifyLength).arrayBuffer(),
    downloadArchiveExportJob(jobId, `bytes=0-${verifyLength - 1}`),
  ])
  if (remoteBlob.size !== verifyLength) return false
  const local = new Uint8Array(localBuffer)
  const remote = new Uint8Array(await remoteBlob.arrayBuffer())
  if (local.length !== remote.length) return false
  for (let index = 0; index < local.length; index++) {
    if (local[index] !== remote[index]) return false
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

export async function downloadExportJobWithResume(job: ArchiveExportJob): Promise<'resumable' | 'blob'> {
  const fileName = uniqueFileName(job)
  const totalBytes = Number(job.outputBytes || 0)
  const picker = (window as FilePickerWindow).showSaveFilePicker
  if (!picker || totalBytes <= 0) {
    return downloadAsBlob(job, fileName)
  }

  let writable: FileSystemWritableFileStreamLike | undefined
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
    let offset = existingFile.size
    if (offset > totalBytes || !(await hasMatchingPrefix(job.id, existingFile))) {
      offset = 0
    }

    writable = await handle.createWritable({ keepExistingData: true })
    if (offset === 0 && existingFile.size > 0) {
      await writable.truncate(0)
    }

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
    return 'resumable'
  }
  catch (error: unknown) {
    if ((error as { name?: string })?.name === 'AbortError') {
      throw error
    }
    return downloadAsBlob(job, fileName)
  }
  finally {
    if (writable) {
      try {
        await writable.close()
      }
      catch {
        // Range 下载失败时可能已回退普通下载，关闭失败不覆盖原始结果。
      }
    }
  }
}
