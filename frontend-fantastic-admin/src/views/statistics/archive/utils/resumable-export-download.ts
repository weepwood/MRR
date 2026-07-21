import type { ArchiveExportJob } from '@/api/modules/archive-export'
import { downloadArchiveExportJob } from '@/api/modules/archive-export'

const DOWNLOAD_CHUNK_BYTES = 8 * 1024 * 1024

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

export async function downloadExportJobWithResume(job: ArchiveExportJob): Promise<'resumable' | 'blob'> {
  const fileName = job.fileName || `archive-export-${job.id}.${job.format.toLowerCase()}`
  const totalBytes = Number(job.outputBytes || 0)
  const picker = (window as FilePickerWindow).showSaveFilePicker
  if (!picker || totalBytes <= 0) {
    const blob = await downloadArchiveExportJob(job.id)
    saveBlob(blob, fileName)
    return 'blob'
  }

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
  const writable = await handle.createWritable({ keepExistingData: true })
  let offset = existingFile.size
  if (offset > totalBytes) {
    await writable.truncate(0)
    offset = 0
  }

  try {
    while (offset < totalBytes) {
      const end = Math.min(totalBytes - 1, offset + DOWNLOAD_CHUNK_BYTES - 1)
      const expectedLength = end - offset + 1
      const chunk = await downloadArchiveExportJob(job.id, `bytes=${offset}-${end}`)
      if (chunk.size !== expectedLength) {
        throw new Error(`断点下载区间长度不一致：期望 ${expectedLength}，实际 ${chunk.size}`)
      }
      await writable.write({ type: 'write', position: offset, data: chunk })
      offset += chunk.size
    }
    await writable.truncate(totalBytes)
    return 'resumable'
  }
  finally {
    await writable.close()
  }
}
