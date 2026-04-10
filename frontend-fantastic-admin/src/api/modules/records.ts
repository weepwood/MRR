import type { ScanRequest } from '../types'
import api from '../index'

/** GET /api/v1/scan/page — 分页获取扫描记录 */
export function getScanList(params: { page: number, size: number }) {
  return api.get('/api/v1/scan/page', { params })
}

/** POST /api/v1/scan/page/condition — 根据条件分页查询扫描记录 */
export function getScanByCondition(request: Partial<ScanRequest>, page: number = 1, size: number = 10) {
  return api.post('/api/v1/scan/page/condition', request, {
    params: { page, size },
  })
}

/** GET /api/v1/scan/bah/{bah} — 根据病案号查询扫描记录 */
export function getScanByBah(bah: string) {
  return api.get(`/api/v1/scan/bah/${bah}`)
}

/** GET /api/v1/scan/brxh/{brxh} — 根据病人序号查询扫描记录 */
export function getScanByBrxh(brxh: string) {
  return api.get(`/api/v1/scan/brxh/${brxh}`)
}

/** GET /api/v1/scan/{id} — 根据ID查询扫描记录 */
export function getScanById(id: string | number) {
  return api.get(`/api/v1/scan/${id}`)
}

/** POST /api/v1/scan — 创建新的扫描记录 */
export function createScan(data: ScanRequest) {
  return api.post('/api/v1/scan', data)
}

/** PUT /api/v1/scan/{id} — 更新扫描记录 */
export function updateScan(id: string | number, data: Partial<ScanRequest>) {
  return api.put(`/api/v1/scan/${id}`, data)
}

/** DELETE /api/v1/scan/{id} — 删除扫描记录 */
export function deleteScan(id: string | number) {
  return api.delete(`/api/v1/scan/${id}`)
}

/** POST /api/v1/scan/batch-download — 批量打包下载 */
export function batchDownloadRecords(ids: (string | number)[]) {
  return api.post('/api/v1/scan/batch-download', { ids }, {
    responseType: 'blob',
  })
}

/** POST /api/v1/scan/condition — 根据条件查询(无分页) */
export function findByCondition(request: ScanRequest) {
  return api.post('/api/v1/scan/condition', request)
}
