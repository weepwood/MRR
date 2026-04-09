import type { EncryptIDSearchParams } from '../types'
import api from '../index'

/** GET /v2/search/getBAHByID/{idCard} — 根据身份证号获取BAH */
export function getBAHByIdCard(idCard: string) {
  return api.get(`/v2/search/getBAHByID/${idCard}`)
}

/** GET /v2/search/getBAHByEncryptID — 根据加密身份证获取BAH */
export function getBAHByEncryptID(params: EncryptIDSearchParams) {
  return api.get('/v2/search/getBAHByEncryptID', { params })
}
