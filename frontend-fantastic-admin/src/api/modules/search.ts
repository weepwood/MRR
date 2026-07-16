import type { BAHRecord, EncryptIDSearchParams } from '../types'
import { getRequest, postRequest } from '../index'

export interface PatientSearchRecord extends BAHRecord {
  ruyuan?: string
  binqu?: string
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
  binqu?: string
  chuangwei?: string
}

export interface IdCardArchiveSearchResponse {
  token: string
  maskedIdCard: string
  cases: IdCardArchiveCase[]
}

/** GET /api/v1/search/getBAHByID/{idCard} — 根据身份证号获取BAH（旧接口） */
export function getBAHByIdCard(idCard: string) {
  return getRequest<PatientSearchRecord>(`/api/v1/search/getBAHByID/${idCard}`)
}

/** GET /api/v1/search/getBAHByEncryptID — 根据加密身份证获取BAH */
export function getBAHByEncryptID(params: EncryptIDSearchParams) {
  return getRequest<PatientSearchRecord>('/api/v1/search/getBAHByEncryptID', { params })
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
