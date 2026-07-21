import type { AxiosResponse } from 'axios'
import api from '../index'

export function downloadArchiveZip(bah?: string, sjh?: string) {
  return api.get<Blob, AxiosResponse<Blob>>('/api/v1/archive-exports/zip', {
    params: { bah: bah || undefined, sjh: sjh || undefined },
    responseType: 'blob',
    timeout: 1000 * 60 * 10,
  })
}

export function downloadArchivePdf(bah?: string, sjh?: string) {
  return api.get<Blob, AxiosResponse<Blob>>('/api/v1/archive-exports/pdf', {
    params: { bah: bah || undefined, sjh: sjh || undefined },
    responseType: 'blob',
    timeout: 1000 * 60 * 10,
  })
}

export function downloadSelectedImagesPdf(ids: Array<string | number>) {
  return api.post<Blob, AxiosResponse<Blob>>(
    '/api/v1/archive-exports/pdf/selection',
    { ids: ids.map(String) },
    {
      responseType: 'blob',
      timeout: 1000 * 60 * 10,
    },
  )
}
