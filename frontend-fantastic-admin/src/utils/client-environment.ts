/**
 * 登录页客户端环境探测。
 *
 * 全部信息都在浏览器侧采集，零外部依赖，并保持与构建基线（Chrome 86 / Edge 109 /
 * Firefox 114 / Safari 16.4）兼容：不使用 `Array.prototype.at`、`Object.hasOwn`、
 * `structuredClone` 等更新语法，所有宿主 API 均带存在性判断，在 jsdom 等
 * 精简环境中也不会抛错。
 *
 * 唯一无法在浏览器侧获得的是「服务端看到的客户端地址」，由
 * `getPublicClientEnvironment()` 请求后端回显。
 */

import { browserMinimumVersions } from './browser-baseline'

export interface BrowserInfo {
  /** 展示用浏览器名称，例如 Chrome、Edge、360 安全浏览器 */
  browserName: string
  /** 展示用版本号，例如 141.0.0.0 */
  browserVersion: string
  /** 渲染引擎名称，例如 Blink、Gecko、WebKit、Trident */
  engineName: string
  /** 引擎版本；无法确定时为空串 */
  engineVersion: string
  /** 操作系统名称，例如 Windows 10/11 */
  osName: string
  /** 操作系统版本；无法确定时为空串 */
  osVersion: string
  /** 设备类型：桌面设备 / 移动设备 / 平板设备 */
  deviceType: string
  /** 基于 Chromium 的浏览器中解析出的 Chromium 版本，用于基线判定；非 Blink 内核为空串 */
  chromiumVersion: string
}

export interface NetworkSnapshot {
  /** navigator.onLine 报告的连接状态 */
  online: boolean
  /** 有效连接类型，例如 4g、wifi；浏览器不支持时为空串 */
  effectiveType: string
  /** 连接物理类型，例如 wifi、cellular；浏览器不支持时为空串 */
  connectionType: string
  /** 预估下行带宽（Mbps）；不可用时为 null */
  downlinkMbps: number | null
  /** 预估往返时延（毫秒）；不可用时为 null */
  rttMs: number | null
  /** 是否处于省流量模式 */
  saveData: boolean
  /** 浏览器是否提供网络信息 API */
  supported: boolean
}

export interface DeviceSnapshot {
  language: string
  languages: string[]
  timeZone: string
  timeZoneOffsetMinutes: number | null
  viewportWidth: number
  viewportHeight: number
  screenWidth: number
  screenHeight: number
  pixelRatio: number
  colorDepth: number
  hardwareConcurrency: number | null
  deviceMemoryGb: number | null
  maxTouchPoints: number
  cookieEnabled: boolean
  isSecureContext: boolean
}

export interface BrowserSupport {
  supported: boolean
  /** 判定所用基线的可读名称，例如 Chrome 86 */
  baselineLabel: string
  /** 未达标时的说明；达标时为空串 */
  reason: string
}

interface NetworkInformationLike {
  effectiveType?: string
  type?: string
  downlink?: number
  rtt?: number
  saveData?: boolean
  addEventListener?: (type: string, listener: () => void) => void
  removeEventListener?: (type: string, listener: () => void) => void
}

const UNKNOWN_BROWSER = '未知浏览器'

function matchVersion(source: string, pattern: RegExp): string {
  const matched = pattern.exec(source)
  return matched && matched[1] ? matched[1] : ''
}

function normalizeVersion(raw: string): string {
  return raw ? raw.replace(/_/g, '.') : ''
}

function detectEngine(source: string): { engineName: string, engineVersion: string } {
  if (/Trident\//.test(source)) {
    return { engineName: 'Trident', engineVersion: matchVersion(source, /Trident\/([\d.]+)/) }
  }
  const webkitVersion = matchVersion(source, /AppleWebKit\/([\d.]+)/)
  if (webkitVersion) {
    const isBlink = /Chrome\/|Chromium\/|CriOS\/|Edg\/|EdgA\/|EdgiOS\//.test(source)
    return { engineName: isBlink ? 'Blink' : 'WebKit', engineVersion: webkitVersion }
  }
  if (/Gecko\//.test(source)) {
    return { engineName: 'Gecko', engineVersion: '' }
  }
  return { engineName: '', engineVersion: '' }
}

