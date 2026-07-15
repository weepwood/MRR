import type { ArchivePreviewMode, EffectiveSystemSettings } from '@/utils/system-settings'

export const ARCHIVE_LOCAL_PREFERENCES_STORAGE_KEY = 'MRR-ADMIN:archive-local-preferences'

export interface ArchiveLocalPreferences {
  archivePreviewMode?: ArchivePreviewMode
  archiveThumbnailSize?: number
  archiveAutoFit?: boolean
}

export interface ArchiveDisplayPreferences {
  archivePreviewMode: ArchivePreviewMode
  archiveThumbnailSize: number
  archiveAutoFit: boolean
}

function parsePreferences(value: unknown): ArchiveLocalPreferences {
  if (!value || typeof value !== 'object') {
    return {}
  }

  const source = value as Record<string, unknown>
  const thumbnailSize = Number(source.archiveThumbnailSize)
  return {
    archivePreviewMode: source.archivePreviewMode === 'scroll' || source.archivePreviewMode === 'single'
      ? source.archivePreviewMode
      : undefined,
    archiveThumbnailSize: Number.isFinite(thumbnailSize)
      ? Math.min(320, Math.max(160, thumbnailSize))
      : undefined,
    archiveAutoFit: typeof source.archiveAutoFit === 'boolean' ? source.archiveAutoFit : undefined,
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
    archiveThumbnailSize: preferences.archiveThumbnailSize ?? settings.archiveThumbnailSize,
    archiveAutoFit: preferences.archiveAutoFit ?? settings.archiveAutoFit,
  }
}
