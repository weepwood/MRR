import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '@/api/modules/settings'
import {
  createDefaultSystemSettings,
  loadEffectiveSystemSettings,
  SYSTEM_SETTINGS_STORAGE_KEY,
} from '../system-settings'

vi.mock('@/api/modules/settings', () => ({ getSystemSettings: vi.fn() }))

type SettingsResponse = Awaited<ReturnType<typeof api.getSystemSettings>>
const respond = (data: Record<string, string>) => ({ code: 200, data } as unknown as SettingsResponse)

/**
 * 读取来源契约。
 *
 * 只读消费者（档案袋、水印、患者页、页面标题）在后端不可用时要能降级到缓存或默认值，
 * 但设置页只能以服务器读取成功的数据作为「可保存基线」。两类调用方共用同一个加载器，
 * 因此加载器必须如实报告服务器是否可达，而不是把失败吞成一次普通的回退。
 */
describe('loadEffectiveSystemSettings 读取来源', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    localStorage.clear()
  })
  afterEach(() => localStorage.clear())

  it('服务器返回设置：来源为 server，并标记服务器可达', async () => {
    vi.mocked(api.getSystemSettings).mockResolvedValueOnce(respond({ systemName: '服务器名称', developerModeEnabled: 'false' }))
    const result = await loadEffectiveSystemSettings()
    expect(result.source).toBe('server')
    expect(result.serverReachable).toBe(true)
    expect(result.settings.systemName).toBe('服务器名称')
  })

  it('请求失败：回退到本地缓存，但明确标记服务器不可达', async () => {
    localStorage.setItem(SYSTEM_SETTINGS_STORAGE_KEY, JSON.stringify({ ...createDefaultSystemSettings(), systemName: '缓存名称', developerModeEnabled: true }))
    vi.mocked(api.getSystemSettings).mockRejectedValueOnce(new Error('network'))
    const result = await loadEffectiveSystemSettings()
    expect(result.source).toBe('local')
    expect(result.serverReachable).toBe(false)
    expect(result.settings.systemName).toBe('缓存名称')
  })

  it('请求失败且没有缓存：回退到默认值，同样标记服务器不可达', async () => {
    vi.mocked(api.getSystemSettings).mockRejectedValueOnce(new Error('network'))
    const result = await loadEffectiveSystemSettings()
    expect(result.source).toBe('default')
    expect(result.serverReachable).toBe(false)
  })

  it('服务器可达但尚无任何设置（全新部署）：标记可达，只读消费者仍可用缓存', async () => {
    localStorage.setItem(SYSTEM_SETTINGS_STORAGE_KEY, JSON.stringify({ ...createDefaultSystemSettings(), systemName: '缓存名称' }))
    vi.mocked(api.getSystemSettings).mockResolvedValueOnce(respond({}))
    const result = await loadEffectiveSystemSettings()
    expect(result.serverReachable).toBe(true)
    expect(result.source).toBe('local')
  })
})
