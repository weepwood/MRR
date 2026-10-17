import { describe, expect, it } from 'vitest'
import {
  collectDeviceSnapshot,
  collectNetworkSnapshot,
  describeConnectionType,
  describeEffectiveType,
  evaluateBrowserSupport,
  formatTimeZoneOffset,
  parseUserAgent,
  parseVersionParts,
} from '../client-environment'

const CHROME_WINDOWS = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36'
const EDGE_WINDOWS = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0'
const SAFARI_MAC = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15'
const FIREFOX_WINDOWS = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0'
const SAFARI_IPHONE = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1'
const CHROME_ANDROID = 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'
const IE_ELEVEN = 'Mozilla/5.0 (Windows NT 6.1; Trident/7.0; rv:11.0) like Gecko'

describe('parseUserAgent', () => {
  it('识别 Windows 上的 Chrome 并得出 Blink 内核版本', () => {
    const info = parseUserAgent(CHROME_WINDOWS)

    expect(info.browserName).toBe('Chrome')
    expect(info.browserVersion).toBe('141.0.0.0')
    expect(info.engineName).toBe('Blink')
    expect(info.chromiumVersion).toBe('141.0.0.0')
    expect(info.osName).toBe('Windows')
    expect(info.osVersion).toBe('10/11')
    expect(info.deviceType).toBe('桌面设备')
  })

  it('把 Chromium 版 Edge 识别为 Edge 而不是 Chrome', () => {
    const info = parseUserAgent(EDGE_WINDOWS)

    expect(info.browserName).toBe('Edge')
    expect(info.browserVersion).toBe('141.0.0.0')
    expect(info.engineName).toBe('Blink')
  })

  it('识别 macOS 上的 Safari 为 WebKit 内核', () => {
    const info = parseUserAgent(SAFARI_MAC)

    expect(info.browserName).toBe('Safari')
    expect(info.browserVersion).toBe('17.2')
    expect(info.engineName).toBe('WebKit')
    expect(info.chromiumVersion).toBe('')
    expect(info.osName).toBe('macOS')
    expect(info.osVersion).toBe('10.15.7')
  })

  it('识别 Firefox 为 Gecko 内核且不误判为 Blink', () => {
    const info = parseUserAgent(FIREFOX_WINDOWS)

    expect(info.browserName).toBe('Firefox')
    expect(info.browserVersion).toBe('121.0')
    expect(info.engineName).toBe('Gecko')
    expect(info.chromiumVersion).toBe('')
  })

  it('识别国产壳浏览器并保留 Chromium 版本用于基线判定', () => {
    const qihu = parseUserAgent(`${CHROME_WINDOWS} QIHU 360SE`)
    const qq = parseUserAgent(`${CHROME_WINDOWS} QQBrowser/11.9.5294.400`)

    expect(qihu.browserName).toContain('360')
    expect(qihu.chromiumVersion).toBe('141.0.0.0')
    expect(qq.browserName).toBe('QQ 浏览器')
    expect(qq.browserVersion).toBe('11.9.5294.400')
    expect(qq.chromiumVersion).toBe('141.0.0.0')
  })

  it('识别微信内置浏览器，便于区分扫码访问', () => {
    const info = parseUserAgent(`${SAFARI_IPHONE} MicroMessenger/8.0.44`)

    expect(info.browserName).toBe('微信内置浏览器')
    expect(info.osName).toBe('iOS')
    expect(info.deviceType).toBe('移动设备')
  })

  it('识别手机与平板设备的设备类型', () => {
    expect(parseUserAgent(CHROME_ANDROID).deviceType).toBe('移动设备')
    expect(parseUserAgent(CHROME_ANDROID).osName).toBe('Android')
    const ipad = 'Mozilla/5.0 (iPad; CPU OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/604.1'
    expect(parseUserAgent(ipad).deviceType).toBe('平板设备')
    expect(parseUserAgent(ipad).osName).toBe('iPadOS')
  })

  it('识别 IE11 以便提示升级浏览器', () => {
    const info = parseUserAgent(IE_ELEVEN)

    expect(info.browserName).toBe('Internet Explorer')
    expect(info.engineName).toBe('Trident')
    expect(info.osName).toBe('Windows')
    expect(info.osVersion).toBe('7')
  })

  it('无法识别时回退为未知浏览器而不是抛错', () => {
    const info = parseUserAgent('')

    expect(info.browserName).toBe('未知浏览器')
    expect(info.browserVersion).toBe('')
    expect(info.engineName).toBe('')
  })
})

