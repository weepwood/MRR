<script setup lang="ts">
import { useSlots } from '@/slots'
import Breadcrumb from './Breadcrumb/index.vue'

defineOptions({
  name: 'ToolbarLeftSide',
})

const settingsStore = useSettingsStore()

async function toggleMobileSidebar(event: MouseEvent) {
  settingsStore.toggleSidebarCollapse()
  if (event.detail === 0 && !settingsStore.settings.menu.subMenuCollapse) {
    await nextTick()
    document.querySelector<HTMLElement>('#mrr-sidebar .mrr-navigation-control')?.focus()
  }
}
</script>

<template>
  <div class="toolbar-leading min-w-0 flex items-center">
    <el-button v-if="settingsStore.mode === 'mobile'" id="mrr-mobile-sidebar-toggle" text aria-label="切换侧栏导航" aria-controls="mrr-sidebar" :aria-expanded="!settingsStore.settings.menu.subMenuCollapse" class="mrr-migrated-toolbar mrr-toolbar-icon -rotate-z-180" @click="toggleMobileSidebar">
      <FaIcon name="toolbar-collapse" class="size-4" />
    </el-button>
    <component :is="useSlots('toolbar-start')" />
    <Breadcrumb v-if="settingsStore.settings.toolbar.breadcrumb" />
  </div>
</template>

<style scoped>
.toolbar-leading {
  width: 100%;
}

.toolbar-leading > :deep(.el-button) {
  flex: none;
}

.toolbar-leading > :deep(.breadcrumb) {
  min-width: 0;
}
</style>
