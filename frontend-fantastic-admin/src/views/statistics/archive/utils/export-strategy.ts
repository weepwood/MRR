export const CLIENT_PDF_MAX_IMAGES = 20

export type ArchiveExportFormat = 'zip' | 'pdf'
export type ArchiveExportMode = 'client-pdf' | 'backend-stream'

export interface ArchiveExportStrategyInput {
  format: ArchiveExportFormat
  selectedCount: number
  totalCount: number
}

export function resolveArchiveExportMode(input: ArchiveExportStrategyInput): ArchiveExportMode {
  if (input.format === 'zip') {
    return 'backend-stream'
  }

  const selectedCount = Math.max(0, Math.trunc(input.selectedCount))
  const totalCount = Math.max(0, Math.trunc(input.totalCount))
  const isWholeArchive = totalCount > 0 && selectedCount === totalCount

  if (!isWholeArchive && selectedCount <= CLIENT_PDF_MAX_IMAGES) {
    return 'client-pdf'
  }

  return 'backend-stream'
}
