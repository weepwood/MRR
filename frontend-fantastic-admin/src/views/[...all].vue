<script setup lang="ts">
import { useIntervalFn } from '@vueuse/core'
import MrrErrorPage from '@/components/MrrErrorPage/index.vue'

const router = useRouter()
const settingsStore = useSettingsStore()

const countdown = ref(5)

const { pause } = useIntervalFn(() => {
  countdown.value--
  if (countdown.value <= 0) {
    pause()
    goBack()
  }
}, 1000)

onBeforeRouteLeave(() => pause())

function goBack() {
  router.push(settingsStore.settings.home.fullPath)
}
</script>

<template>
  <MrrErrorPage code="404" heading="页面不存在" description="此页面可能已被移动或移除。你可以返回首页，或返回上一页继续操作。">
    <el-button class="mrr-button-flow" type="primary" :auto-insert-space="false" @click="goBack">
      返回首页 ({{ countdown }}s)
    </el-button>
    <el-button class="mrr-button-flow" :auto-insert-space="false" @click="router.back()">
      返回上页
    </el-button>
  </MrrErrorPage>
</template>
