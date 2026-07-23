import api, { postRequest } from '../index'

export type DataExchangeDataset = 'patients' | 'statistics' | 'archive-boxes' | 'scan'
export type DataExchangeExportDataset = DataExchangeDataset | 'archives'

export interface DataExchangeImportError {
  rowNumber: number
  field: string
  message: string
  value: string
}

export interface DataExchangeImportResult {
  dataset: 'MR_PATIENT' | 'MR_STATISTICS' | 'MR_ARCHIVE_BOX_RECORD' | 'MR_SCAN'
  fileName: string
  encoding: string
  dryRun: boolean
  canImport: boolean
  totalRows: number
  validRows: number
  insertedRows: number
  updatedRows: number
  duplicateRows: number
  errorRows: number
  errorsTruncated: boolean
  errors: DataExchangeImportError[]
}

export interface PatientExchangeFilters {
  keyword?: string
}

export interface StatisticsExchangeFilters {
  keyword?: string
  bah?: string
  sjh?: string
  type?: string
  startDate?: string
  endDate?: string
}

export interface ArchiveExchangeFilters {
  keyword?: string
  bah?: string
  sjh?: string
  patientId?: string
  type?: string
  startDate?: string
  endDate?: string
}

export interface ArchiveBoxExchangeFilters {
  keyword?: string
  bah?: string
  sjh?: string
  boxNo?: string
  status?: string
}

export interface ScanExchangeFilters {
  bah?: string
  sjh?: string
  brxh?: string
  folder?: string
  filename?: string
  btype?: number
  afterId?: number
}

export function importDataExchangeFile(dataset: DataExchangeDataset, file: File, dryRun: boolean) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('dryRun', String(dryRun))
  return postRequest<DataExchangeImportResult>(`/api/v1/data-exchange/${dataset}/import`, formData, {
    timeout: 10 * 60 * 1000,
  })
}

export function downloadDataExchangeTemplate(dataset: DataExchangeDataset) {
  return api.get<Blob>(`/api/v1/data-exchange/${dataset}/template`, {
    responseType: 'blob',
  })
}

export function exportDataExchangeCsv(
  dataset: DataExchangeExportDataset,
  params: Record<string, string | number | undefined>,
) {
  return api.get<Blob>(`/api/v1/data-exchange/${dataset}/export/csv`, {
    params,
    responseType: 'blob',
    timeout: 5 * 60 * 1000,
  })
}
