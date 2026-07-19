<script setup lang="ts">
import { isRuntimeDeveloperModeEnabled } from '@/api/modules/developer-mode'
import {
  readLocalSystemSettings,
  SYSTEM_SETTINGS_UPDATED_EVENT,
  type EffectiveSystemSettings,
} from '@/utils/system-settings'

defineOptions({ name: 'DeveloperModeBanner' })

const router = useRouter()
const enabled = ref(readLocalSystemSettings()?.developerModeEnabled ?? false)
let refreshTimer: ReturnType<typeof setInterval> | undefined

async function refreshStatus(force = false) {
  enabled.value = await isRuntimeDeveloperModeEnabled(force)
}

function handleSettingsUpdated(event: Event) {
  const settings = (event as CustomEvent<EffectiveSystemSettings>).detail
  if (settings) {
    enabled.value = settings.developerModeEnabled
  }
  void refreshStatus(true)
}

function handleVisibilityChange() {
  if (!document.hidden) {
    void refreshStatus(true)
  }
}

function openDeveloperSettings() {
  void router.push({
    path: '/settings',
    query: { section: 'developer' },
  })
}

onMounted(() => {
  void refreshStatus(true)
  window.addEventListener(SYSTEM_SETTINGS_UPDATED_EVENT, handleSettingsUpdated)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  refreshTimer = setInterval(() => void refreshStatus(true), 30_000)
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
  <Transition name="developer-mode-banner">
    <section
      v-if="enabled"
      class="developer-mode-banner"
      role="alert"
      aria-live="assertive"
      data-testid="developer-mode-banner"
    >
      <span class="banner-icon" aria-hidden="true">
        <FaIcon name="i-ri:alarm-warning-fill" />
      </span>
      <div class="banner-copy">
        <strong>开发者模式已启用</strong>
        <span>无有效 JWT 的受保护接口可能以虚拟管理员身份执行，并允许宽松的跨域调试访问。请勿在正式环境长期启用。</span>
      </div>
      <el-button type="danger" plain size="small" class="banner-action" @click="openDeveloperSettings">
        前往系统设置关闭
      </el-button>
    </section>
  </Transition>
</template>

<style scoped>
.developer-mode-banner {
  position: sticky;
  top: 0;
  z-index: 20;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 12px 14px;
  margin: 0 0 16px;
  color: var(--el-color-danger-dark-2);
  background:
    linear-gradient(90deg, color-mix(in srgb, var(--el-color-danger) 12%, transparent), transparent 65%),
    color-mix(in srgb, var(--el-color-danger-light-9) 92%, var(--mrr-surface));
  border: 1px solid color-mix(in srgb, var(--el-color-danger) 38%, var(--mrr-border));
  border-radius: var(--mrr-radius-lg);
  box-shadow: 0 10px 28px color-mix(in srgb, var(--el-color-danger) 12%, transparent);
  backdrop-filter: blur(14px) saturate(135%);
}

.banner-icon {
  display: grid;
  width: 34px;
  height: 34px;
  font-size: 20px;
  color: var(--el-color-danger);
  place-items: center;
  background: color-mix(in srgb, var(--el-color-danger) 13%, transparent);
  border-radius: var(--mrr-radius-md);
}

.banner-copy {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.banner-copy strong {
  font-size: 14px;
  line-height: 1.4;
}

.banner-copy span {
  overflow: hidden;
  font-size: 12px;
  line-height: 1.5;
  color: color-mix(in srgb, currentcolor 78%, var(--mrr-text-secondary));
  text-overflow: ellipsis;
  white-space: nowrap;
}

.banner-action {
  flex: none;
}

.developer-mode-banner-enter-active,
.developer-mode-banner-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.developer-mode-banner-enter-from,
.developer-mode-banner-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@media (width <= 760px) {
  .developer-mode-banner {
    grid-template-columns: auto minmax(0, 1fr);
    padding: 12px;
  }

  .banner-copy span {
    display: -webkit-box;
    overflow: hidden;
    white-space: normal;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  .banner-action {
    grid-column: 1 / -1;
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .developer-mode-banner-enter-active,
  .developer-mode-banner-leave-active {
    transition: none;
  }
}
</style>
