/**
 * 登录页「接入环境」展示模型。
 *
 * 把浏览器侧探测结果与服务端回显合并成可渲染的分组明细，并生成供支持人员
 * 使用的纯文本报告（便于用户一键复制发给管理员排查接入问题）。
 */

import type { ClientEnvironmentProbe } from '@/api/modules/client-environment'
import type { BrowserInfo, BrowserSupport, DeviceSnapshot, NetworkSnapshot } from '@/utils/client-environment'
import {
  describeConnectionType,
  describeEffectiveType,
  formatTimeZoneOffset,
} from '@/utils/client-environment'

export type EnvironmentTone = 'ok' | 'warn' | 'danger' | 'neutral'

export interface EnvironmentItem {
  label: string
  value: string
  tone?: EnvironmentTone
}

export interface EnvironmentGroup {
  key: string
  title: string
  items: EnvironmentItem[]
}

export interface LoginEnvironmentView {
  /** 徽标内常显的精简状态，例如「网络正常」或「后端不可达」 */
  statusLabel: string
  statusTone: EnvironmentTone
  /** 徽标内常显的客户端地址；不可用时为空串 */
  clientIpLabel: string
  /** 徽标内常显的浏览器短名；不可用时为空串 */
  browserLabel: string
  groups: EnvironmentGroup[]
  reportText: string
}

/** 时间偏差超过该秒数时提示，避免内网时钟漂移导致令牌校验困惑。 */
const CLOCK_SKEW_WARNING_SECONDS = 60

function formatServerTime(value: string): string {
  if (!value) { return '' }
  const matched = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}:\d{2})(?:\.\d+)?(Z|[+-]\d{2}:\d{2})?/.exec(value)
  if (!matched) { return value }
  const offset = matched[3] === 'Z' ? 'UTC' : (matched[3] || '')
  return `${matched[1]} ${matched[2]}${offset ? ` ${offset}` : ''}`
}

function parseServerEpochMillis(value: string): number | null {
  if (!value) { return null }
  const parsed = Date.parse(value)
  return Number.isFinite(parsed) ? parsed : null
}

/** 计算「服务器时间 − 本机时间」的秒数；无法解析时返回 null。 */
export function computeClockSkewSeconds(serverTime: string, clientEpochMillis: number): number | null {
  const serverEpoch = parseServerEpochMillis(serverTime)
  if (serverEpoch === null) { return null }
  return Math.round((serverEpoch - clientEpochMillis) / 1000)
}

function describeClockSkew(seconds: number | null): EnvironmentItem | null {
  if (seconds === null) { return null }
  if (Math.abs(seconds) <= CLOCK_SKEW_WARNING_SECONDS) {
    return { label: '时间偏差', value: '与本机一致', tone: 'ok' }
  }
  const direction = seconds > 0 ? '快' : '慢'
  return {
    label: '时间偏差',
    value: `服务器比本机${direction} ${Math.abs(seconds)} 秒`,
    tone: 'warn',
  }
}

function describeBrowser(info: BrowserInfo): string {
  const version = info.browserVersion ? ` ${info.browserVersion}` : ''
  return `${info.browserName}${version}`
}

function describeBrowserSummary(info: BrowserInfo): string {
  const majorVersion = /^(\d+)/.exec(info.browserVersion)?.[1]
  const version = majorVersion ? ` ${majorVersion}` : ''
  return `${info.browserName}${version}`
}

function describeEngine(info: BrowserInfo): string {
  if (!info.engineName) { return '' }
  return info.engineVersion ? `${info.engineName} ${info.engineVersion}` : info.engineName
}

function describeOs(info: BrowserInfo): string {
  const version = info.osVersion ? ` ${info.osVersion}` : ''
  return `${info.osName}${version}`
}

function describeNetworkQuality(network: NetworkSnapshot): string {
  const effective = describeEffectiveType(network.effectiveType)
  const physical = describeConnectionType(network.connectionType)
  if (physical && effective) { return `${physical} · ${effective}` }
  return physical || effective
}

