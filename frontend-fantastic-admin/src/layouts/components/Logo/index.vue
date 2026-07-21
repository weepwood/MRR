<script setup lang="ts">
import imgLogo from '@/assets/images/mrr-logo.svg'
import { isRuntimeDeveloperModeEnabled } from '@/api/modules/developer-mode'
import {
  readLocalSystemSettings,
  SYSTEM_SETTINGS_UPDATED_EVENT,
  type EffectiveSystemSettings,
} from '@/utils/system-settings'

defineOptions({
  name: 'Logo',
})

const props = withDefaults(
  defineProps<{
    showLogo?: boolean
    showTitle?: boolean
  }>(),
  {
    showLogo: true,
    showTitle: true,
  },
)

const settingsStore = useSettingsStore()

const title = ref(import.meta.env.VITE_APP_TITLE)
const logo = ref(imgLogo)
const developerModeEnabled = ref(readLocalSystemSettings()?.developerModeEnabled ?? false)
let refreshTimer: ReturnType<typeof setInterval> | undefined

const to = computed(() => settingsStore.settings.home.enable ? settingsStore.settings.home.fullPath : '')

async function refreshDeveloperModeStatus(force = false) {
  developerModeEnabled.value = await isRuntimeDeveloperModeEnabled(force)
}

function handleSettingsUpdated(event: Event) {
  const settings = (event as CustomEvent<EffectiveSystemSettings>).detail
  if (settings) {
    developerModeEnabled.value = settings.developerModeEnabled
  }
  void refreshDeveloperModeStatus(true)
}

function handleVisibilityChange() {
  if (!document.hidden) {
    void refreshDeveloperModeStatus(true)
  }
}

onMounted(() => {
  if (!props.showTitle) return

  void refreshDeveloperModeStatus(true)
  window.addEventListener(SYSTEM_SETTINGS_UPDATED_EVENT, handleSettingsUpdated)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  refreshTimer = setInterval(() => void refreshDeveloperModeStatus(true), 30_000)
})

onBeforeUnmount(() => {
  window.removeEventListener(SYSTEM_SETTINGS_UPDATED_EVENT, handleSettingsUpdated)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
</script>

<template>
  <RouterLink :to class="h-[var(--g-sidebar-logo-height)] w-inherit flex-center gap-2 px-3 text-inherit no-underline" :class="{ 'cursor-default': !settingsStore.settings.home.enable }" :title="title">
    <img v-if="showLogo" :src="logo" :alt="`${title} logo`" class="logo h-[30px] w-[30px] object-contain">
    <span v-if="showTitle" class="logo-title">
      <strong class="title-text">{{ title }}</strong>
      <small
        v-if="developerModeEnabled"
        class="developer-mode-badge"
        role="status"
        aria-live="polite"
        data-testid="developer-mode-badge"
        title="开发者模式已启用"
      >开发者模式</small>
    </span>
  </RouterLink>
</template>

<style scoped>
.logo-title {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.title-text {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.developer-mode-badge {
  flex: none;
  padding: 1px 6px;
  font-size: 10px !important;
  font-weight: 700;
  line-height: 1.6;
  color: var(--el-color-danger);
  letter-spacing: 0;
  white-space: nowrap;
  background: color-mix(in srgb, var(--el-color-danger) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--el-color-danger) 35%, var(--mrr-navigation-border));
  border-radius: 999px;
}
</style>
