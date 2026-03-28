import { describe, expect, it } from 'vitest'
import { adminDefaultSettings, adminSettingsStorageKey } from '../constants/adminDashboard'
import { normalizeAdminUrl, useAdminSettings } from './useAdminSettings'

describe('normalizeAdminUrl', () => {
  it('resolves relative urls against the current origin', () => {
    expect(normalizeAdminUrl('/swagger-ui/index.html')).toBe(
      new URL('/swagger-ui/index.html', window.location.origin).toString()
    )
  })

  it('returns an empty string for blank input', () => {
    expect(normalizeAdminUrl('   ')).toBe('')
  })
})

describe('useAdminSettings', () => {
  it('loads defaults when storage is empty', () => {
    const { settings, swaggerUrl, resolvedSwaggerUrl } = useAdminSettings()

    expect(settings).toMatchObject(adminDefaultSettings)
    expect(swaggerUrl.value).toBe(adminDefaultSettings.swaggerUrl)
    expect(resolvedSwaggerUrl.value).toBe(
      new URL(adminDefaultSettings.swaggerUrl, window.location.origin).toString()
    )
  })

  it('loads, saves, and resets persisted settings', () => {
    const stored = {
      ...adminDefaultSettings,
      systemName: 'Custom System',
      swaggerUrl: '/docs/swagger'
    }
    localStorage.setItem(adminSettingsStorageKey, JSON.stringify(stored))

    const { settings, loadSettings, saveSettings, resetSettings } = useAdminSettings()

    loadSettings()
    expect(settings.systemName).toBe('Custom System')
    expect(settings.swaggerUrl).toBe('/docs/swagger')

    const snapshot = saveSettings({
      ...settings,
      swaggerUrl: 'https://example.com/swagger'
    })
    expect(snapshot.swaggerUrl).toBe('https://example.com/swagger')
    expect(JSON.parse(localStorage.getItem(adminSettingsStorageKey))).toMatchObject(snapshot)

    resetSettings()
    expect(settings).toMatchObject(adminDefaultSettings)
    expect(localStorage.getItem(adminSettingsStorageKey)).toBeNull()
  })
})
