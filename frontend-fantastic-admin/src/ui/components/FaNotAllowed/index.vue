<script setup lang="ts">
import MrrErrorPage from '@/components/MrrErrorPage/index.vue'

defineOptions({
  name: 'FaNotAllowed',
})

const route = useRoute()
const router = useRouter()

const settingsStore = useSettingsStore()
const tabbarStore = useTabbarStore()

const data = ref({
  inter: Number.NaN,
  countdown: 5,
})

onUnmounted(() => {
  data.value.inter && window.clearInterval(data.value.inter)
})

onMounted(() => {
  if (settingsStore.settings.tabbar.enable) {
    tabbarStore.remove(route.meta.activeMenu || route.fullPath)
  }
  data.value.inter = window.setInterval(() => {
    data.value.countdown--
    if (data.value.countdown === 0) {
      data.value.inter && window.clearInterval(data.value.inter)
      goBack()
    }
  }, 1000)
})

function goBack() {
  router.push(settingsStore.settings.home.fullPath)
}
</script>

<template>
  <MrrErrorPage code="403" heading="暂无访问权限" description="当前账号无权访问此页面。如需使用此功能，请联系系统管理员确认权限。">
    <el-button class="mrr-button-flow" type="primary" :auto-insert-space="false" @click="goBack">
      返回首页 ({{ data.countdown }}s)
    </el-button>
  </MrrErrorPage>
</template>
