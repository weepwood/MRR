import { describe, expect, it, vi } from 'vitest'
import { getRequest } from '../../index'
import {
  describeIpVersion,
  getPublicClientEnvironment,
  normalizeClientEnvironment,
} from '../client-environment'

vi.mock('../../index', () => ({
  getRequest: vi.fn(),
}))

describe('normalizeClientEnvironment', () => {
  it('保留服务端返回的地址与接入方式', () => {
    expect(normalizeClientEnvironment({
      clientIp: ' 192.168.10.24 ',
      ipVersion: 'ipv4',
      behindProxy: true,
      serverTime: '2026-10-16T17:15:20+08:00',
      serverZoneId: 'Asia/Shanghai',
    })).toEqual({
      clientIp: '192.168.10.24',
      ipVersion: 'IPV4',
      behindProxy: true,
      serverTime: '2026-10-16T17:15:20+08:00',
      serverZoneId: 'Asia/Shanghai',
    })
  })

  it('字段缺失或类型异常时回落到安全默认值', () => {
    expect(normalizeClientEnvironment(null)).toEqual({
      clientIp: '',
      ipVersion: 'UNKNOWN',
      behindProxy: false,
      serverTime: '',
      serverZoneId: '',
    })
    expect(normalizeClientEnvironment({ ipVersion: 'IPV9', behindProxy: 'no' }).ipVersion).toBe('UNKNOWN')
    expect(normalizeClientEnvironment({ behindProxy: 'no' }).behindProxy).toBe(false)
  })

  it('字符串形式的 behindProxy 也能正确解析', () => {
    expect(normalizeClientEnvironment({ behindProxy: 'true' }).behindProxy).toBe(true)
  })
})

describe('describeIpVersion', () => {
  it('把地址类型翻译成可读文本', () => {
    expect(describeIpVersion('IPV4')).toBe('IPv4')
    expect(describeIpVersion('IPV6')).toBe('IPv6')
    expect(describeIpVersion('UNKNOWN')).toBe('未识别')
    expect(describeIpVersion('')).toBe('未识别')
  })
})

describe('getPublicClientEnvironment', () => {
  it('后端可达时返回归一化结果与耗时', async () => {
    vi.mocked(getRequest).mockResolvedValue({
      data: { clientIp: '10.20.30.40', ipVersion: 'IPV4', behindProxy: true, serverTime: '2026-10-16T17:15:20+08:00', serverZoneId: 'Asia/Shanghai' },
    } as any)

    const probe = await getPublicClientEnvironment()

    expect(probe.reachable).toBe(true)
    expect(probe.payload?.clientIp).toBe('10.20.30.40')
    expect(probe.payload?.behindProxy).toBe(true)
    expect(probe.latencyMs).toBeGreaterThanOrEqual(0)
    expect(getRequest).toHaveBeenCalledWith(
      '/api/v1/public/config/client-environment',
      expect.objectContaining({ skipGlobalError: true }),
    )
  })

  it('请求失败时标记后端不可达而不抛出，保证登录页仍可用', async () => {
    vi.mocked(getRequest).mockRejectedValue(new Error('network down'))

    const probe = await getPublicClientEnvironment()

    expect(probe.reachable).toBe(false)
    expect(probe.payload).toBeUndefined()
    expect(probe.latencyMs).toBeGreaterThanOrEqual(0)
  })
})
