<script setup lang="ts">
import zhCN from 'element-plus/es/locale/lang/zh-cn'

const settingsStore = useSettingsStore()
let darkThemePromise: Promise<unknown> | undefined

function ensureDarkTheme() {
  darkThemePromise ??= import('element-plus/theme-chalk/dark/css-vars.css')
  return darkThemePromise
}

// 跟随框架主题。暗色变量的按需加载不依赖 color-mix 支持，保证旧浏览器也能切换主题。
const isSupportColorMix = CSS.supports('color', 'color-mix(in srgb, #fff, #000)')
if (isSupportColorMix) {
  document.body.style.setProperty('--el-bg-color', 'hsl(var(--background))')
  document.body.style.setProperty('--el-color-primary', 'hsl(var(--primary))')
  document.body.style.setProperty('--el-color-white', 'hsl(var(--primary-foreground))')
  document.body.style.setProperty('--el-color-black', 'hsl(var(--primary-foreground))')
}

watch(() => settingsStore.currentColorScheme, (val) => {
  const isDark = val === 'dark'
  if (isDark) {
    void ensureDarkTheme()
  }
  if (!isSupportColorMix) {
    return
  }

  for (let index = 1; index < 10; index++) {
    document.body.style.setProperty(
      `--el-color-primary-light-${index}`,
      `color-mix(in hsl, hsl(var(--primary)), ${isDark ? '#000' : '#fff'} ${index * 10}%)`,
    )
    document.body.style.setProperty(
      `--el-color-primary-dark-${index}`,
      `color-mix(in hsl, hsl(var(--primary)), ${isDark ? '#fff' : '#000'} ${index * 10}%)`,
    )
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