export function buildLoginEnvironmentView(
  browser: BrowserInfo,
  network: NetworkSnapshot,
  device: DeviceSnapshot,
  support: BrowserSupport,
  probe: ClientEnvironmentProbe | null,
  userAgent: string,
  clientEpochMillis: number,
): LoginEnvironmentView {
  const reachable = probe ? probe.reachable : false
  const server = probe && probe.payload ? probe.payload : null
  const offline = !network.online

  let statusLabel = '网络正常'
  let statusTone: EnvironmentTone = 'ok'
  if (offline) {
    statusLabel = '网络已断开'
    statusTone = 'danger'
  }
  else if (probe && !reachable) {
    statusLabel = '后端不可达'
    statusTone = 'danger'
  }
  else if (!probe) {
    statusLabel = '正在检测'
    statusTone = 'neutral'
  }

  const reachabilityValue = probe
    ? (reachable ? `可达 · ${probe.latencyMs} 毫秒` : '不可达')
    : '检测中'

  const networkItems: EnvironmentItem[] = [
    { label: '连接状态', value: offline ? '已断开' : '已连接', tone: offline ? 'danger' : 'ok' },
    { label: '后端可达性', value: reachabilityValue, tone: probe ? (reachable ? 'ok' : 'danger') : 'neutral' },
  ]
  const quality = describeNetworkQuality(network)
  if (quality) {
    networkItems.push({ label: '网络类型', value: quality })
  }
  else if (!network.supported) {
    networkItems.push({ label: '网络类型', value: '浏览器未提供', tone: 'neutral' })
  }
  if (network.downlinkMbps !== null) {
    networkItems.push({ label: '预估下行带宽', value: `${network.downlinkMbps} Mbps` })
  }
  if (network.rttMs !== null) {
    networkItems.push({ label: '预估往返时延', value: `${network.rttMs} 毫秒` })
  }
  if (network.supported) {
    networkItems.push({ label: '省流量模式', value: network.saveData ? '已开启' : '未开启' })
  }

  const addressItems: EnvironmentItem[] = [
    { label: '客户端 IP', value: server && server.clientIp ? server.clientIp : '未能获取' },
  ]
  if (server) {
    const versionLabel = server.ipVersion === 'IPV4'
      ? 'IPv4'
      : (server.ipVersion === 'IPV6' ? 'IPv6' : '未识别')
    addressItems.push({ label: '地址类型', value: versionLabel })
    addressItems.push({
      label: '接入方式',
      value: server.behindProxy ? '经反向代理转发' : '直连后端',
    })
    const serverTime = formatServerTime(server.serverTime)
    if (serverTime) {
      addressItems.push({ label: '服务器时间', value: serverTime })
    }
    const skew = describeClockSkew(computeClockSkewSeconds(server.serverTime, clientEpochMillis))
    if (skew) {
      addressItems.push(skew)
    }
    if (server.serverZoneId) {
      addressItems.push({ label: '服务器时区', value: server.serverZoneId })
    }
  }

  const clientItems: EnvironmentItem[] = [
    { label: '浏览器', value: describeBrowser(browser) },
  ]
  const engine = describeEngine(browser)
  if (engine) {
    clientItems.push({ label: '渲染引擎', value: engine })
  }
  clientItems.push({ label: '操作系统', value: describeOs(browser) })
  clientItems.push({ label: '设备类型', value: browser.deviceType })
  if (device.screenWidth && device.screenHeight) {
    const viewport = device.viewportWidth && device.viewportHeight
      ? `（窗口 ${device.viewportWidth}×${device.viewportHeight}）`
      : ''
    clientItems.push({
      label: '屏幕分辨率',
      value: `${device.screenWidth}×${device.screenHeight}${viewport}`,
    })
  }
  clientItems.push({ label: '像素比', value: String(device.pixelRatio) })
  const offset = formatTimeZoneOffset(device.timeZoneOffsetMinutes)
  const zone = device.timeZone || '未知'
  clientItems.push({ label: '本机时区', value: offset ? `${zone}（${offset}）` : zone })
  clientItems.push({ label: '界面语言', value: device.language || '未知' })
  if (device.hardwareConcurrency !== null) {
    clientItems.push({ label: '处理器核心', value: `${device.hardwareConcurrency} 核` })
  }
  if (device.deviceMemoryGb !== null) {
    clientItems.push({ label: '设备内存', value: `约 ${device.deviceMemoryGb} GB` })
  }
  clientItems.push({
    label: '浏览器兼容性',
    value: support.supported ? `满足基线（${support.baselineLabel}）` : `低于基线（${support.baselineLabel}）`,
    tone: support.supported ? 'ok' : 'warn',
  })

  const groups: EnvironmentGroup[] = [
    { key: 'network', title: '网络', items: networkItems },
    { key: 'address', title: '接入地址', items: addressItems },
    { key: 'client', title: '客户端', items: clientItems },
  ]

  const reportLines: string[] = []
  reportLines.push('MRR 登录环境自检')
  reportLines.push(`采集时间：${new Date(clientEpochMillis).toLocaleString()}`)
  groups.forEach((group) => {
    reportLines.push('')
    reportLines.push(`【${group.title}】`)
    group.items.forEach(item => reportLines.push(`${item.label}：${item.value}`))
  })
  if (userAgent) {
    reportLines.push('')
    reportLines.push(`User-Agent：${userAgent}`)
  }
  if (!support.supported && support.reason) {
    reportLines.push('')
    reportLines.push(`提示：${support.reason}`)
  }

  return {
    statusLabel,
    statusTone,
    clientIpLabel: server && server.clientIp ? server.clientIp : '',
    browserLabel: browser.browserName === '未知浏览器' ? '' : describeBrowserSummary(browser),
    groups,
    reportText: reportLines.join('\n'),
  }
}
