<script setup lang="ts">
import eventBus from '@/utils/eventBus'
import AppConfigPanel from '@/views/settings/components/AppConfigPanel.vue'

defineOptions({
  name: 'AppSetting',
})

const route = useRoute()

const settingsStore = useSettingsStore()
const menuStore = useMenuStore()

const isShow = ref(false)

watch(() => settingsStore.settings.menu.mode, (value) => {
  if (value === 'single') {
    menuStore.setActived(0)
  }
  else {
    menuStore.setActived(route.fullPath)
  }
})

onMounted(() => {
  eventBus.on('global-app-setting-toggle', () => {
    isShow.value = !isShow.value
  })
})
</script>

<template>
  <FaDrawer v-model="isShow" title="应用配置" description="在生产环境中应关闭该模块" :destroy-on-close="false" content-class="sm:min-w-md">
    <AppConfigPanel />
  </FaDrawer>
</template>
