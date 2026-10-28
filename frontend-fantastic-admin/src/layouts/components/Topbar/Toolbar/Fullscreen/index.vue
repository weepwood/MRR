<script setup lang="ts">
import { useFullscreen } from '@vueuse/core'

defineOptions({
  name: 'Fullscreen',
})

const settingsStore = useSettingsStore()

const { isFullscreen, toggle } = useFullscreen()
</script>

<template>
  <FaButton v-if="settingsStore.mode === 'pc'" variant="ghost" size="icon" @click="toggle">
    <FaIcon :name="isFullscreen ? 'i-lucide:minimize' : 'i-lucide:maximize'" class="size-4" />
  </FaButton>
</template>
