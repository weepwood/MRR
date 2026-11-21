import type { AxiosAdapter } from 'axios'
import { AxiosError, AxiosHeaders } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import api from '../index'
import { getRawRequest } from '../raw-response'

vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({
    isLogin: false,
    token: '',
  }),
}))

describe('raw API response requests', () => {
  it.each([
    ['/api/v1/statistics/export/csv', '统计导出超过单次 100000 条上限，请缩小筛选范围'],
    ['/api/v1/patients/export/excel', '患者导出超过单次 100000 条上限，请缩小筛选范围'],
  ])('surfaces the backend 413 message on Blob download %s', async (endpoint, message) => {
    const adapter: AxiosAdapter = async (config) => {
      const response = {
        data: new Blob([JSON.stringify({ code: 413, message })], { type: 'application/json' }),
        status: 413,
        statusText: 'Payload Too Large',
        headers: new AxiosHeaders({ 'content-type': 'application/json;charset=UTF-8' }),
        config,
        request: {},
      }
      throw new AxiosError('Request failed with status code 413', AxiosError.ERR_BAD_REQUEST, config, {}, response)
    }

    await expect(api.get<Blob>(endpoint, {
      adapter,
      responseType: 'blob',
      skipGlobalError: true,
      skipResponseMetrics: true,
    })).rejects.toMatchObject({ message })
  })

  it('preserves blob data and response headers through the global interceptor', async () => {
    const blob = new Blob(['bah,sjh\r\n001234,10001\r\n'], { type: 'text/csv' })
    const adapter: AxiosAdapter = async config => ({
      data: blob,
      status: 200,
      statusText: 'OK',
      headers: new AxiosHeaders({
        'content-disposition': 'attachment; filename="mr_patient.csv"',
        'x-export-row-limit': '100000',
      }),
      config,
      request: {},
    })

    const response = await getRawRequest<Blob>('/api/v1/data-exchange/patients/export/csv', {
      adapter,
      responseType: 'blob',
    })

    expect(response.data).toBe(blob)
    expect(response.headers).toBeInstanceOf(AxiosHeaders)
    expect((response.headers as AxiosHeaders).get('content-disposition')).toContain('mr_patient.csv')
    expect((response.headers as AxiosHeaders).get('x-export-row-limit')).toBe('100000')
  })
})