function detectBrowser(source: string): { browserName: string, browserVersion: string } {
  // 顺序敏感：先匹配最具体的壳浏览器，最后才是通用内核标识。
  // versionPattern 用于「识别标记」与「版本号」不在同一处的浏览器（Safari 的
  // 识别标记是 Safari/，而可比较的版本号在 Version/ 里），拆开写也避免使用
  // 会引发回溯的重叠量词。
  const rules: Array<{ name: string, pattern: RegExp, versionPattern?: RegExp }> = [
    { name: '企业微信', pattern: /wxwork\/([\d.]+)/ },
    { name: '微信内置浏览器', pattern: /MicroMessenger\/([\d.]+)/ },
    { name: '360 安全浏览器', pattern: /360SE[\s/]?([\d.]*)/ },
    { name: '360 极速浏览器', pattern: /360EE[\s/]?([\d.]*)/ },
    { name: '360 浏览器', pattern: /QIHU\s*360SE[\s/]?([\d.]*)/ },
    { name: 'QQ 浏览器', pattern: /QQBrowser\/([\d.]+)/ },
    { name: 'UC 浏览器', pattern: /UBrowser\/([\d.]+)/ },
    { name: 'UC 浏览器', pattern: /UCBrowser\/([\d.]+)/ },
    { name: '搜狗浏览器', pattern: /MetaSr\s*([\d.]*)/ },
    { name: '搜狗浏览器', pattern: /SogouMobileBrowser\/([\d.]+)/ },
    { name: '猎豹浏览器', pattern: /LBBROWSER/ },
    { name: '傲游浏览器', pattern: /Maxthon\/([\d.]+)/ },
    { name: 'Vivaldi', pattern: /Vivaldi\/([\d.]+)/ },
    { name: 'Edge', pattern: /EdgA?\/([\d.]+)/ },
    { name: 'Edge', pattern: /EdgiOS\/([\d.]+)/ },
    { name: 'Opera', pattern: /OPR\/([\d.]+)/ },
    { name: 'Opera', pattern: /Opera[ /]([\d.]+)/ },
    { name: 'Chromium', pattern: /Chromium\/([\d.]+)/ },
    { name: 'Chrome', pattern: /CriOS\/([\d.]+)/ },
    { name: 'Chrome', pattern: /Chrome\/([\d.]+)/ },
    { name: 'Firefox', pattern: /FxiOS\/([\d.]+)/ },
    { name: 'Firefox', pattern: /Firefox\/([\d.]+)/ },
    { name: 'Internet Explorer', pattern: /MSIE\s([\d.]+)/ },
    { name: 'Internet Explorer', pattern: /rv:([\d.]+)\) like Gecko/ },
    { name: 'Safari', pattern: /Safari\//, versionPattern: /Version\/(\d+(?:\.\d+)*)/ },
  ]

  for (const rule of rules) {
    const matched = rule.pattern.exec(source)
    if (matched) {
      const rawVersion = rule.versionPattern
        ? matchVersion(source, rule.versionPattern)
        : (matched[1] || '')
      return { browserName: rule.name, browserVersion: normalizeVersion(rawVersion) }
    }
  }
  return { browserName: UNKNOWN_BROWSER, browserVersion: '' }
}

function detectOs(source: string): { osName: string, osVersion: string } {
  const windowsVersions: Array<{ pattern: RegExp, name: string, version: string }> = [
    { pattern: /Windows NT 10\.0/, name: 'Windows', version: '10/11' },
    { pattern: /Windows NT 6\.3/, name: 'Windows', version: '8.1' },
    { pattern: /Windows NT 6\.2/, name: 'Windows', version: '8' },
    { pattern: /Windows NT 6\.1/, name: 'Windows', version: '7' },
    { pattern: /Windows NT 6\.0/, name: 'Windows', version: 'Vista' },
    { pattern: /Windows NT 5\.1/, name: 'Windows', version: 'XP' },
  ]
  if (/Windows Phone/.test(source)) {
    return { osName: 'Windows Phone', osVersion: matchVersion(source, /Windows Phone ([\d.]+)/) }
  }
  if (/iPad/.test(source)) {
    return { osName: 'iPadOS', osVersion: normalizeVersion(matchVersion(source, /OS ([\d_]+)/)) }
  }
  if (/iPhone|iPod/.test(source)) {
    return { osName: 'iOS', osVersion: normalizeVersion(matchVersion(source, /OS ([\d_]+)/)) }
  }
  for (const item of windowsVersions) {
    if (item.pattern.test(source)) {
      return { osName: item.name, osVersion: item.version }
    }
  }
  if (/HarmonyOS|OpenHarmony/.test(source)) {
    return { osName: 'HarmonyOS', osVersion: '' }
  }
  if (/CrOS/.test(source)) {
    return { osName: 'ChromeOS', osVersion: '' }
  }
  if (/Android/.test(source)) {
    return { osName: 'Android', osVersion: matchVersion(source, /Android[ /]([\d.]+)/) }
  }
  if (/Mac OS X/.test(source)) {
    return { osName: 'macOS', osVersion: normalizeVersion(matchVersion(source, /Mac OS X ([\d_.]+)/)) }
  }
  if (/Linux/.test(source)) {
    return { osName: 'Linux', osVersion: '' }
  }
  return { osName: '未知系统', osVersion: '' }
}

