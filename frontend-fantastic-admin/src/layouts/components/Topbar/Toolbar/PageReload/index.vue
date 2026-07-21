<script setup lang="ts">
import hotkeys from 'hotkeys-js'

defineOptions({
  name: 'PageReload',
})

const settingsStore = useSettingsStore()
const mainPage = useMainPage()

const isAnimating = ref(false)
let animationFrame: number | undefined
let animationTimer: number | undefined

onMounted(() => {
  hotkeys('f5', (e) => {
    if (settingsStore.settings.toolbar.pageReload) {
      e.preventDefault()
      mainPage.reload()
    }
  })
})
onUnmounted(() => {
  hotkeys.unbind('f5')
  if (animationFrame !== undefined) cancelAnimationFrame(animationFrame)
  if (animationTimer !== undefined) window.clearTimeout(animationTimer)
})

function playRefreshFeedback() {
  isAnimating.value = false
  if (animationFrame !== undefined) cancelAnimationFrame(animationFrame)
  animationFrame = requestAnimationFrame(() => {
    isAnimating.value = true
    animationFrame = undefined
  })
  if (animationTimer !== undefined) window.clearTimeout(animationTimer)
  animationTimer = window.setTimeout(() => {
    isAnimating.value = false
    animationTimer = undefined
  }, 700)
}

function handleClick() {
  playRefreshFeedback()
  mainPage.reload()
}

function handleCtrlClick() {
  location.reload()
}
</script>

<template>
  <FaTooltip side="bottom" :disabled="settingsStore.os === 'mac'">
    <template #content>
      <div class="flex-col-center gap-2">
        <p>按住 <FaKbd>Ctrl</FaKbd> 键并点击</p>
        <p>可切换为浏览器原生刷新</p>
      </div>
    </template>
    <FaButton variant="ghost" size="icon" class="size-9" @click.exact="handleClick" @click.ctrl.exact="handleCtrlClick">
      <FaIcon
        name="i-iconoir:refresh-double"
        class="mrr-icon-interactive size-4"
        :class="{ 'mrr-icon-spin-once': isAnimating }"
      />
    </FaButton>
  </FaTooltip>
</template>
