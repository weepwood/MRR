import type { PaginatedResult } from '../types'
import api, { getRequest, postRequest } from '../index'

export interface PatientRecord {
  id?: number
  idCard?: string
  bah?: string
  name?: string
  ruyuan?: string
  admissiontime?: string
  department?: string
  bingqu?: string
  chuangwei?: string
}

export interface PatientImportError {
  rowNumber: number
  field: string
  message: string
  value: string
}

export interface PatientImportResult {
  fileName: string
  encoding: string
  dryRun: boolean
  canImport: boolean
  totalRows: number
  validRows: number
  insertedRows: number
  duplicateRows: number
  errorRows: number
  errorsTruncated: boolean
  errors: PatientImportError[]
}

export interface PatientDateCount {
  date: string
  count: number
}

export interface PatientDepartmentCount {
  department: string
  count: number
}

export interface PatientAnalyticsSummary {
  year: number
  totalRecords: number
  totalArchives: number
  yearArchives: number
  missingIdCardRecords: number
  confirmedMultiRecordGroups: number
  suspectedMultiRecordGroups: number
  dateCounts: PatientDateCount[]
  departmentCounts: PatientDepartmentCount[]
}

export interface PatientMultiRecordGroup {
  matchType: 'IDCARD' | 'NAME_ONLY'
  confidence: 'HIGH' | 'LOW'
  patientName: string
  maskedIdCard?: string
  recordCount: number
  archiveCount: number
  archiveNumbers: string[]
  firstAdmissionDate?: string
  lastAdmissionDate?: string
}

/** GET /api/v1/patients — 分页查询患者列表 */
export function getPatients(params: { page: number, size: number, keyword?: string }) {
  return getRequest<PaginatedResult<PatientRecord>>('/api/v1/patients', { params })
}

/** GET /api/v1/patients/analytics/summary — 患者数据质量与年度统计 */
export function getPatientAnalyticsSummary(year: number) {
  return getRequest<PatientAnalyticsSummary>('/api/v1/patients/analytics/summary', {
    params: { year },
  })
}

/** GET /api/v1/patients/analytics/missing-idcard — 身份证号为空的病案 */
export function getMissingIdCardPatients(params: { page: number, size: number }) {
  return getRequest<PaginatedResult<PatientRecord>>('/api/v1/patients/analytics/missing-idcard', { params })
}

/** GET /api/v1/patients/analytics/multi-record-groups — 同一患者多病案分组 */
export function getPatientMultiRecordGroups(params: {
  page: number
  size: number
  includeSuspected: boolean
}) {
  return getRequest<PaginatedResult<PatientMultiRecordGroup>>('/api/v1/patients/analytics/multi-record-groups', { params })
}

/** POST /api/v1/patients/import — 校验或正式导入患者文件 */
export function importPatients(file: File, dryRun: boolean) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('dryRun', String(dryRun))
  return postRequest<PatientImportResult>('/api/v1/patients/import', formData, {
    timeout: 5 * 60 * 1000,
  })
}

/** GET /api/v1/patients/export/excel — 导出患者列表 */
export function exportPatientsExcel(keyword?: string) {
  return api.get<Blob>('/api/v1/patients/export/excel', {
    params: keyword ? { keyword } : undefined,
    responseType: 'blob',
  })
}

/** GET /api/v1/patients/bah/{bah} — 根据病案号查询 */
export function getPatientByBah(bah: string) {
  return getRequest<PatientRecord[]>(`/api/v1/patients/bah/${bah}`)
}

/** GET /api/v1/patients/idcard/{idCard} — 根据身份证号查询 */
export function getPatientByIdCard(idCard: string) {
  return getRequest<PatientRecord[]>(`/api/v1/patients/idcard/${idCard}`)
}