function detectDeviceType(source: string): string {
  if (/iPad|Tablet|PlayBook|Silk/.test(source)) { return '平板设备' }
  if (/Mobi|Android.*Mobile|iPhone|iPod|Windows Phone/.test(source)) { return '移动设备' }
  return '桌面设备'
}

/** 解析 User-Agent。纯函数，便于单测覆盖各浏览器写法。 */
export function parseUserAgent(userAgent: string): BrowserInfo {
  const source = userAgent || ''
  const browser = detectBrowser(source)
  const engine = detectEngine(source)
  const os = detectOs(source)
  const chromiumVersion = engine.engineName === 'Blink'
    ? matchVersion(source, /Chrome\/([\d.]+)/) || matchVersion(source, /Chromium\/([\d.]+)/)
    : ''
  return {
    browserName: browser.browserName,
    browserVersion: browser.browserVersion,
    engineName: engine.engineName,
    engineVersion: engine.engineVersion,
    osName: os.osName,
    osVersion: os.osVersion,
    deviceType: detectDeviceType(source),
    chromiumVersion,
  }
}

export function parseVersionParts(version: string): number[] {
  return (version || '')
    .split('.')
    .map(part => Number.parseInt(part, 10))
    .filter(part => Number.isFinite(part))
}

function compareVersion(left: number[], right: number[]): number {
  for (let index = 0; index < Math.max(left.length, right.length); index += 1) {
    const a = left[index] || 0
    const b = right[index] || 0
    if (a !== b) { return a > b ? 1 : -1 }
  }
  return 0
}

/**
 * 判定浏览器是否达到项目声明的最低版本。
 *
 * Blink 系（含 360、QQ 等国产壳浏览器）用 Chromium 版本对 Chrome 基线判定；
 * 其余按各自基线判定。
 */
export function evaluateBrowserSupport(info: BrowserInfo): BrowserSupport {
  let baselineKey = 'Chrome'
  if (info.browserName === 'Edge') { baselineKey = 'Edge' }
  else if (info.browserName === 'Firefox') { baselineKey = 'Firefox' }
  else if (info.browserName === 'Safari') { baselineKey = 'Safari' }

  const required = parseVersionParts(browserMinimumVersions[baselineKey])
  const baselineLabel = `${baselineKey} ${browserMinimumVersions[baselineKey]}`
  const measured = baselineKey === 'Chrome' && info.chromiumVersion
    ? info.chromiumVersion
    : info.browserVersion
  const parts = parseVersionParts(measured)
  if (!parts.length) {
    return {
      supported: true,
      baselineLabel,
      reason: `无法识别当前浏览器版本，建议使用 ${baselineLabel} 及以上版本。`,
    }
  }
  const supported = compareVersion(parts, required) >= 0
  return {
    supported,
    baselineLabel,
    reason: supported ? '' : `当前版本低于项目支持的 ${baselineLabel}，部分功能可能异常，建议升级浏览器。`,
  }
}

function readNetworkInformation(): NetworkInformationLike | undefined {
  if (typeof navigator === 'undefined') { return undefined }
  const candidate = navigator as Navigator & {
    connection?: NetworkInformationLike
    mozConnection?: NetworkInformationLike
    webkitConnection?: NetworkInformationLike
  }
  return candidate.connection || candidate.mozConnection || candidate.webkitConnection
}

export function collectNetworkSnapshot(): NetworkSnapshot {
  const online = typeof navigator === 'undefined' || navigator.onLine === undefined
    ? true
    : navigator.onLine
  const info = readNetworkInformation()
  if (!info) {
    return {
      online,
      effectiveType: '',
      connectionType: '',
      downlinkMbps: null,
      rttMs: null,
      saveData: false,
      supported: false,
    }
  }
  return {
    online,
    effectiveType: typeof info.effectiveType === 'string' ? info.effectiveType : '',
    connectionType: typeof info.type === 'string' ? info.type : '',
    downlinkMbps: typeof info.downlink === 'number' ? info.downlink : null,
    rttMs: typeof info.rtt === 'number' ? info.rtt : null,
    saveData: info.saveData === true,
    supported: true,
  }
}

