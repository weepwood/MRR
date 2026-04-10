import type { EncryptIDSearchParams } from '../types'
import api from '../index'

/** GET /api/v1/search/getBAHByID/{idCard} — 根据身份证号获取BAH */
export function getBAHByIdCard(idCard: string) {
  return api.get(`/api/v1/search/getBAHByID/${idCard}`)
}

/** GET /api/v1/search/getBAHByEncryptID — 根据加密身份证获取BAH */
export function getBAHByEncryptID(params: EncryptIDSearchParams) {
  return api.get('/api/v1/search/getBAHByEncryptID', { params })
}
