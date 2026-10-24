import type { BAHRecord } from '../types'
import { getRequest, postRequest } from '../index'

export interface PatientSearchRecord extends BAHRecord {
  ruyuan?: string
  bingqu?: string
  chuangwei?: string
}

export interface IdCardArchiveCase {
  patientRecordId?: number
  bah?: string
  sjh?: string
  name?: string
  ruyuan?: string
  admissionTime?: string
  department?: string
  bingqu?: string
  chuangwei?: string
}

export interface IdCardArchiveSearchResponse {
  token: string
  maskedIdCard: string
  cases: IdCardArchiveCase[]
}

/** POST /api/v1/search/archive-cases — 查询身份证对应全部影像档案并生成 URL 令牌 */
export function getArchiveCasesByIdCard(idCard: string) {
  return postRequest<IdCardArchiveSearchResponse>('/api/v1/search/archive-cases', { idCard })
}

/** GET /api/v1/search/archive-cases?id=... — 从混淆 URL 令牌恢复身份证查询 */
export function getArchiveCasesByToken(token: string) {
  return getRequest<IdCardArchiveSearchResponse>('/api/v1/search/archive-cases', {
    params: { id: token },
  })
}

/** GET /api/v1/search/patient/{bah} — 根据病案号查询患者信息 */
export function getPatientByBah(bah: string) {
  return getRequest<PatientSearchRecord>(`/api/v1/search/patient/${bah}`)
}
