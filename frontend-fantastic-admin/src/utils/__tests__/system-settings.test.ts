import { describe, expect, it } from 'vitest'
import {
  createDefaultSystemSettings,
  parseSystemSettings,
  serializeSystemSettings,
} from '../system-settings'

describe('effective system settings', () => {
  it('restores supported branding, support and archive value types from server strings', () => {
    const settings = parseSystemSettings({
      systemName: '病案影像中心',
      systemShortName: '影像中心',
      systemEnglishName: 'Medical Archive Center',
      organizationName: '测试医院',
      loginFeatureEnabled: 'false',
      systemAdminContactEnabled: 'true',
      systemAdminPublicVisible: '1',
      systemAdminPhone: '0571-12345678',
      imageSource: 'oss',
      archiveDefaultView: 'list',
      archivePreviewMode: 'scroll',
      archiveThumbnailSize: '260',
      archivePreloadCount: '40',
      archiveAutoFit: 'false',
      archiveRememberSelection: 'true',
      archiveWatermarkEnabled: '0',
      archiveWatermarkOpacity: '22',
      archiveIpMaxChanges: '5',
      patientIdCardRevealEnabled: 'true',
      patientIdCardCopyEnabled: '1',
      developerModeEnabled: 'enabled',
      developerModeApiAccessEnabled: 'true',
      developerModeAllowedSources: '192.168.10.25\n192.168.20.0/24',
    })

    expect(settings.systemName).toBe('病案影像中心')
    expect(settings.systemShortName).toBe('影像中心')
    expect(settings.systemEnglishName).toBe('Medical Archive Center')
    expect(settings.organizationName).toBe('测试医院')
    expect(settings.loginFeatureEnabled).toBe(false)
    expect(settings.systemAdminContactEnabled).toBe(true)
    expect(settings.systemAdminPublicVisible).toBe(true)
    expect(settings.systemAdminPhone).toBe('0571-12345678')
    expect(settings.imageSource).toBe('oss')
    expect(settings.archiveDefaultView).toBe('list')
    expect(settings.archivePreviewMode).toBe('scroll')
    expect(settings.archiveThumbnailSize).toBe(260)
    expect(settings.archivePreloadCount).toBe(40)
    expect(settings.archiveAutoFit).toBe(false)
    expect(settings.archiveRememberSelection).toBe(true)
    expect(settings.archiveWatermarkEnabled).toBe(false)
    expect(settings.archiveWatermarkOpacity).toBe(22)
    expect(settings.archiveIpMaxChanges).toBe(5)
    expect(settings.patientIdCardRevealEnabled).toBe(true)
    expect(settings.patientIdCardCopyEnabled).toBe(true)
    expect(settings.developerModeEnabled).toBe(true)
    expect(settings.developerModeApiAccessEnabled).toBe(true)
    expect(settings.developerModeAllowedSources).toBe('192.168.10.25\n192.168.20.0/24')
  })

  it('uses secure defaults and rejects unsupported modes', () => {
    const settings = parseSystemSettings({
      imageSource: 's3',
      archiveDefaultView: 'single',
      archivePreviewMode: 'grid',
      archiveThumbnailSize: 999,
      archivePreloadCount: 1,
      archiveWatermarkOpacity: -10,
      archiveIpMaxChanges: 99,
      systemAdminPublicVisible: 'unexpected',
      developerModeEnabled: 'unexpected',
      developerModeApiAccessEnabled: 'unexpected',
    })

    expect(settings.imageSource).toBe('local')
    expect(settings.archiveDefaultView).toBe('thumb')
    expect(settings.archivePreviewMode).toBe('single')
    expect(settings.archiveThumbnailSize).toBe(320)
    expect(settings.archivePreloadCount).toBe(10)
    expect(settings.archiveWatermarkOpacity).toBe(5)
    expect(settings.archiveIpMaxChanges).toBe(20)
    expect(settings.systemAdminContactEnabled).toBe(false)
    expect(settings.systemAdminPublicVisible).toBe(false)
    expect(settings.developerModeEnabled).toBe(false)
    expect(settings.developerModeApiAccessEnabled).toBe(false)
    expect(settings.developerModeAllowedSources).toBe('127.0.0.1\n::1')
  })

  it('serializes developer mode for the key-value API', () => {
    const defaults = createDefaultSystemSettings()
    const serialized = serializeSystemSettings({
      ...defaults,
      developerModeEnabled: true,
      developerModeApiAccessEnabled: true,
      developerModeAllowedSources: '192.168.10.25\n192.168.20.0/24',
    })

    expect(defaults.imageSource).toBe('local')
    expect(defaults.systemAdminContactEnabled).toBe(false)
    expect(defaults.systemAdminPublicVisible).toBe(false)
    expect(defaults.developerModeApiAccessEnabled).toBe(false)
    expect(serialized.systemName).toBe('MRR 病案文件管理系统')
    expect(serialized.loginFeatureEnabled).toBe('true')
    expect(serialized.systemAdminContactEnabled).toBe('false')
    expect(serialized.archiveAutoFit).toBe('true')
    expect(serialized.archiveThumbnailSize).toBe('200')
    expect(serialized.developerModeEnabled).toBe('true')
    expect(serialized.developerModeApiAccessEnabled).toBe('true')
    expect(serialized.developerModeAllowedSources).toBe('192.168.10.25\n192.168.20.0/24')
    expect(Object.keys(serialized)).toHaveLength(Object.keys(defaults).length)
  })
})