function resolveTimeZone(): string {
  try {
    return new Intl.DateTimeFormat().resolvedOptions().timeZone || ''
  }
  catch {
    return ''
  }
}

export function collectDeviceSnapshot(): DeviceSnapshot {
  const hasWindow = typeof window !== 'undefined'
  const hasNavigator = typeof navigator !== 'undefined'
  const languages = hasNavigator && Array.isArray(navigator.languages)
    ? navigator.languages.slice(0, 5)
    : []
  const offsetMinutes = hasWindow && typeof Date !== 'undefined'
    ? -new Date().getTimezoneOffset()
    : null
  const connectionNavigator = hasNavigator
    ? (navigator as Navigator & { deviceMemory?: number, hardwareConcurrency?: number })
    : undefined
  return {
    language: hasNavigator ? (navigator.language || '') : '',
    languages,
    timeZone: resolveTimeZone(),
    timeZoneOffsetMinutes: offsetMinutes,
    viewportWidth: hasWindow ? window.innerWidth || 0 : 0,
    viewportHeight: hasWindow ? window.innerHeight || 0 : 0,
    screenWidth: hasWindow && window.screen ? window.screen.width || 0 : 0,
    screenHeight: hasWindow && window.screen ? window.screen.height || 0 : 0,
    pixelRatio: hasWindow && typeof window.devicePixelRatio === 'number' ? window.devicePixelRatio : 1,
    colorDepth: hasWindow && window.screen ? window.screen.colorDepth || 0 : 0,
    hardwareConcurrency: connectionNavigator && typeof connectionNavigator.hardwareConcurrency === 'number'
      ? connectionNavigator.hardwareConcurrency
      : null,
    deviceMemoryGb: connectionNavigator && typeof connectionNavigator.deviceMemory === 'number'
      ? connectionNavigator.deviceMemory
      : null,
    maxTouchPoints: hasNavigator && typeof navigator.maxTouchPoints === 'number' ? navigator.maxTouchPoints : 0,
    cookieEnabled: hasNavigator ? navigator.cookieEnabled !== false : false,
    isSecureContext: hasWindow && typeof window.isSecureContext === 'boolean' ? window.isSecureContext : false,
  }
}

/** 把时区偏移分钟数格式化为 UTC+08:00 形式。 */
export function formatTimeZoneOffset(offsetMinutes: number | null): string {
  if (offsetMinutes === null || !Number.isFinite(offsetMinutes)) { return '' }
  const sign = offsetMinutes < 0 ? '-' : '+'
  const absolute = Math.abs(offsetMinutes)
  const hours = String(Math.floor(absolute / 60)).padStart(2, '0')
  const minutes = String(absolute % 60).padStart(2, '0')
  return `UTC${sign}${hours}:${minutes}`
}

/**
 * 订阅在线状态与网络信息变化，返回取消订阅函数。
 *
 * 浏览器不支持网络信息 API 时只监听 online/offline，仍然可用。
 */
export function subscribeNetworkChanges(listener: () => void): () => void {
  if (typeof window === 'undefined') { return () => {} }
  window.addEventListener('online', listener)
  window.addEventListener('offline', listener)
  const info = readNetworkInformation()
  if (info && typeof info.addEventListener === 'function') {
    info.addEventListener('change', listener)
    return () => {
      window.removeEventListener('online', listener)
      window.removeEventListener('offline', listener)
      if (info.removeEventListener) { info.removeEventListener('change', listener) }
    }
  }
  return () => {
    window.removeEventListener('online', listener)
    window.removeEventListener('offline', listener)
  }
}

/** 网络信息 API 的 effectiveType 转中文说明。 */export function describeEffectiveType(effectiveType: string): string {
  switch (effectiveType) {
    case 'slow-2g': return '极慢（slow-2g）'
    case '2g': return '2G'
    case '3g': return '3G'
    case '4g': return '4G'
    default: return effectiveType || ''
  }
}

export function describeConnectionType(connectionType: string): string {
  switch (connectionType) {
    case 'wifi': return 'Wi-Fi'
    case 'ethernet': return '有线网络'
    case 'cellular': return '移动网络'
    case 'bluetooth': return '蓝牙'
    case 'wimax': return 'WiMAX'
    case 'none': return '无连接'
    default: return connectionType
  }
}
