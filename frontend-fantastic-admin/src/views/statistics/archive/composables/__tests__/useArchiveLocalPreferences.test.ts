import { afterEach, describe, expect, it } from 'vitest'
import { createDefaultSystemSettings } from '@/utils/system-settings'
import {
  ARCHIVE_LOCAL_PREFERENCES_STORAGE_KEY,
  readArchiveLocalPreferences,
  resolveArchiveDisplayPreferences,
  writeArchiveLocalPreferences,
} from '../useArchiveLocalPreferences'

afterEach(() => localStorage.clear())

describe('archive local preferences', () => {
  it('uses local display preferences without changing the remaining system settings', () => {
    const settings = createDefaultSystemSettings()
    const result = resolveArchiveDisplayPreferences(settings, {
      archivePreviewMode: 'scroll',
      archiveTypeDisplayMode: 'double-column',
      archiveThumbnailSize: 260,
      archiveFitMode: 'width',
      archiveHideScrollbars: true,
    })

    expect(result).toEqual({
      archivePreviewMode: 'scroll',
      archiveTypeDisplayMode: 'double-column',
      archiveThumbnailSize: 260,
      archiveFitMode: 'width',
      archiveHideScrollbars: true,
      archiveDepartmentColorsEnabled: false,
    })
  })

  it('persists only valid local preferences', () => {
    writeArchiveLocalPreferences({ archivePreviewMode: 'scroll', archiveTypeDisplayMode: 'single-column', archiveThumbnailSize: 999, archiveHideScrollbars: true })

    expect(readArchiveLocalPreferences()).toEqual({
      archivePreviewMode: 'scroll',
      archiveTypeDisplayMode: 'single-column',
      archiveThumbnailSize: 320,
      archiveHideScrollbars: true,
    })
    expect(localStorage.getItem(ARCHIVE_LOCAL_PREFERENCES_STORAGE_KEY)).toContain('scroll')
  })

  it('migrates legacy button and tree display preferences to column layouts', () => {
    localStorage.setItem(ARCHIVE_LOCAL_PREFERENCES_STORAGE_KEY, JSON.stringify({ archiveTypeDisplayMode: 'tree' }))
    expect(readArchiveLocalPreferences().archiveTypeDisplayMode).toBe('single-column')

    localStorage.setItem(ARCHIVE_LOCAL_PREFERENCES_STORAGE_KEY, JSON.stringify({ archiveTypeDisplayMode: 'buttons' }))
    expect(readArchiveLocalPreferences().archiveTypeDisplayMode).toBe('double-column')
  })
})
