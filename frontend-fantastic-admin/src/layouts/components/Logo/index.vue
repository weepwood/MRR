<script setup lang="ts">
import type { EffectiveSystemSettings } from '@/utils/system-settings'
import { RouterLink } from 'vue-router'
import { isRuntimeDeveloperModeEnabled } from '@/api/modules/developer-mode'
import imgLogo from '@/assets/images/mrr-logo.svg'
import {
  readLocalSystemSettings,
  SYSTEM_SETTINGS_UPDATED_EVENT,
} from '@/utils/system-settings'

defineOptions({
  name: 'Logo',
})

const props = withDefaults(
  defineProps<{
    showLogo?: boolean
    showTitle?: boolean
    collapsible?: boolean
  }>(),
  {
    showLogo: true,
    showTitle: true,
    collapsible: false,
  },
)

const settingsStore = useSettingsStore()

const title = ref(import.meta.env.VITE_APP_TITLE)
const logo = ref(imgLogo)
const developerModeEnabled = ref(readLocalSystemSettings()?.developerModeEnabled ?? false)
let refreshTimer: ReturnType<typeof setInterval> | undefined

const to = computed(() => settingsStore.settings.home.enable ? settingsStore.settings.home.fullPath : '')
const canToggleSidebar = computed(() => props.collapsible && props.showLogo && settingsStore.mode === 'pc')
const sidebarCollapsed = computed(() => canToggleSidebar.value && settingsStore.settings.menu.subMenuCollapse)
const toggleLabel = computed(() => sidebarCollapsed.value ? '展开侧边栏' : '收起侧边栏')

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
  if (!props.showTitle) {
    return
  }

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
  <component
    :is="canToggleSidebar ? 'div' : RouterLink" :to="canToggleSidebar ? undefined : to" :title="canToggleSidebar ? undefined : title"
    class="h-[var(--g-sidebar-logo-height)] w-inherit flex-center gap-2 px-3 text-inherit no-underline" :class="{
      'logo-container--collapsible': canToggleSidebar,
      'cursor-default': !canToggleSidebar && !settingsStore.settings.home.enable,
    }"
  >
    <FaButton
      v-if="canToggleSidebar" type="button" variant="ghost" size="icon"
      class="logo-toggle relative" :class="{ 'is-collapsed': sidebarCollapsed }"
      :aria-label="toggleLabel" :aria-expanded="!sidebarCollapsed" :title="toggleLabel"
      @click="settingsStore.toggleSidebarCollapse()"
    >
      <img :src="logo" alt="" class="logo logo-mark size-[17px] shrink-0 object-contain">
      <FaIcon :name="sidebarCollapsed ? 'i-lucide:panel-left-open' : 'i-lucide:panel-left-close'" class="logo-toggle-icon absolute size-[17px]" />
    </FaButton>
    <component
      :is="canToggleSidebar ? RouterLink : 'span'" v-if="!sidebarCollapsed" :to="canToggleSidebar ? to : undefined"
      class="min-h-[var(--mrr-control-height)] min-w-0 flex-center gap-2 text-inherit no-underline"
      :class="{ 'cursor-default': !settingsStore.settings.home.enable }" :title="title"
    >
      <img v-if="showLogo && !canToggleSidebar" :src="logo" :alt="`${title} logo`" class="logo size-[17px] shrink-0 object-contain">
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
    </component>
  </component>
</template>

<style scoped>
.logo-container--collapsible {
  justify-content: flex-start;
  padding-inline-start: calc((var(--g-sub-sidebar-collapse-width) - var(--mrr-control-height)) / 2);
}

.logo-mark,
.logo-toggle-icon {
  transition: opacity var(--mrr-motion-fast) ease;
}

.logo-toggle-icon {
  color: var(--text-secondary);
  pointer-events: none;
  opacity: 0;
}

.logo-toggle:hover .logo-mark,
.logo-toggle:focus-visible .logo-mark,
.logo-toggle.is-collapsed .logo-mark {
  opacity: 0;
}

.logo-toggle:hover .logo-toggle-icon,
.logo-toggle:focus-visible .logo-toggle-icon,
.logo-toggle.is-collapsed .logo-toggle-icon {
  opacity: 1;
}

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
