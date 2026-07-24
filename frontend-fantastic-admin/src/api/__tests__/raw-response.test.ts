import type { AxiosAdapter } from 'axios'
import { AxiosHeaders } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import { getRawRequest } from '../raw-response'

vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({
    isLogin: false,
    token: '',
  }),
}))

describe('raw API response requests', () => {
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
