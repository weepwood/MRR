import api from '../index'
import type { ScanRequest } from '../types'

/** GET /v1/scan-api/page — 分页获取扫描记录 */
export function getScanList(params: { page: number, size: number }) {
  return api.get('/v1/scan-api/page', { params })
}

/** POST /v1/scan-api/page/condition — 根据条件分页查询扫描记录 */
export function getScanByCondition(request: Partial<ScanRequest>, page: number = 1, size: number = 10) {
  return api.post('/v1/scan-api/page/condition', request, {
    params: { page, size },
  })
}

/** GET /v1/scan-api/bah/{bah} — 根据病案号查询扫描记录 */
export function getScanByBah(bah: string) {
  return api.get(`/v1/scan-api/bah/${bah}`)
}

/** GET /v1/scan-api/brxh/{brxh} — 根据病人序号查询扫描记录 */
export function getScanByBrxh(brxh: string) {
  return api.get(`/v1/scan-api/brxh/${brxh}`)
}

/** GET /v1/scan-api/{id} — 根据ID查询扫描记录 */
export function getScanById(id: string | number) {
  return api.get(`/v1/scan-api/${id}`)
}

/** POST /v1/scan-api — 创建新的扫描记录 */
export function createScan(data: ScanRequest) {
  return api.post('/v1/scan-api', data)
}

/** PUT /v1/scan-api/{id} — 更新扫描记录 */
export function updateScan(id: string | number, data: Partial<ScanRequest>) {
  return api.put(`/v1/scan-api/${id}`, data)
}

/** DELETE /v1/scan-api/{id} — 删除扫描记录 */
export function deleteScan(id: string | number) {
  return api.delete(`/v1/scan-api/${id}`)
}

/** POST /v1/scan-api/batch-download — 批量打包下载 */
export function batchDownloadRecords(ids: (string | number)[]) {
  return api.post('/v1/scan-api/batch-download', { ids }, {
    responseType: 'blob',
  })
}

/** POST /v1/scan-api/condition — 根据条件查询(无分页) */
export function findByCondition(request: ScanRequest) {
  return api.post('/v1/scan-api/condition', request)
}
