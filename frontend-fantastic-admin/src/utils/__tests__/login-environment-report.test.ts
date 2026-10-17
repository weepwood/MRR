import type { ClientEnvironmentProbe } from '@/api/modules/client-environment'
import type { BrowserInfo, DeviceSnapshot, NetworkSnapshot } from '@/utils/client-environment'
import { describe, expect, it } from 'vitest'
import {

  collectDeviceSnapshot,

  evaluateBrowserSupport,

  parseUserAgent,
} from '@/utils/client-environment'
import { buildLoginEnvironmentView, computeClockSkewSeconds } from '@/utils/login-environment-report'

const CHROME_WINDOWS = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36'

const BROWSER: BrowserInfo = parseUserAgent(CHROME_WINDOWS)

const ONLINE_NETWORK: NetworkSnapshot = {
  online: true,
  effectiveType: '4g',
  connectionType: 'wifi',
  downlinkMbps: 10,
  rttMs: 50,
  saveData: false,
  supported: true,
}

const DEVICE: DeviceSnapshot = {
  language: 'zh-CN',
  languages: ['zh-CN', 'en'],
  timeZone: 'Asia/Shanghai',
  timeZoneOffsetMinutes: 480,
  viewportWidth: 1440,
  viewportHeight: 900,
  screenWidth: 2560,
  screenHeight: 1440,
  pixelRatio: 1.25,
  colorDepth: 24,
  hardwareConcurrency: 16,
  deviceMemoryGb: 8,
  maxTouchPoints: 0,
  cookieEnabled: true,
  isSecureContext: true,
}

const REACHABLE_PROBE: ClientEnvironmentProbe = {
  reachable: true,
  latencyMs: 18,
  payload: {
    clientIp: '192.168.10.24',
    ipVersion: 'IPV4',
    behindProxy: true,
    serverTime: '2026-10-16T17:15:20+08:00',
    serverZoneId: 'Asia/Shanghai',
  },
}

function build(overrides: {
  network?: NetworkSnapshot
  probe?: ClientEnvironmentProbe | null
  clientEpochMillis?: number
} = {}) {
  return buildLoginEnvironmentView(
    BROWSER,
    overrides.network || ONLINE_NETWORK,
    DEVICE,
    evaluateBrowserSupport(BROWSER),
    overrides.probe === undefined ? REACHABLE_PROBE : overrides.probe,
    CHROME_WINDOWS,
    overrides.clientEpochMillis === undefined
      ? Date.parse('2026-10-16T09:15:20Z')
      : overrides.clientEpochMillis,
  )
}

function findItem(view: ReturnType<typeof build>, groupKey: string, label: string) {
  const group = view.groups.find(candidate => candidate.key === groupKey)
  return group ? group.items.find(item => item.label === label) : undefined
}

