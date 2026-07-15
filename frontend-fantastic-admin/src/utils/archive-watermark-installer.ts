import type { Pinia } from 'pinia'
import type { Router } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import {
  loadEffectiveSystemSettings,
  readLocalSystemSettings,
  SYSTEM_SETTINGS_STORAGE_KEY,
  SYSTEM_SETTINGS_UPDATED_EVENT,
  type EffectiveSystemSettings,
} from './system-settings'
import {
  ARCHIVE_WATERMARK_BACKGROUND_SIZE,
  createArchiveWatermarkDataUrl,
  formatArchiveWatermarkTime,
  resolveArchiveWatermarkUserId,
} from './archive-watermark'

const WATERMARK_REFRESH_INTERVAL = 1_000
const WATERMARK_Z_INDEX = '4000'

function isArchiveRoute(path: string): boolean {
  return path === '/archive' || path.startsWith('/archive/')
}

export function installArchiveWatermark(router: Router, pinia: Pinia): void {
  const userStore = useUserStore(pinia)
  let watermarkElement: HTMLDivElement | null = null
  let refreshTimer: number | null = null
  let syncVersion = 0
  let currentOpacity = 14

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
    const dataUrl = createArchiveWatermarkDataUrl(userId, time, darkMode, currentOpacity)
    watermarkElement.style.backgroundImage = dataUrl ? `url("${dataUrl}")` : 'none'
  }

  function applyWatermarkSettings(path: string, settings: EffectiveSystemSettings) {
    if (!isArchiveRoute(path) || !settings.archiveWatermarkEnabled) {
      removeWatermark()
      return
    }

    currentOpacity = settings.archiveWatermarkOpacity
    ensureWatermarkElement()
    refreshWatermark()
    stopRefreshTimer()
    refreshTimer = window.setInterval(refreshWatermark, WATERMARK_REFRESH_INTERVAL)
  }

  async function syncWatermark(path: string) {
    const version = ++syncVersion
    if (!isArchiveRoute(path)) {
      removeWatermark()
      return
    }

    const { settings } = await loadEffectiveSystemSettings()
    if (version !== syncVersion || !isArchiveRoute(router.currentRoute.value.path)) {
      return
    }
    applyWatermarkSettings(path, settings)
  }

  router.afterEach(to => void syncWatermark(to.path))

  window.addEventListener(SYSTEM_SETTINGS_UPDATED_EVENT, (event) => {
    const settings = (event as CustomEvent<EffectiveSystemSettings>).detail
    if (settings) {
      applyWatermarkSettings(router.currentRoute.value.path, settings)
    }
  })

  window.addEventListener('storage', (event) => {
    if (event.key !== SYSTEM_SETTINGS_STORAGE_KEY) {
      return
    }
    const settings = readLocalSystemSettings()
    if (settings) {
      applyWatermarkSettings(router.currentRoute.value.path, settings)
    }
  })

  void syncWatermark(router.currentRoute.value.path)
}
