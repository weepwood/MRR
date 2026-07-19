<script setup lang="ts">
defineOptions({ name: 'AppConfigPanel' })

const props = withDefaults(defineProps<{
  drawer?: boolean
}>(), {
  drawer: false,
})

const legacyPanelRef = ref<{ autoSaveLabel: string }>()
const autoSaveLabel = computed(() => legacyPanelRef.value?.autoSaveLabel ?? '修改后自动保存到当前浏览器')

defineExpose({ autoSaveLabel })
</script>

<template>
  <div class="app-config-panel-wrapper" :class="{ 'app-config-panel-wrapper--drawer': props.drawer }">
    <AppConfigPanelLegacy ref="legacyPanelRef" :drawer="props.drawer" />
    <MenuVisibilitySettings />
  </div>
</template>

<style scoped>
.app-config-panel-wrapper {
  max-width: 1080px;
}

.app-config-panel-wrapper--drawer {
  max-width: none;
}
</style>
