import { describe, expect, it } from 'vitest'
import {
  createDefaultSystemSettings,
  parseSystemSettings,
  serializeSystemSettings,
} from '../system-settings'

describe('effective system settings', () => {
  it('restores supported value types from server strings', () => {
    const settings = parseSystemSettings({
      systemName: '病案影像中心',
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
    })

    expect(settings).toEqual({
      systemName: '病案影像中心',
      imageSource: 'oss',
      archiveDefaultView: 'list',
      archivePreviewMode: 'scroll',
      archiveThumbnailSize: 260,
      archivePreloadCount: 40,
      archiveAutoFit: false,
      archiveRememberSelection: true,
      archiveWatermarkEnabled: false,
      archiveWatermarkOpacity: 22,
      archiveIpMaxChanges: 5,
      patientIdCardRevealEnabled: true,
      patientIdCardCopyEnabled: true,
    })
  })

  it('uses local images by default and rejects unsupported modes', () => {
    const settings = parseSystemSettings({
      imageSource: 's3',
      archiveDefaultView: 'single',
      archivePreviewMode: 'grid',
      archiveThumbnailSize: 999,
      archivePreloadCount: 1,
      archiveWatermarkOpacity: -10,
      archiveIpMaxChanges: 99,
    })

    expect(settings.imageSource).toBe('local')
    expect(settings.archiveDefaultView).toBe('thumb')
    expect(settings.archivePreviewMode).toBe('single')
    expect(settings.archiveThumbnailSize).toBe(320)
    expect(settings.archivePreloadCount).toBe(10)
    expect(settings.archiveWatermarkOpacity).toBe(5)
    expect(settings.archiveIpMaxChanges).toBe(20)
  })

  it('serializes every effective setting for the key-value API', () => {
    const defaults = createDefaultSystemSettings()
    const serialized = serializeSystemSettings(defaults)

    expect(defaults.imageSource).toBe('local')
    expect(defaults.archiveIpMaxChanges).toBe(3)
    expect(defaults.patientIdCardRevealEnabled).toBe(false)
    expect(defaults.patientIdCardCopyEnabled).toBe(false)
    expect(serialized.imageSource).toBe('local')
    expect(serialized.archiveAutoFit).toBe('true')
    expect(serialized.archiveThumbnailSize).toBe('200')
    expect(serialized.archiveIpMaxChanges).toBe('3')
    expect(serialized.patientIdCardRevealEnabled).toBe('false')
    expect(serialized.patientIdCardCopyEnabled).toBe('false')
    expect(Object.keys(serialized)).toHaveLength(Object.keys(defaults).length)
  })
})
