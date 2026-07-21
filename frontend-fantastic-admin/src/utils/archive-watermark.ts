import {
  readLocalSystemSettings,
  SYSTEM_SETTINGS_STORAGE_KEY,
} from './system-settings'

export { SYSTEM_SETTINGS_STORAGE_KEY }
export const ARCHIVE_WATERMARK_SETTING_KEY = 'archiveWatermarkEnabled'
export const ARCHIVE_WATERMARK_OPACITY_SETTING_KEY = 'archiveWatermarkOpacity'

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
  const datePart = [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
  ].join('-')
  return `${datePart} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function resolveArchiveWatermarkUserId(
  profile?: ArchiveWatermarkProfile | null,
  account?: unknown,
): string {
  const username = String(profile?.username ?? '').trim()
  const id = String(profile?.id ?? '').trim()

  if (username && id) {
    return `${username}-${id}`
  }
  if (username) {
    return username
  }
  if (id) {
    return id
  }

  const accountValue = String(account ?? '').trim()
  return accountValue || '未登录'
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

export function parseArchiveWatermarkOpacity(value: unknown, fallback = 14): number {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return fallback
  }
  return Math.min(35, Math.max(5, parsed))
}

export function readLocalArchiveWatermarkSetting(): boolean {
  return readLocalSystemSettings()?.archiveWatermarkEnabled ?? true
}

export function readLocalArchiveWatermarkOpacity(): number {
  return readLocalSystemSettings()?.archiveWatermarkOpacity ?? 14
}

export function createArchiveWatermarkDataUrl(
  userId: string,
  time: string,
  darkMode = false,
  opacityPercent = 14,
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

  const opacity = parseArchiveWatermarkOpacity(opacityPercent) / 100
  context.scale(pixelRatio, pixelRatio)
  context.translate(WATERMARK_TILE_WIDTH / 2, WATERMARK_TILE_HEIGHT / 2)
  context.rotate(-Math.PI / 7)
  context.textAlign = 'center'
  context.textBaseline = 'middle'
  context.fillStyle = darkMode
    ? `rgba(255, 255, 255, ${opacity})`
    : `rgba(17, 24, 39, ${Math.min(opacity, 0.3)})`
  context.font = '600 15px Inter, "Microsoft YaHei", sans-serif'
  context.fillText(userId, 0, -12)
  context.font = '500 13px Inter, "Microsoft YaHei", sans-serif'
  context.fillText(time, 0, 14)

  return canvas.toDataURL('image/png')
}

export const ARCHIVE_WATERMARK_BACKGROUND_SIZE = `${WATERMARK_TILE_WIDTH}px ${WATERMARK_TILE_HEIGHT}px`
