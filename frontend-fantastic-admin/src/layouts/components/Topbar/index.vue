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
  // passive：滚动监听不调用 preventDefault，声明后浏览器无需等待回调即可滚动。
  window.addEventListener('scroll', onScroll, { passive: true })
})
onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})
function onScroll() {
  scrollTop.value = (document.documentElement || document.body).scrollTop
}
// 方向切换阈值：触控板惯性滚动会产生 1–2px 的来回抖动，没有阈值时顶栏会反复隐藏/出现。
const SCROLL_DIRECTION_THRESHOLD = 6
let lastDirectionAnchor = 0
watch(scrollTop, (val) => {
  if (settingsStore.settings.topbar.mode !== 'sticky' || val <= topbarHeight.value) {
    scrollOnHide.value = false
    lastDirectionAnchor = val
    return
  }
  const delta = val - lastDirectionAnchor
  if (Math.abs(delta) < SCROLL_DIRECTION_THRESHOLD) {
    return
  }
  scrollOnHide.value = delta > 0
  lastDirectionAnchor = val
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
  transition:
    transform var(--mrr-motion-move) var(--mrr-ease-out),
    box-shadow var(--mrr-motion-feedback) var(--mrr-ease-out),
    background-color var(--mrr-motion-feedback) var(--mrr-ease-out);

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
</style>
