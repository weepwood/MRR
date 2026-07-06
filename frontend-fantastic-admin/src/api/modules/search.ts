import type { BAHRecord, EncryptIDSearchParams } from '../types'
import { getRequest } from '../index'

/** GET /api/v1/search/getBAHByID/{idCard} — 根据身份证号获取BAH */
export function getBAHByIdCard(idCard: string) {
  return getRequest<BAHRecord>(`/api/v1/search/getBAHByID/${idCard}`)
}

/** GET /api/v1/search/getBAHByEncryptID — 根据加密身份证获取BAH */
export function getBAHByEncryptID(params: EncryptIDSearchParams) {
  return getRequest<BAHRecord>('/api/v1/search/getBAHByEncryptID', { params })
}

/** GET /api/v1/search/patient/{bah} — 根据病案号查询患者信息 */
export function getPatientByBah(bah: string) {
  return getRequest<BAHRecord>(`/api/v1/search/patient/${bah}`)
}
