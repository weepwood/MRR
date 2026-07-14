export const SYSTEM_SETTINGS_STORAGE_KEY = 'MRR-ADMIN:system-settings'
export const ARCHIVE_WATERMARK_SETTING_KEY = 'archiveWatermarkEnabled'

const WATERMARK_TILE_WIDTH = 360
const WATERMARK_TILE_HEIGHT = 210

export interface ArchiveWatermarkProfile {
  id?: unknown
  username?: unknown
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

export function formatArchiveWatermarkTime(date: Date): string {
  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
  ].join('-') + ` ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function resolveArchiveWatermarkUserId(
  profile?: ArchiveWatermarkProfile | null,
  account?: unknown,
): string {
  const candidates = [profile?.id, profile?.username, account]
  for (const candidate of candidates) {
    const value = String(candidate ?? '').trim()
    if (value) {
      return value
    }
  }
  return '未登录'
}

export function parseArchiveWatermarkEnabled(value: unknown, fallback = true): boolean {
  if (typeof value === 'boolean') {
    return value
  }
  if (typeof value === 'number') {
    return value !== 0
  }
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()
    if (['true', '1', 'yes', 'on'].includes(normalized)) {
      return true
    }
    if (['false', '0', 'no', 'off'].includes(normalized)) {
      return false
    }
  }
  return fallback
}

export function readLocalArchiveWatermarkSetting(): boolean {
  try {
    const raw = localStorage.getItem(SYSTEM_SETTINGS_STORAGE_KEY)
    if (!raw) {
      return true
    }
    const settings = JSON.parse(raw) as Record<string, unknown>
    return parseArchiveWatermarkEnabled(settings[ARCHIVE_WATERMARK_SETTING_KEY], true)
  }
  catch {
    return true
  }
}

export function createArchiveWatermarkDataUrl(
  userId: string,
  time: string,
  darkMode = false,
): string {
  if (typeof document === 'undefined') {
    return ''
  }

  const canvas = document.createElement('canvas')
  const pixelRatio = Math.min(window.devicePixelRatio || 1, 2)
  canvas.width = WATERMARK_TILE_WIDTH * pixelRatio
  canvas.height = WATERMARK_TILE_HEIGHT * pixelRatio

  const context = canvas.getContext('2d')
  if (!context) {
    return ''
  }

  context.scale(pixelRatio, pixelRatio)
  context.translate(WATERMARK_TILE_WIDTH / 2, WATERMARK_TILE_HEIGHT / 2)
  context.rotate(-Math.PI / 7)
  context.textAlign = 'center'
  context.textBaseline = 'middle'
  context.fillStyle = darkMode ? 'rgb(255 255 255 / 14%)' : 'rgb(17 24 39 / 12%)'
  context.font = '600 15px Inter, "Microsoft YaHei", sans-serif'
  context.fillText(`用户ID：${userId}`, 0, -12)
  context.font = '500 13px Inter, "Microsoft YaHei", sans-serif'
  context.fillText(`时间：${time}`, 0, 14)

  return canvas.toDataURL('image/png')
}

export const ARCHIVE_WATERMARK_BACKGROUND_SIZE = `${WATERMARK_TILE_WIDTH}px ${WATERMARK_TILE_HEIGHT}px`
