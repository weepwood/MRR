<script setup lang="ts">
import { useElementSize } from '@vueuse/core'
import Tabbar from './Tabbar/index.vue'
import Toolbar from './Toolbar/index.vue'

defineOptions({
  name: 'Topbar',
})

const settingsStore = useSettingsStore()

const enableToolbar = computed(() => {
  return settingsStore.settings.toolbar.enable && Object.keys(settingsStore.settings.toolbar).some((key) => {
    if (settingsStore.settings.app.routeBaseOn === 'filesystem' && key === 'breadcrumb') {
      return false
    }
    return settingsStore.settings.toolbar[key as keyof typeof settingsStore.settings.toolbar]
  })
})

const scrollTop = ref(0)
const scrollOnHide = ref(false)

const topbarRef = useTemplateRef('topbarRef')
const { height: topbarHeight } = useElementSize(topbarRef)

onMounted(() => {
  window.addEventListener('scroll', onScroll)
})
onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})
function onScroll() {
  scrollTop.value = (document.documentElement || document.body).scrollTop
}
watch(scrollTop, (val, oldVal) => {
  scrollOnHide.value = settingsStore.settings.topbar.mode === 'sticky' && val > oldVal && val > topbarHeight.value
})
</script>

<template>
  <FaSmartFixedBlock position="top" :class="{ 'absolute!': settingsStore.settings.topbar.mode === 'static' }">
    <div
      ref="topbarRef" class="topbar-container" :class="{
        [`topbar-${settingsStore.settings.topbar.mode}`]: true,
        mask: scrollTop,
        hide: scrollOnHide,
      }"
    >
      <Tabbar v-if="settingsStore.settings.tabbar.enable" />
      <Toolbar v-if="enableToolbar" />
    </div>
  </FaSmartFixedBlock>
</template>

<style scoped>
.topbar-container {
  display: flex;
  flex-direction: column;
  width: calc(100% - var(--scrollbar-width, 0px));
  overflow: hidden;
  background: var(--mrr-topbar-bg);
  border-bottom: 1px solid var(--mrr-shell-divider);
  backdrop-filter: blur(18px) saturate(140%);
  transition: transform 220ms ease, box-shadow 220ms ease, background-color 220ms ease;

  &.mask {
    box-shadow: 0 8px 24px rgb(15 23 42 / 6%);
  }

  &.topbar-fixed,
  &.topbar-sticky {
    position: fixed;
  }

  &.topbar-sticky.hide {
    transform: translateY(-100%);
  }
}

:global(.dark) .topbar-container.mask {
  box-shadow: 0 10px 30px rgb(0 0 0 / 24%);
}

@media (prefers-reduced-motion: reduce) {
  .topbar-container {
    transition: none;
  }
}
</style>