describe('buildLoginEnvironmentView', () => {
  it('后端可达时徽标显示网络正常并带出客户端地址', () => {
    const view = build()

    expect(view.statusLabel).toBe('网络正常')
    expect(view.statusTone).toBe('ok')
    expect(view.clientIpLabel).toBe('192.168.10.24')
    expect(view.browserLabel).toBe('Chrome 141.0.0.0')
  })

  it('按网络、接入地址、客户端三组输出明细', () => {
    const view = build()

    expect(view.groups.map(group => group.key)).toEqual(['network', 'address', 'client'])
    expect(findItem(view, 'network', '连接状态')?.value).toBe('已连接')
    expect(findItem(view, 'network', '后端可达性')?.value).toBe('可达 · 18 毫秒')
    expect(findItem(view, 'network', '网络类型')?.value).toBe('Wi-Fi · 4G')
    expect(findItem(view, 'network', '预估下行带宽')?.value).toBe('10 Mbps')
    expect(findItem(view, 'address', '客户端地址')?.value).toBe('192.168.10.24')
    expect(findItem(view, 'address', '地址类型')?.value).toBe('IPv4')
    expect(findItem(view, 'address', '接入方式')?.value).toBe('经反向代理转发')
    expect(findItem(view, 'address', '服务器时间')?.value).toContain('2026-10-16 17:15:20')
    expect(findItem(view, 'client', '浏览器')?.value).toBe('Chrome 141.0.0.0')
    expect(findItem(view, 'client', '渲染引擎')?.value).toBe('Blink 537.36')
    expect(findItem(view, 'client', '操作系统')?.value).toBe('Windows 10/11')
    expect(findItem(view, 'client', '屏幕分辨率')?.value).toBe('2560×1440（窗口 1440×900）')
    expect(findItem(view, 'client', '本机时区')?.value).toBe('Asia/Shanghai（UTC+08:00）')
    expect(findItem(view, 'client', '浏览器兼容性')?.tone).toBe('ok')
  })

  it('后端不可达时把状态降级为后端不可达而不抛错', () => {
    const view = build({ probe: { reachable: false, latencyMs: 3000 } })

    expect(view.statusLabel).toBe('后端不可达')
    expect(view.statusTone).toBe('danger')
    expect(view.clientIpLabel).toBe('')
    expect(findItem(view, 'network', '后端可达性')?.value).toBe('不可达')
    expect(findItem(view, 'address', '客户端地址')?.value).toBe('未能获取')
  })

  it('网络断开时优先报告连接状态', () => {
    const view = build({ network: { ...ONLINE_NETWORK, online: false } })

    expect(view.statusLabel).toBe('网络已断开')
    expect(view.statusTone).toBe('danger')
    expect(findItem(view, 'network', '连接状态')?.value).toBe('已断开')
  })

  it('尚未探测完成时显示检测中', () => {
    const view = build({ probe: null })

    expect(view.statusLabel).toBe('正在检测')
    expect(view.statusTone).toBe('neutral')
    expect(findItem(view, 'network', '后端可达性')?.value).toBe('检测中')
  })

  it('服务器时间对照本机偏差超过阈值时给出告警', () => {
    const clientEpoch = Date.parse('2026-10-16T09:15:20Z')
    const view = build({
      clientEpochMillis: clientEpoch,
      probe: {
        reachable: true,
        latencyMs: 12,
        payload: {
          clientIp: '10.0.0.8',
          ipVersion: 'IPV4',
          behindProxy: false,
          serverTime: new Date(clientEpoch + 120_000).toISOString(),
          serverZoneId: 'Asia/Shanghai',
        },
      },
    })

    const skew = findItem(view, 'address', '时间偏差')
    expect(skew?.tone).toBe('warn')
    expect(skew?.value).toContain('快')
  })

  it('时间一致时不产生偏差告警行', () => {
    const view = build()

    const skew = findItem(view, 'address', '时间偏差')
    expect(skew?.tone).toBe('ok')
    expect(skew?.value).toBe('与本机一致')
  })

  it('不支持网络信息 API 时明确标注而不是留空', () => {
    const view = build({
      network: {
        online: true,
        effectiveType: '',
        connectionType: '',
        downlinkMbps: null,
        rttMs: null,
        saveData: false,
        supported: false,
      },
    })

    expect(findItem(view, 'network', '网络类型')?.value).toBe('浏览器未提供')
    expect(findItem(view, 'network', '预估下行带宽')).toBeUndefined()
    expect(findItem(view, 'network', '省流量模式')).toBeUndefined()
  })

  it('生成的报告文本包含分组、关键字段与 User-Agent，便于复制给管理员', () => {
    const view = build()

    expect(view.reportText).toContain('MRR 登录环境自检')
    expect(view.reportText).toContain('【网络】')
    expect(view.reportText).toContain('【接入地址】')
    expect(view.reportText).toContain('【客户端】')
    expect(view.reportText).toContain('客户端地址：192.168.10.24')
    expect(view.reportText).toContain(`User-Agent：${CHROME_WINDOWS}`)
  })

  it('浏览器低于基线时在报告与分组中给出提示', () => {
    const legacy = parseUserAgent(CHROME_WINDOWS.replace('Chrome/141', 'Chrome/70'))
    const view = buildLoginEnvironmentView(
      legacy,
      ONLINE_NETWORK,
      DEVICE,
      evaluateBrowserSupport(legacy),
      REACHABLE_PROBE,
      CHROME_WINDOWS,
      Date.now(),
    )

    expect(findItem(view, 'client', '浏览器兼容性')?.tone).toBe('warn')
    expect(view.reportText).toContain('低于')
  })
})

describe('computeClockSkewSeconds', () => {
  it('计算服务器与本机的秒级偏差', () => {
    const base = Date.parse('2026-10-16T09:15:20Z')
    expect(computeClockSkewSeconds(new Date(base + 90_000).toISOString(), base)).toBe(90)
    expect(computeClockSkewSeconds(new Date(base - 30_000).toISOString(), base)).toBe(-30)
  })

  it('无法解析的时间返回 null 而不是 NaN', () => {
    expect(computeClockSkewSeconds('', Date.now())).toBeNull()
    expect(computeClockSkewSeconds('not-a-time', Date.now())).toBeNull()
  })
})

describe('collectDeviceSnapshot 与报告的集成', () => {
  it('使用真实设备快照也能构建出完整视图', () => {
    const view = buildLoginEnvironmentView(
      parseUserAgent(CHROME_WINDOWS),
      ONLINE_NETWORK,
      collectDeviceSnapshot(),
      evaluateBrowserSupport(parseUserAgent(CHROME_WINDOWS)),
      REACHABLE_PROBE,
      CHROME_WINDOWS,
      Date.now(),
    )

    expect(view.groups).toHaveLength(3)
    expect(view.reportText.length).toBeGreaterThan(0)
  })
})
