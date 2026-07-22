import { describe, expect, it } from 'vitest'
import { canUseArchiveLegacyRoute } from '../developer-mode'

describe('developer archive legacy route', () => {
  const enabledStatus = {
    enabled: true,
    accessMode: 'ARCHIVE_LEGACY' as const,
  }

  it('仅允许独立影像档案袋路由', () => {
    expect(canUseArchiveLegacyRoute('archive', enabledStatus)).toBe(true)
    expect(canUseArchiveLegacyRoute('archiveEmbedded', enabledStatus)).toBe(false)
    expect(canUseArchiveLegacyRoute('settings', enabledStatus)).toBe(false)
    expect(canUseArchiveLegacyRoute('users', enabledStatus)).toBe(false)
  })

  it('模式关闭时拒绝匿名进入档案袋', () => {
    expect(canUseArchiveLegacyRoute('archive', {
      enabled: false,
      accessMode: 'DISABLED',
    })).toBe(false)
  })
})
