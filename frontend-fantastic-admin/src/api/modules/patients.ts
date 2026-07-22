import type { PaginatedResult } from '../types'
import { getRequest, postRequest } from '../index'

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

/** GET /api/v1/patients — 分页查询患者列表 */
export function getPatients(params: { page: number, size: number, keyword?: string }) {
  return getRequest<PaginatedResult<PatientRecord>>('/api/v1/patients', { params })
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

/** GET /api/v1/patients/bah/{bah} — 根据病案号查询 */
export function getPatientByBah(bah: string) {
  return getRequest<PatientRecord[]>(`/api/v1/patients/bah/${bah}`)
}

/** GET /api/v1/patients/idcard/{idCard} — 根据身份证号查询 */
export function getPatientByIdCard(idCard: string) {
  return getRequest<PatientRecord[]>(`/api/v1/patients/idcard/${idCard}`)
}
