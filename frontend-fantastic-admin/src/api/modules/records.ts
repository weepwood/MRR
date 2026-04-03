import api from '../index'

export function getRecords(params: any = {}) {
  return api.get('/scan-api/page', { params })
}

export function getRecordByBah(bah: string) {
  return api.get(`/scan-api/${bah}`)
}

export function updateRecord(id: string | number, data: any) {
  return api.put(`/scan-api/${id}`, data)
}

export function deleteRecord(id: string | number) {
  return api.delete(`/scan-api/${id}`)
}

export function batchDownloadRecords(ids: (string | number)[]) {
  return api.post('/scan-api/batch-download', { ids }, { responseType: 'blob' })
}

export function getScanById(id: string | number) {
  return api.get(`/scan-api/${id}`)
}

export function getScanList(params: any = {}) {
  return api.get('/scan-api/page', { params })
}

export function getScanByCondition(request: any = {}, page = 1, size = 10) {
  return api.post('/scan-api/page/condition', request, {
    params: { page, size }
  })
}

export function getScanByBah(bah: string) {
  return api.get(`/scan-api/bah/${bah}`)
}

export function getScanByBrxh(brxh: string) {
  return api.get(`/scan-api/brxh/${brxh}`)
}

export function createScan(data: any) {
  return api.post('/scan-api', data)
}

export function updateScan(id: string | number, data: any) {
  return api.put(`/scan-api/${id}`, data)
}

export function deleteScan(id: string | number) {
  return api.delete(`/scan-api/${id}`)
}
