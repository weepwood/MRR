/** 首轮上线预算；提高上限前必须完成目标终端和下载链路验收。 */
export const ARCHIVE_EXPORT_MAX_BYTES = 128 * 1024 * 1024
export const ARCHIVE_PRINT_MAX_IMAGES = 50
export const ARCHIVE_EXPORT_BUDGET_MESSAGE = '导出文件超过首轮 128 MiB 上限，请减少选页并分批导出'

export function requireArchiveExportSize(bytes: unknown): number {
  if (typeof bytes !== 'number' || !Number.isSafeInteger(bytes) || bytes <= 0) {
    throw new Error('导出文件大小未知或无效，请刷新任务后重试，或减少选页分批导出')
  }
  if (bytes > ARCHIVE_EXPORT_MAX_BYTES) {
    throw new Error(ARCHIVE_EXPORT_BUDGET_MESSAGE)
  }
  return bytes
}

export function requireArchiveExportBlob(blob: Blob): Blob {
  if (!(blob instanceof Blob) || blob.size <= 0) {
    throw new Error('服务器返回的导出文件为空')
  }
  requireArchiveExportSize(blob.size)
  return blob
}
