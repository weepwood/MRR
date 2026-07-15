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
      archiveTypeDisplayMode: 'tree',
      archiveThumbnailSize: 260,
      archiveFitMode: 'width',
      archiveHideScrollbars: true,
    })

    expect(result).toEqual({
      archivePreviewMode: 'scroll',
      archiveTypeDisplayMode: 'tree',
      archiveThumbnailSize: 260,
      archiveFitMode: 'width',
      archiveHideScrollbars: true,
      archiveDepartmentColorsEnabled: false,
    })
  })

  it('persists only valid local preferences', () => {
    writeArchiveLocalPreferences({ archivePreviewMode: 'scroll', archiveTypeDisplayMode: 'tree', archiveThumbnailSize: 999, archiveHideScrollbars: true })

    expect(readArchiveLocalPreferences()).toEqual({
      archivePreviewMode: 'scroll',
      archiveTypeDisplayMode: 'tree',
      archiveThumbnailSize: 320,
      archiveHideScrollbars: true,
    })
    expect(localStorage.getItem(ARCHIVE_LOCAL_PREFERENCES_STORAGE_KEY)).toContain('scroll')
  })
})
