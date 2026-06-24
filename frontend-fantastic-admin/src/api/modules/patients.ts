import type { PaginatedResult } from '../types'
import api, { getRequest } from '../index'

export interface PatientRecord {
  id?: number
  idCard?: string
  bah?: string
  name?: string
  admissiontime?: string
  department?: string
}

/** GET /api/v1/patients — 分页查询患者列表 */
export function getPatients(params: { page: number, size: number, keyword?: string }) {
  return getRequest<PaginatedResult<PatientRecord>>('/api/v1/patients', { params })
}

/** GET /api/v1/patients/bah/{bah} — 根据病案号查询 */
export function getPatientByBah(bah: string) {
  return getRequest<PatientRecord[]>(`/api/v1/patients/bah/${bah}`)
}

/** GET /api/v1/patients/idcard/{idCard} — 根据身份证号查询 */
export function getPatientByIdCard(idCard: string) {
  return getRequest<PatientRecord[]>(`/api/v1/patients/idcard/${idCard}`)
}

/** GET /api/v1/patients/export/excel — 导出患者列表为 Excel */
export function exportPatientsExcel(params?: { keyword?: string }) {
  return api.get('/api/v1/patients/export/excel', { params, responseType: 'blob' })
}
