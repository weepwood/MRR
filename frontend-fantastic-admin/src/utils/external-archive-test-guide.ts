export interface ExternalArchiveClientStatus {
  clientId: string
  enabled: boolean
  secretConfigured: boolean
  allowedIps: string[]
  requestIpAllowed: boolean
}

export interface ExternalArchiveIntegrationStatus {
  enabled: boolean
  requestIp: string
  ticketTtlSeconds: number
  sessionTtlSeconds: number
  timestampToleranceSeconds: number
  maxArchivesPerTicket: number
  clients: ExternalArchiveClientStatus[]
}

export interface ExternalArchiveReadiness {
  level: 'success' | 'warning' | 'danger' | 'info'
  title: string
  description: string
  ready: boolean
}

export function createRandomHmacSecret(byteLength = 32): string {
  if (!Number.isInteger(byteLength) || byteLength < 32) {
    throw new Error('HMAC Secret 至少需要 32 个随机字节')
  }
  if (!globalThis.crypto?.getRandomValues) {
    throw new Error('当前浏览器不支持安全随机数生成')
  }
  const bytes = new Uint8Array(byteLength)
  globalThis.crypto.getRandomValues(bytes)
  return Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
}

export function findExternalArchiveClient(
  status: ExternalArchiveIntegrationStatus | null,
  clientId: string,
): ExternalArchiveClientStatus | null {
  const normalized = clientId.trim()
  if (!status || !normalized) {
    return null
  }
  return status.clients.find(client => client.clientId === normalized) ?? null
}

export function getExternalArchiveReadiness(
  status: ExternalArchiveIntegrationStatus | null,
  clientId: string,
  secret: string,
): ExternalArchiveReadiness {
  if (!status) {
    return {
      level: 'info',
      title: '尚未检查后端配置',
      description: '先点击“检查后端配置”，确认集成功能、Client ID、Secret 和来源 IP。',
      ready: false,
    }
  }
  if (!status.enabled) {
    return {
      level: 'danger',
      title: '外部系统集成未启用',
      description: '随机生成 Secret 只会填写当前页面。必须把同一个 Secret 写入 application-secrets.properties，将 mrr.integration.enabled 改为 true，并重启后端。',
      ready: false,
    }
  }

  const client = findExternalArchiveClient(status, clientId)
  if (!client) {
    return {
      level: 'danger',
      title: 'Client ID 未在后端配置',
      description: `后端没有找到 ${clientId.trim() || '当前 Client ID'}。复制页面生成的配置片段到 application-secrets.properties 后重启。`,
      ready: false,
    }
  }
  if (!client.enabled) {
    return {
      level: 'danger',
      title: '外部客户端已停用',
      description: '将对应 mrr.integration.clients[n].enabled 设置为 true 后重启后端。',
      ready: false,
    }
  }
  if (!client.secretConfigured) {
    return {
      level: 'danger',
      title: '后端没有配置 HMAC Secret',
      description: '把页面中的 HMAC Secret 写入对应客户端 secret 配置，并重启后端。',
      ready: false,
    }
  }
  if (!client.requestIpAllowed) {
    return {
      level: 'danger',
      title: '当前来源 IP 不在白名单',
      description: `MRR 识别到当前请求 IP 为 ${status.requestIp || '未知'}，请加入对应 allowed-ips 配置。`,
      ready: false,
    }
  }
  if (!secret.trim()) {
    return {
      level: 'warning',
      title: '后端已就绪，页面尚未填写 Secret',
      description: '请填写与后端对应客户端完全相同的 HMAC Secret。页面无法读取后端 Secret 明文。',
      ready: false,
    }
  }
  return {
    level: 'success',
    title: '后端配置检查通过',
    description: '可以发送票据请求。若返回“签名无效”，说明页面 Secret 与后端配置不一致。',
    ready: true,
  }
}

export function buildExternalArchiveIntegrationConfig(input: {
  clientId: string
  secret: string
  allowedIp?: string
  clientIndex?: number
  ticketTtlSeconds?: number
  sessionTtlSeconds?: number
  timestampToleranceSeconds?: number
  maxArchivesPerTicket?: number
}): string {
  const index = Number.isInteger(input.clientIndex) && (input.clientIndex ?? 0) >= 0
    ? input.clientIndex ?? 0
    : 0
  const clientId = input.clientId.trim() || 'his-system'
  const secret = input.secret.trim() || 'PASTE_THE_SAME_64_HEX_SECRET_HERE'
  const allowedIp = input.allowedIp?.trim() || '127.0.0.1'

  return [
    'mrr.integration.enabled=true',
    `mrr.integration.ticket-ttl-seconds=${input.ticketTtlSeconds ?? 90}`,
    `mrr.integration.session-ttl-seconds=${input.sessionTtlSeconds ?? 1800}`,
    `mrr.integration.timestamp-tolerance-seconds=${input.timestampToleranceSeconds ?? 300}`,
    `mrr.integration.max-archives-per-ticket=${input.maxArchivesPerTicket ?? 100}`,
    '',
    `mrr.integration.clients[${index}].client-id=${clientId}`,
    `mrr.integration.clients[${index}].secret=${secret}`,
    `mrr.integration.clients[${index}].enabled=true`,
    `mrr.integration.clients[${index}].allowed-ips[0]=${allowedIp}`,
  ].join('\n')
}

export function explainExternalArchiveTicketFailure(status: number, payload: unknown): string {
  const message = payload && typeof payload === 'object' && 'message' in payload
    ? String((payload as { message?: unknown }).message ?? '')
    : ''

  if (status === 503 && message.includes('外部系统集成未启用')) {
    return '后端配置仍为 mrr.integration.enabled=false。页面随机生成 Secret 不会自动修改后端配置；请复制配置片段、重启后端，再检查配置。'
  }
  if (status === 503 && message.includes('密钥未配置')) {
    return 'Client ID 已存在，但对应 secret 为空。请把页面中同一个 Secret 写入后端配置并重启。'
  }
  if (status === 401 && message.includes('客户端无效')) {
    return '当前 Client ID 与后端配置不一致，或该客户端未启用。'
  }
  if (status === 401 && message.includes('签名无效')) {
    return '页面中的 HMAC Secret 与后端配置不同，或者签名后原始 JSON、时间戳、nonce 被修改。'
  }
  if (status === 401 && message.includes('时间戳')) {
    return '请求时间戳超出后端允许范围，请点击“更新时间与 nonce”，并检查两台服务器时间是否同步。'
  }
  if (status === 403 && message.includes('IP')) {
    return '当前来源 IP 不在该 Client ID 的 allowed-ips 白名单中。'
  }
  if (status === 409 && message.includes('nonce')) {
    return '这个 nonce 已经使用。正常请求应生成新 nonce；保留它可以测试防重放。'
  }
  return message || '请检查后端配置、Client ID、Secret、来源 IP、时间戳和请求参数。'
}
