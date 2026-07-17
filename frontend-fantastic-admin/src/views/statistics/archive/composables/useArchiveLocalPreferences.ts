import type { ArchivePreviewMode, EffectiveSystemSettings } from '@/utils/system-settings'

export const ARCHIVE_LOCAL_PREFERENCES_STORAGE_KEY = 'MRR-ADMIN:archive-local-preferences'

export type ArchiveTypeDisplayMode = 'double-column' | 'single-column'
export type ArchiveFitMode = 'height' | 'width'

export interface ArchiveLocalPreferences {
  archivePreviewMode?: ArchivePreviewMode
  archiveTypeDisplayMode?: ArchiveTypeDisplayMode
  archiveThumbnailSize?: number
  archiveFitMode?: ArchiveFitMode
  archiveHideScrollbars?: boolean
  archiveDepartmentColorsEnabled?: boolean
}

export interface ArchiveDisplayPreferences {
  archivePreviewMode: ArchivePreviewMode
  archiveTypeDisplayMode: ArchiveTypeDisplayMode
  archiveThumbnailSize: number
  archiveFitMode: ArchiveFitMode
  archiveHideScrollbars: boolean
  archiveDepartmentColorsEnabled: boolean
}

function parsePreferences(value: unknown): ArchiveLocalPreferences {
  if (!value || typeof value !== 'object') {
    return {}
  }

  const source = value as Record<string, unknown>
  const thumbnailSize = Number(source.archiveThumbnailSize)
  const legacyAutoFit = typeof source.archiveAutoFit === 'boolean' ? source.archiveAutoFit : undefined
  return {
    archivePreviewMode: source.archivePreviewMode === 'scroll' || source.archivePreviewMode === 'single'
      ? source.archivePreviewMode
      : undefined,
    archiveTypeDisplayMode: source.archiveTypeDisplayMode === 'single-column' || source.archiveTypeDisplayMode === 'tree'
      ? 'single-column'
      : source.archiveTypeDisplayMode === 'double-column' || source.archiveTypeDisplayMode === 'buttons'
        ? 'double-column'
        : undefined,
    archiveThumbnailSize: Number.isFinite(thumbnailSize)
      ? Math.min(320, Math.max(160, thumbnailSize))
      : undefined,
    archiveFitMode: source.archiveFitMode === 'width' || source.archiveFitMode === 'height'
      ? source.archiveFitMode
      : legacyAutoFit === undefined ? undefined : legacyAutoFit ? 'height' : 'width',
    archiveHideScrollbars: typeof source.archiveHideScrollbars === 'boolean'
      ? source.archiveHideScrollbars
      : undefined,
    archiveDepartmentColorsEnabled: typeof source.archiveDepartmentColorsEnabled === 'boolean'
      ? source.archiveDepartmentColorsEnabled
      : undefined,
  }
}

export function readArchiveLocalPreferences(): ArchiveLocalPreferences {
  try {
    const raw = localStorage.getItem(ARCHIVE_LOCAL_PREFERENCES_STORAGE_KEY)
    return raw ? parsePreferences(JSON.parse(raw)) : {}
  }
  catch {
    return {}
  }
}

export function writeArchiveLocalPreferences(preferences: ArchiveLocalPreferences): void {
  try {
    localStorage.setItem(ARCHIVE_LOCAL_PREFERENCES_STORAGE_KEY, JSON.stringify(parsePreferences(preferences)))
  }
  catch {
    // 浏览器禁用本地存储时，仍可在本次打开期间调整显示方式。
  }
}

export function clearArchiveLocalPreferences(): void {
  try {
    localStorage.removeItem(ARCHIVE_LOCAL_PREFERENCES_STORAGE_KEY)
  }
  catch {
    // 忽略受限浏览器环境中的本地存储异常。
  }
}

export function resolveArchiveDisplayPreferences(
  settings: EffectiveSystemSettings,
  local: ArchiveLocalPreferences,
): ArchiveDisplayPreferences {
  const preferences = parsePreferences(local)
  return {
    archivePreviewMode: preferences.archivePreviewMode ?? settings.archivePreviewMode,
    archiveTypeDisplayMode: preferences.archiveTypeDisplayMode ?? 'double-column',
    archiveThumbnailSize: preferences.archiveThumbnailSize ?? settings.archiveThumbnailSize,
    archiveFitMode: preferences.archiveFitMode ?? (settings.archiveAutoFit ? 'height' : 'width'),
    archiveHideScrollbars: preferences.archiveHideScrollbars ?? true,
    archiveDepartmentColorsEnabled: preferences.archiveDepartmentColorsEnabled ?? false,
  }
}
