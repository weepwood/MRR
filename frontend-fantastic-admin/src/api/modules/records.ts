import api from '../index'

export function getRecords(params: any = {}) {
  return api.get('/v1/scan-api/page', { params })
}

export function getRecordByBah(bah: string) {
  return api.get(`/v1/scan-api/bah/${bah}`)
}

export function updateRecord(id: string | number, data: any) {
  return api.put(`/v1/scan-api/${id}`, data)
}

export function deleteRecord(id: string | number) {
  return api.delete(`/v1/scan-api/${id}`)
}

export function batchDownloadRecords(ids: (string | number)[]) {
  return api.post('/v1/scan-api/batch-download', { ids }, { responseType: 'blob' })
}

export function getScanById(id: string | number) {
  return api.get(`/v1/scan-api/${id}`)
}

export function getScanList(params: any = {}) {
  return api.get('/v1/scan-api/page', { params })
}

export function getScanByCondition(request: any = {}, page = 1, size = 10) {
  return api.post('/v1/scan-api/page/condition', request, {
    params: { page, size },
  })
}

export function getScanByBah(bah: string) {
  return api.get(`/v1/scan-api/bah/${bah}`)
}

export function getScanByBrxh(brxh: string) {
  return api.get(`/v1/scan-api/brxh/${brxh}`)
}

export function createScan(data: any) {
  return api.post('/v1/scan-api', data)
}

export function updateScan(id: string | number, data: any) {
  return api.put(`/v1/scan-api/${id}`, data)
}

export function deleteScan(id: string | number) {
  return api.delete(`/v1/scan-api/${id}`)
}
