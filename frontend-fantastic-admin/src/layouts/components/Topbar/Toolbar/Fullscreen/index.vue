<script setup lang="ts">
import { useFullscreen } from '@vueuse/core'

defineOptions({
  name: 'Fullscreen',
})

const settingsStore = useSettingsStore()

const { isFullscreen, toggle } = useFullscreen()
</script>

<template>
  <el-button v-if="settingsStore.mode === 'pc'" class="mrr-migrated-toolbar mrr-toolbar-icon" text :aria-label="isFullscreen ? '退出全屏' : '进入全屏'" :title="isFullscreen ? '退出全屏' : '进入全屏'" @click="toggle">
    <FaIcon :name="isFullscreen ? 'i-lucide:minimize' : 'i-lucide:maximize'" class="size-4" />
  </el-button>
</template>