describe('evaluateBrowserSupport', () => {
  it('chrome 以 86 为基线', () => {
    expect(evaluateBrowserSupport(parseUserAgent(CHROME_WINDOWS)).supported).toBe(true)
    const old = parseUserAgent(CHROME_WINDOWS.replace('Chrome/141', 'Chrome/85'))
    expect(evaluateBrowserSupport(old).supported).toBe(false)
    expect(evaluateBrowserSupport(old).baselineLabel).toBe('Chrome 86')
    const edgeOfBaseline = parseUserAgent(CHROME_WINDOWS.replace('Chrome/141', 'Chrome/86'))
    expect(evaluateBrowserSupport(edgeOfBaseline).supported).toBe(true)
  })

  it('edge 以 111 为基线', () => {
    expect(evaluateBrowserSupport(parseUserAgent(EDGE_WINDOWS)).supported).toBe(true)
    const old = parseUserAgent(EDGE_WINDOWS.replace('Edg/141', 'Edg/110'))
    expect(evaluateBrowserSupport(old).supported).toBe(false)
    expect(evaluateBrowserSupport(old).baselineLabel).toBe('Edge 111')
  })

  it('firefox 以 114 为基线', () => {
    expect(evaluateBrowserSupport(parseUserAgent(FIREFOX_WINDOWS)).supported).toBe(true)
    const old = parseUserAgent(FIREFOX_WINDOWS.replace('Firefox/121', 'Firefox/113'))
    expect(evaluateBrowserSupport(old).supported).toBe(false)
  })

  it('safari 以 16.4 为基线且比较次版本号', () => {
    expect(evaluateBrowserSupport(parseUserAgent(SAFARI_MAC)).supported).toBe(true)
    const old = parseUserAgent(SAFARI_MAC.replace('Version/17.2', 'Version/16.3'))
    expect(evaluateBrowserSupport(old).supported).toBe(false)
    const edgeOfBaseline = parseUserAgent(SAFARI_MAC.replace('Version/17.2', 'Version/16.4'))
    expect(evaluateBrowserSupport(edgeOfBaseline).supported).toBe(true)
  })

  it('国产壳浏览器按 Chromium 版本对 Chrome 基线判定', () => {
    const modern = parseUserAgent(`${CHROME_WINDOWS} QIHU 360SE`)
    const legacy = parseUserAgent(`${CHROME_WINDOWS.replace('Chrome/141', 'Chrome/78')} QIHU 360SE`)

    expect(evaluateBrowserSupport(modern).supported).toBe(true)
    expect(evaluateBrowserSupport(legacy).supported).toBe(false)
  })

  it('版本无法识别时按通过处理并给出提示而不是误报不支持', () => {
    const support = evaluateBrowserSupport(parseUserAgent(''))

    expect(support.supported).toBe(true)
    expect(support.reason).toContain('无法识别')
  })
})

describe('浏览器侧快照采集', () => {
  it('网络信息 API 缺失时返回不支持标记且不抛错', () => {
    const snapshot = collectNetworkSnapshot()

    expect(snapshot.supported).toBe(false)
    expect(snapshot.online).toBe(true)
    expect(snapshot.effectiveType).toBe('')
    expect(snapshot.downlinkMbps).toBeNull()
    expect(snapshot.rttMs).toBeNull()
  })

  it('设备快照给出可渲染的数值字段', () => {
    const snapshot = collectDeviceSnapshot()

    expect(typeof snapshot.pixelRatio).toBe('number')
    expect(snapshot.pixelRatio).toBeGreaterThan(0)
    expect(typeof snapshot.language).toBe('string')
    expect(Array.isArray(snapshot.languages)).toBe(true)
    expect(snapshot.timeZoneOffsetMinutes === null || typeof snapshot.timeZoneOffsetMinutes === 'number').toBe(true)
  })
})

describe('格式化辅助函数', () => {
  it('把时区偏移分钟数格式化为 UTC±HH:MM', () => {
    expect(formatTimeZoneOffset(480)).toBe('UTC+08:00')
    expect(formatTimeZoneOffset(-300)).toBe('UTC-05:00')
    expect(formatTimeZoneOffset(0)).toBe('UTC+00:00')
    expect(formatTimeZoneOffset(-330)).toBe('UTC-05:30')
    expect(formatTimeZoneOffset(null)).toBe('')
  })

  it('把网络类型翻译成中文，未知值原样返回', () => {
    expect(describeEffectiveType('4g')).toBe('4G')
    expect(describeEffectiveType('slow-2g')).toContain('极慢')
    expect(describeEffectiveType('')).toBe('')
    expect(describeConnectionType('wifi')).toBe('Wi-Fi')
    expect(describeConnectionType('ethernet')).toBe('有线网络')
    expect(describeConnectionType('5g')).toBe('5g')
  })

  it('解析版本号时丢弃非数字片段', () => {
    expect(parseVersionParts('141.0.0.0')).toEqual([141, 0, 0, 0])
    expect(parseVersionParts('17.2')).toEqual([17, 2])
    expect(parseVersionParts('')).toEqual([])
  })
})
