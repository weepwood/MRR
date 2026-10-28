<script setup lang="ts">
import AppConfigPanel from '@/views/settings/components/AppConfigPanel.vue'

defineOptions({
  name: 'AppSetting',
})

const isShow = ref(false)
</script>

<template>
  <FaButton
    variant="ghost"
    size="icon"
    title="应用外观设置"
    aria-label="打开应用外观设置"
    @click="isShow = true"
  >
    <FaIcon name="i-lucide:settings-2" class="size-4" />
  </FaButton>

  <FaDrawer
    v-model="isShow"
    title="应用外观设置"
    description="修改后自动保存；具备系统管理权限时同步到全站"
    :destroy-on-close="false"
    :footer="false"
    content-class="sm:w-[520px] sm:max-w-[520px]"
  >
    <AppConfigPanel drawer />
  </FaDrawer>
</template>
