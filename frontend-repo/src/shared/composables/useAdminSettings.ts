import { computed, reactive } from 'vue'
import { adminDefaultSettings, adminSettingsStorageKey } from '../constants/adminDashboard'

const cloneDefaults = () => ({ ...adminDefaultSettings })

const readSettings = () => {
  if (typeof window === 'undefined') {
    return cloneDefaults()
  }

  try {
    const raw = window.localStorage.getItem(adminSettingsStorageKey)
    if (!raw) {
      return cloneDefaults()
    }

    return { ...adminDefaultSettings, ...JSON.parse(raw) }
  } catch {
    return cloneDefaults()
  }
}

const writeSettings = (settings) => {
  if (typeof window === 'undefined') return
  window.localStorage.setItem(adminSettingsStorageKey, JSON.stringify(settings))
}

export const normalizeAdminUrl = (value) => {
  const raw = String(value || '').trim()
  if (!raw) return ''

  try {
    return new URL(raw, window.location.origin).toString()
  } catch {
    return raw
  }
}

export function useAdminSettings() {
  const settings = reactive(cloneDefaults())

  const loadSettings = () => {
    Object.assign(settings, readSettings())
  }

  const saveSettings = (nextSettings = settings) => {
    const snapshot = { ...adminDefaultSettings, ...nextSettings }
    Object.assign(settings, snapshot)
    writeSettings(snapshot)
    return snapshot
  }

  const resetSettings = () => {
    Object.assign(settings, cloneDefaults())
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem(adminSettingsStorageKey)
    }
  }

  const swaggerUrl = computed(() => String(settings.swaggerUrl || '').trim())
  const resolvedSwaggerUrl = computed(() => normalizeAdminUrl(swaggerUrl.value))

  return {
    settings,
    swaggerUrl,
    resolvedSwaggerUrl,
    loadSettings,
    saveSettings,
    resetSettings
  }
}
