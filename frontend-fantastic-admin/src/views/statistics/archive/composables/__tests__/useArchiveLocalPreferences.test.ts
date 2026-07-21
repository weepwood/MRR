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
      archivePreviewScale: 125,
      archiveScrollbarMode: 'semi-hidden',
    })

    expect(result).toEqual({
      archivePreviewMode: 'scroll',
      archiveTypeDisplayMode: 'double-column',
      archiveThumbnailSize: 260,
      archiveFitMode: 'width',
      archivePreviewScale: 125,
      archiveScrollbarMode: 'semi-hidden',
      archiveDepartmentColorsEnabled: false,
    })
  })

  it('persists only valid local preferences', () => {
    writeArchiveLocalPreferences({ archivePreviewMode: 'scroll', archiveTypeDisplayMode: 'single-column', archiveThumbnailSize: 999, archiveScrollbarMode: 'hidden' })

    expect(readArchiveLocalPreferences()).toEqual({
      archivePreviewMode: 'scroll',
      archiveTypeDisplayMode: 'single-column',
      archiveThumbnailSize: 480,
      archiveScrollbarMode: 'hidden',
    })
    expect(localStorage.getItem(ARCHIVE_LOCAL_PREFERENCES_STORAGE_KEY)).toContain('scroll')
  })

  it('migrates legacy button and tree display preferences to column layouts', () => {
    localStorage.setItem(ARCHIVE_LOCAL_PREFERENCES_STORAGE_KEY, JSON.stringify({ archiveTypeDisplayMode: 'tree' }))
    expect(readArchiveLocalPreferences().archiveTypeDisplayMode).toBe('single-column')

    localStorage.setItem(ARCHIVE_LOCAL_PREFERENCES_STORAGE_KEY, JSON.stringify({ archiveTypeDisplayMode: 'buttons' }))
    expect(readArchiveLocalPreferences().archiveTypeDisplayMode).toBe('double-column')
  })

  it('migrates the legacy scrollbar toggle to the matching visibility mode', () => {
    localStorage.setItem(ARCHIVE_LOCAL_PREFERENCES_STORAGE_KEY, JSON.stringify({ archiveHideScrollbars: true }))
    expect(readArchiveLocalPreferences().archiveScrollbarMode).toBe('hidden')

    localStorage.setItem(ARCHIVE_LOCAL_PREFERENCES_STORAGE_KEY, JSON.stringify({ archiveHideScrollbars: false }))
    expect(readArchiveLocalPreferences().archiveScrollbarMode).toBe('visible')
  })

  it('hides scrollbars by default', () => {
    expect(resolveArchiveDisplayPreferences(createDefaultSystemSettings(), {}).archiveScrollbarMode).toBe('hidden')
  })

  it('clamps the scroll preview scale', () => {
    expect(resolveArchiveDisplayPreferences(createDefaultSystemSettings(), { archivePreviewScale: 999 }).archivePreviewScale).toBe(150)
    expect(resolveArchiveDisplayPreferences(createDefaultSystemSettings(), { archivePreviewScale: 1 }).archivePreviewScale).toBe(50)
  })
})
