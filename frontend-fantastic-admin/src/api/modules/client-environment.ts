/**
 * 登录页客户端接入环境接口。
 *
 * 客户端地址只能由服务端回显（浏览器无法得知服务端看到的是哪个地址），
 * 因此这里同时承担「探测后端可达性」的职责：请求失败即视为后端不可达，
 * 登录页据此展示离线提示，不影响其他登录流程。
 */

export interface ClientEnvironmentPayload {
  clientIp: string
  ipVersion: string
  behindProxy: boolean
  serverTime: string
  serverZoneId: string
}

export interface ClientEnvironmentProbe {
  /** 后端是否可达 */
  reachable: boolean
  /** 本次探测耗时（毫秒），四舍五入到整数 */
  latencyMs: number
  payload?: ClientEnvironmentPayload
}

export const EMPTY_CLIENT_ENVIRONMENT: ClientEnvironmentPayload = {
  clientIp: '',
  ipVersion: 'UNKNOWN',
  behindProxy: false,
  serverTime: '',
  serverZoneId: '',
}

function asText(value: unknown): string {
  return String(value ?? '').trim()
}

/** 后端返回内容做防御性归一，字段缺失或类型异常时回落到安全默认值。 */
export function normalizeClientEnvironment(values?: Record<string, unknown> | null): ClientEnvironmentPayload {
  const source = values || {}
  const ipVersion = asText(source.ipVersion).toUpperCase()
  return {
    clientIp: asText(source.clientIp),
    ipVersion: ipVersion === 'IPV4' || ipVersion === 'IPV6' ? ipVersion : 'UNKNOWN',
    behindProxy: source.behindProxy === true || String(source.behindProxy).toLowerCase() === 'true',
    serverTime: asText(source.serverTime),
    serverZoneId: asText(source.serverZoneId),
  }
}

function nowMs(): number {
  if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
    return performance.now()
  }
  return Date.now()
}

export function describeIpVersion(ipVersion: string): string {
  if (ipVersion === 'IPV4') { return 'IPv4' }
  if (ipVersion === 'IPV6') { return 'IPv6' }
  return '未识别'
}

/** 探测后端可达性并获取服务端识别到的客户端地址。失败不抛错。 */
export async function getPublicClientEnvironment(): Promise<ClientEnvironmentProbe> {
  const startedAt = nowMs()
  try {
    const { getRequest } = await import('../index')
    const response = await getRequest<Record<string, unknown>>('/api/v1/public/config/client-environment', {
      skipGlobalError: true,
    } as any)
    return {
      reachable: true,
      latencyMs: Math.round(nowMs() - startedAt),
      payload: normalizeClientEnvironment(response.data),
    }
  }
  catch {
    return {
      reachable: false,
      latencyMs: Math.round(nowMs() - startedAt),
    }
  }
}
