import api from '../index'

export function getScanList(params: {
  page: number
  size: number
  bah?: string
  brxh?: string
  openerNo?: string
  btype?: string
  uploadFlag?: string
}) {
  return api.get('/v1/records/scan', { params })
}

export function getScanByCondition(params: {
  bah?: string
  brxh?: string
  openerNo?: string
  btype?: string
  uploadFlag?: string
}) {
  return api.get('/v1/records/scan/condition', { params })
}

export function batchDownloadRecords(ids: (string | number)[]) {
  return api.post('/v1/records/batch-download', { ids }, { responseType: 'blob' })
}
