<script setup lang="ts">
import { archiveAccessMode, canRenderArchiveRoute } from '@/views/statistics/archive/access-mode'
import Provider from './ui/provider/index.vue'

const SystemInfo = defineAsyncComponent(() => import('@/ui/components/FaSystemInfo/index.vue'))
const route = useRoute()
const settingsStore = useSettingsStore()
const { auth } = useAuth()
const systemInfoVisible = ref(false)
let resizeFrame: number | undefined

const isAuth = computed(() => {
  if (canRenderArchiveRoute(route.name, archiveAccessMode.value)) {
    return true
  }
  return route.matched.every((item) => {
    return auth(item.meta.auth ?? '')
  })
})

// 设置网页 title
watch([
  () => settingsStore.settings.app.enableDynamicTitle,
  () => settingsStore.title,
], () => {
  if (settingsStore.settings.app.enableDynamicTitle && settingsStore.title) {
    const title = typeof settingsStore.title === 'function' ? settingsStore.title() : settingsStore.title
    document.title = `${title} - ${import.meta.env.VITE_APP_TITLE}`
  }
  else {
    document.title = import.meta.env.VITE_APP_TITLE
  }
}, {
  immediate: true,
})

function syncViewportMode() {
  settingsStore.setMode(document.documentElement.clientWidth)
}

function handleResize() {
  if (resizeFrame !== undefined) {
    return
  }
  resizeFrame = window.requestAnimationFrame(() => {
    resizeFrame = undefined
    syncViewportMode()
  })
}

function handleSystemInfoShortcut(event: KeyboardEvent) {
  if (event.repeat || event.altKey || event.shiftKey) {
    return
  }
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'i') {
    event.preventDefault()
    systemInfoVisible.value = true
  }
}

onMounted(() => {
  syncViewportMode()
  window.addEventListener('resize', handleResize, { passive: true })
  window.addEventListener('keydown', handleSystemInfoShortcut)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('keydown', handleSystemInfoShortcut)
  if (resizeFrame !== undefined) {
    window.cancelAnimationFrame(resizeFrame)
  }
})
</script>

<template>
  <Provider>
    <RouterView v-slot="{ Component }">
      <component :is="Component" v-if="isAuth" />
      <FaNotAllowed v-else />
    </RouterView>
    <FaBackToTop />
    <FaToast />
    <FaNotification />
    <SystemInfo v-if="systemInfoVisible" v-model="systemInfoVisible" />
  </Provider>
</template>
