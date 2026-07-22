import type { DeveloperModeStatus } from '../developer-mode'
import { describe, expect, it } from 'vitest'
import { canUseArchiveLegacyRoute } from '../developer-mode'

describe('developer mode access', () => {
  const archiveStatus: DeveloperModeStatus = {
    enabled: true,
    accessMode: 'ARCHIVE_LEGACY',
    apiPermissionBypassEnabled: false,
  }

  it('只读兼容模式仅允许独立影像档案袋路由', () => {
    expect(canUseArchiveLegacyRoute('archive', archiveStatus)).toBe(true)
    expect(canUseArchiveLegacyRoute('archiveEmbedded', archiveStatus)).toBe(false)
    expect(canUseArchiveLegacyRoute('settings', archiveStatus)).toBe(false)
    expect(canUseArchiveLegacyRoute('users', archiveStatus)).toBe(false)
  })

  it('api 权限旁路不会开放匿名后台路由', () => {
    const status: DeveloperModeStatus = {
      enabled: false,
      accessMode: 'DISABLED',
      apiPermissionBypassEnabled: true,
    }

    expect(canUseArchiveLegacyRoute('settings', status)).toBe(false)
    expect(canUseArchiveLegacyRoute('users', status)).toBe(false)
  })

  it('模式关闭时拒绝匿名访问', () => {
    const status: DeveloperModeStatus = {
      enabled: false,
      accessMode: 'DISABLED',
      apiPermissionBypassEnabled: false,
    }

    expect(canUseArchiveLegacyRoute('archive', status)).toBe(false)
  })
})
