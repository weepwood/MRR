<script setup lang="ts">
import zhCN from 'element-plus/es/locale/lang/zh-cn'

const settingsStore = useSettingsStore()
let darkThemePromise: Promise<unknown> | undefined

function ensureDarkTheme() {
  darkThemePromise ??= import('element-plus/theme-chalk/dark/css-vars.css')
  return darkThemePromise
}

// 主题色的唯一写入点是 settings store 的 applyThemeColor()：它在初始化与变更时
// 同时维护 --color-primary、--primary、--ring 以及 --el-color-primary 与其
// light-N / dark-2 色阶，并在 <html> 上生效。
// 这里只桥接 Element Plus 的表面与文字基色，不再重复写入 --el-color-primary 及色阶：
// 原先写在 <body> 上的内联值会覆盖 <html> 上的设置值，且两处使用不同的混色算法
// （hex 混合 vs color-mix），导致同一主题在不同浏览器下出现不同色阶。
const isSupportColorMix = CSS.supports('color', 'color-mix(in srgb, #fff, #000)')
if (isSupportColorMix) {
  document.body.style.setProperty('--el-bg-color', 'hsl(var(--background))')
  document.body.style.setProperty('--el-color-white', 'hsl(var(--primary-foreground))')
  document.body.style.setProperty('--el-color-black', 'hsl(var(--primary-foreground))')
}

watch(() => settingsStore.currentColorScheme, (val) => {
  if (val === 'dark') {
    void ensureDarkTheme()
  }
}, {
  immediate: true,
})
</script>

<template>
  <ElConfigProvider :locale="zhCN as any" :button="{ autoInsertSpace: true }">
    <slot />
  </ElConfigProvider>
</template>
