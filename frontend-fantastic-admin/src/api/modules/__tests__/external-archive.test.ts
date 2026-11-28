import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearExternalArchiveSession,
  downloadExternalArchive,
  exchangeExternalArchiveTicket,
  externalArchiveSession,
  getExternalArchiveContext,
  getExternalArchiveImages,
  setExternalArchiveSession,
} from '../external-archive'

const transport = vi.hoisted(() => {
  const instance = { get: vi.fn(), post: vi.fn(), interceptors: { response: { use: vi.fn() } } }
  return { instance, create: vi.fn((_config: unknown) => instance) }
})
vi.mock('axios', () => ({ default: { create: transport.create } }))
const synthetic = {
  clientId: 'synthetic-client',
  externalUserId: 'synthetic-user',
  allowDownload: true,
  expiresIn: 60,
  cases: [{ bah: '000123', sjh: '000456' }],
}
const clientConfiguration = transport.create.mock.calls[0]?.[0]
const [onResponse, onFailure] = transport.instance.interceptors.response.use.mock.calls[0]

beforeEach(() => {
  clearExternalArchiveSession()
  transport.instance.get.mockReset()
  transport.instance.post.mockReset()
})
afterEach(() => {
  clearExternalArchiveSession()
  vi.unstubAllEnvs()
  vi.restoreAllMocks()
})

describe('外部影像客户端请求契约', () => {
  it('使用独立凭据客户端，票据交换及上下文保留调用超时', () => {
    expect(clientConfiguration).toEqual(expect.objectContaining({ withCredentials: true, responseType: 'json' }))
    exchangeExternalArchiveTicket('synthetic-ticket', { timeout: 5000 })
    getExternalArchiveContext({ timeout: 3000 })
    expect(transport.instance.post).toHaveBeenCalledExactlyOnceWith('/api/v1/external/archive/session', { ticket: 'synthetic-ticket' }, { timeout: 5000 })
    expect(transport.instance.get).toHaveBeenCalledExactlyOnceWith('/api/v1/external/archive/context', { timeout: 3000 })
  })

  it('查询保留病案组合键和刷新语义，下载仅使用二进制响应', () => {
    vi.spyOn(Date, 'now').mockReturnValue(123456)
    vi.stubEnv('MODE', 'production')
    getExternalArchiveImages('000123', '', true)
    downloadExternalArchive('000123', '000456')
    expect(transport.instance.get.mock.calls).toEqual([
      ['/api/v1/external/archive/images', {
        params: { bah: '000123', sjh: undefined, _: 123456 },
        headers: { 'Cache-Control': 'no-cache', 'Pragma': 'no-cache' },
      }],
      ['/api/v1/external/archive/download', { params: { bah: '000123', sjh: '000456' }, responseType: 'blob' }],
    ])
  })

  it('静态演示不能发起真实档案下载', async () => {
    vi.stubEnv('MODE', 'pages')
    await expect(downloadExternalArchive('000123')).rejects.toThrow('静态演示')
    expect(transport.instance.get).not.toHaveBeenCalled()
  })

  it('成功 JSON 解包、二进制保留完整响应，明确的会话失效清除状态', async () => {
    setExternalArchiveSession(synthetic)
    expect(externalArchiveSession.value).toEqual(synthetic)
    const payload = { code: 200, data: synthetic }
    expect(onResponse({ config: {}, data: payload })).toBe(payload)
    const blob = { config: { responseType: 'blob' }, data: new Blob(['synthetic']) }
    expect(onResponse(blob)).toBe(blob)
    await expect(onResponse({ config: {}, data: { code: 403, message: 'synthetic denied' } })).rejects.toThrow('synthetic denied')
    expect(externalArchiveSession.value).toEqual(synthetic)
    await expect(onResponse({ config: {}, data: { code: 401, message: 'synthetic expired' } })).rejects.toThrow('synthetic expired')
    expect(externalArchiveSession.value).toBeNull()
    setExternalArchiveSession(synthetic)
    const error = Object.assign(new Error('synthetic expired'), { response: { status: 401 } })
    await expect(onFailure(error)).rejects.toBe(error)
    expect(externalArchiveSession.value).toBeNull()
  })
})
