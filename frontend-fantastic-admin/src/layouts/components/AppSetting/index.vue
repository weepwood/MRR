<script setup lang="ts">
import AppConfigPanel from '@/views/settings/components/AppConfigPanel.vue'

defineOptions({
  name: 'AppSetting',
})

const isShow = ref(false)
</script>

<template>
  <el-button
    class="mrr-migrated-toolbar mrr-toolbar-icon"
    text

    title="应用外观设置"
    aria-label="打开应用外观设置"
    @click="isShow = true"
  >
    <FaIcon name="i-lucide:settings-2" class="size-4" />
  </el-button>

  <MrrDrawer
    v-model="isShow"
    title="应用外观设置"
    description="修改后自动保存；具备系统管理权限时同步到全站"
    :destroy-on-close="false"
    :footer="false"
    overlay-class="mrr-vscode-overlay"
    content-class="mrr-vscode-glass mrr-settings-drawer"
  >
    <AppConfigPanel drawer />
  </MrrDrawer>
</template>
