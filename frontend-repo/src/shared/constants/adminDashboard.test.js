import { describe, expect, it } from 'vitest'
import { adminDefaultSettings, adminSectionMetaMap, adminSettingsStorageKey } from './adminDashboard'

describe('admin dashboard constants', () => {
  it('keeps the storage key stable', () => {
    expect(adminSettingsStorageKey).toBe('admin-dashboard-settings')
  })

  it('defines the expected default settings', () => {
    expect(adminDefaultSettings).toMatchObject({
      systemName: expect.any(String),
      maxFileSize: 10,
      sessionTimeout: 30,
      logLevel: 'info',
      swaggerUrl: '/swagger-ui/index.html'
    })
  })

  it('contains metadata for each admin module', () => {
    expect(Object.keys(adminSectionMetaMap)).toEqual([
      'users',
      'records',
      'testing',
      'logs',
      'monitoring',
      'settings'
    ])

    for (const meta of Object.values(adminSectionMetaMap)) {
      expect(meta).toEqual(
        expect.objectContaining({
          title: expect.any(String),
          description: expect.any(String),
          pill: expect.any(String)
        })
      )
    }
  })
})
