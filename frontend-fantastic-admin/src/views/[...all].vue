<route lang="yaml">
name: notFound
meta:
  title: 找不到页面
  constant: true
  layout: false
</route>

<script setup lang="ts">
import { useIntervalFn } from '@vueuse/core'

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
  <div class="relative h-screen w-screen overflow-hidden bg-background">
    <!-- 背景装饰 -->
    <div class="absolute inset-0 opacity-5">
      <div class="absolute left-1/4 top-1/4 h-64 w-64 animate-pulse rounded-full bg-primary blur-3xl" />
      <div class="absolute bottom-1/4 right-1/4 h-96 w-96 animate-pulse rounded-full bg-primary blur-3xl" style="animation-delay: 1s;" />
    </div>

    <!-- 主内容 -->
    <div
      class="absolute left-1/2 top-1/2 flex flex-col items-center justify-between gap-8 lg:flex-row -translate-x-1/2 -translate-y-1/2 lg:gap-16"
    >
      <!-- 左侧图标区域 -->
      <div class="relative select-none">
        <FaIcon name="404" class="text-[200px] text-primary/20 lg:text-[350px]" />
        <div class="absolute inset-0 flex items-center justify-center" />
      </div>

      <!-- 右侧文案区域 -->
      <div class="flex flex-col items-center gap-5 text-center lg:items-start lg:text-left">
        <h1 class="m-0 text-5xl font-bold tracking-tight lg:text-7xl">
          页面走丢了
        </h1>
        <p class="m-0 max-w-md text-lg text-muted-foreground leading-relaxed">
          抱歉，你访问的页面不存在或已被移除
        </p>
        <div class="mt-2 flex gap-3">
          <FaButton @click="goBack">
            返回首页 ({{ countdown }}s)
          </FaButton>
          <FaButton variant="outline" @click="router.back()">
            返回上页
          </FaButton>
        </div>
      </div>
    </div>
  </div>
</template>
