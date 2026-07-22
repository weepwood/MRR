import type { DeveloperModeStatus } from '../developer-mode'
import { describe, expect, it } from 'vitest'
import { canUseArchiveLegacyRoute, canUseDeveloperApi } from '../developer-mode'

describe('developer mode access', () => {
  const archiveStatus: DeveloperModeStatus = {
    enabled: true,
    accessMode: 'ARCHIVE_LEGACY',
  }

  it('只读兼容模式仅允许独立影像档案袋路由', () => {
    expect(canUseArchiveLegacyRoute('archive', archiveStatus)).toBe(true)
    expect(canUseArchiveLegacyRoute('archiveEmbedded', archiveStatus)).toBe(false)
    expect(canUseArchiveLegacyRoute('settings', archiveStatus)).toBe(false)
    expect(canUseArchiveLegacyRoute('users', archiveStatus)).toBe(false)
  })

  it('完整 API 模式必须携带后端虚拟会话', () => {
    const status: DeveloperModeStatus = {
      enabled: true,
      accessMode: 'API_FULL',
      session: {
        username: 'developer-api',
        displayName: 'Developer API',
        roleCode: 'DEVELOPER_API',
        roleName: 'Developer Full API',
        status: 'active',
        mustChangePassword: false,
        passwordVersion: 1,
        permissions: ['record:manage', 'system:manage'],
      },
    }

    expect(canUseDeveloperApi(status)).toBe(true)
    expect(canUseArchiveLegacyRoute('archive', status)).toBe(false)
  })

  it('模式关闭时拒绝匿名访问', () => {
    const status: DeveloperModeStatus = {
      enabled: false,
      accessMode: 'DISABLED',
    }

    expect(canUseArchiveLegacyRoute('archive', status)).toBe(false)
    expect(canUseDeveloperApi(status)).toBe(false)
  })
})
