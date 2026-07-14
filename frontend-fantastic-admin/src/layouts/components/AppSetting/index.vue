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
    class="size-9"
    title="应用外观设置"
    aria-label="打开应用外观设置"
    @click="isShow = true"
  >
    <FaIcon name="i-ri:settings-3-line" class="size-4" />
  </FaButton>

  <FaDrawer
    v-model="isShow"
    title="应用外观设置"
    description="修改后自动保存到当前浏览器"
    :destroy-on-close="false"
    :footer="false"
    content-class="sm:w-[520px] sm:max-w-[520px]"
  >
    <AppConfigPanel drawer />
  </FaDrawer>
</template>
