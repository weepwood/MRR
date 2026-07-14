import type { Pinia } from 'pinia'
import type { Router } from 'vue-router'
import { getSystemSettings } from '@/api/modules/settings'
import { useUserStore } from '@/store/modules/user'
import {
  ARCHIVE_WATERMARK_BACKGROUND_SIZE,
  ARCHIVE_WATERMARK_SETTING_KEY,
  createArchiveWatermarkDataUrl,
  formatArchiveWatermarkTime,
  parseArchiveWatermarkEnabled,
  readLocalArchiveWatermarkSetting,
  resolveArchiveWatermarkUserId,
  SYSTEM_SETTINGS_STORAGE_KEY,
} from './archive-watermark'

const WATERMARK_REFRESH_INTERVAL = 30_000
const WATERMARK_Z_INDEX = '4000'

function isArchiveRoute(path: string): boolean {
  return path === '/archive' || path.startsWith('/archive/')
}

export function installArchiveWatermark(router: Router, pinia: Pinia): void {
  const userStore = useUserStore(pinia)
  let watermarkElement: HTMLDivElement | null = null
  let refreshTimer: number | null = null
  let syncVersion = 0

  function stopRefreshTimer() {
    if (refreshTimer !== null) {
      window.clearInterval(refreshTimer)
      refreshTimer = null
    }
  }

  function removeWatermark() {
    stopRefreshTimer()
    watermarkElement?.remove()
    watermarkElement = null
  }

  function ensureWatermarkElement(): HTMLDivElement {
    if (watermarkElement) {
      return watermarkElement
    }

    watermarkElement = document.createElement('div')
    watermarkElement.setAttribute('aria-hidden', 'true')
    watermarkElement.dataset.archiveWatermark = 'true'
    Object.assign(watermarkElement.style, {
      position: 'fixed',
      inset: '0',
      zIndex: WATERMARK_Z_INDEX,
      pointerEvents: 'none',
      backgroundRepeat: 'repeat',
      backgroundPosition: '0 0',
      backgroundSize: ARCHIVE_WATERMARK_BACKGROUND_SIZE,
    })
    document.body.appendChild(watermarkElement)
    return watermarkElement
  }

  function refreshWatermark() {
    if (!watermarkElement) {
      return
    }

    const userId = resolveArchiveWatermarkUserId(userStore.profile, userStore.account)
    const time = formatArchiveWatermarkTime(new Date())
    const darkMode = document.documentElement.classList.contains('dark')
    const dataUrl = createArchiveWatermarkDataUrl(userId, time, darkMode)
    watermarkElement.style.backgroundImage = dataUrl ? `url("${dataUrl}")` : 'none'
  }

  async function getWatermarkEnabled(): Promise<boolean> {
    const localFallback = readLocalArchiveWatermarkSetting()
    try {
      const response = await getSystemSettings()
      const serverSettings = response.data as Record<string, unknown> | undefined
      if (!serverSettings || !(ARCHIVE_WATERMARK_SETTING_KEY in serverSettings)) {
        return localFallback
      }
      return parseArchiveWatermarkEnabled(serverSettings[ARCHIVE_WATERMARK_SETTING_KEY], localFallback)
    }
    catch {
      return localFallback
    }
  }

  async function syncWatermark(path: string) {
    const version = ++syncVersion
    if (!isArchiveRoute(path)) {
      removeWatermark()
      return
    }

    const enabled = await getWatermarkEnabled()
    if (version !== syncVersion || !isArchiveRoute(router.currentRoute.value.path)) {
      return
    }
    if (!enabled) {
      removeWatermark()
      return
    }

    ensureWatermarkElement()
    refreshWatermark()
    stopRefreshTimer()
    refreshTimer = window.setInterval(refreshWatermark, WATERMARK_REFRESH_INTERVAL)
  }

  router.afterEach(to => void syncWatermark(to.path))
  window.addEventListener('storage', (event) => {
    if (event.key === SYSTEM_SETTINGS_STORAGE_KEY) {
      void syncWatermark(router.currentRoute.value.path)
    }
  })

  void syncWatermark(router.currentRoute.value.path)
}
