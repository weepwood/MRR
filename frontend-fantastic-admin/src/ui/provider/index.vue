<script setup lang="ts">
import zhCN from 'element-plus/es/locale/lang/zh-cn'

const settingsStore = useSettingsStore()
const rootStyle = document.documentElement.style
const supportsColorMix = CSS.supports('color', 'color-mix(in srgb, #fff, #000)')

const elementTokenMap: Record<string, string> = {
  '--el-font-family': 'var(--font-sans)',
  '--el-font-size-base': 'var(--font-size-base)',
  '--el-bg-color': 'var(--surface)',
  '--el-bg-color-page': 'var(--surface-page)',
  '--el-bg-color-overlay': 'var(--surface-raised)',
  '--el-text-color-primary': 'var(--text-primary)',
  '--el-text-color-regular': 'var(--text-secondary)',
  '--el-text-color-secondary': 'var(--text-tertiary)',
  '--el-text-color-placeholder': 'var(--text-hint)',
  '--el-text-color-disabled': 'var(--text-hint)',
  '--el-border-color': 'var(--divider)',
  '--el-border-color-light': 'var(--border-subtle)',
  '--el-border-color-lighter': 'hsl(var(--border) / 55%)',
  '--el-border-color-extra-light': 'hsl(var(--border) / 38%)',
  '--el-border-color-dark': 'var(--border-strong)',
  '--el-fill-color': 'var(--surface-alt)',
  '--el-fill-color-light': 'var(--surface-muted)',
  '--el-fill-color-lighter': 'hsl(var(--muted) / 72%)',
  '--el-fill-color-extra-light': 'hsl(var(--muted) / 48%)',
  '--el-fill-color-dark': 'var(--surface-accent)',
  '--el-fill-color-blank': 'var(--surface)',
  '--el-color-primary': 'hsl(var(--primary))',
  '--el-color-success': 'hsl(var(--success))',
  '--el-color-warning': 'hsl(var(--warning))',
  '--el-color-danger': 'hsl(var(--destructive))',
  '--el-color-error': 'hsl(var(--destructive))',
  '--el-color-info': 'hsl(var(--info))',
  '--el-color-white': '#fff',
  '--el-color-black': 'hsl(var(--foreground))',
  '--el-border-radius-base': 'var(--radius-md)',
  '--el-border-radius-small': 'var(--radius-sm)',
  '--el-border-radius-round': 'var(--radius-pill)',
  '--el-border-radius-circle': '50%',
  '--el-box-shadow': 'var(--shadow-md)',
  '--el-box-shadow-light': 'var(--shadow-sm)',
  '--el-box-shadow-lighter': 'var(--shadow-xs)',
  '--el-box-shadow-dark': 'var(--shadow-lg)',
  '--el-disabled-bg-color': 'var(--surface-muted)',
  '--el-disabled-text-color': 'var(--text-hint)',
  '--el-disabled-border-color': 'var(--border-subtle)',
  '--el-mask-color': 'hsl(var(--foreground) / 45%)',
  '--el-mask-color-extra-light': 'hsl(var(--foreground) / 18%)',
}

for (const [token, value] of Object.entries(elementTokenMap)) {
  rootStyle.setProperty(token, value)
}

const semanticColors = {
  primary: 'var(--primary)',
  success: 'var(--success)',
  warning: 'var(--warning)',
  danger: 'var(--destructive)',
  error: 'var(--destructive)',
  info: 'var(--info)',
}

function syncElementColorScale(colorScheme: 'light' | 'dark') {
  if (!supportsColorMix) {
    return
  }

  const mixTarget = colorScheme === 'light' ? '#fff' : '#000'
  const darkTarget = colorScheme === 'light' ? '#000' : '#fff'

  for (const [name, source] of Object.entries(semanticColors)) {
    for (let index = 1; index < 10; index += 1) {
      rootStyle.setProperty(`--el-color-${name}-light-${index}`, `color-mix(in hsl, hsl(${source}), ${mixTarget} ${index * 10}%)`)
      rootStyle.setProperty(`--el-color-${name}-dark-2`, `color-mix(in hsl, hsl(${source}), ${darkTarget} 20%)`)
    }
  }
}

watch(() => settingsStore.currentColorScheme, syncElementColorScale, { immediate: true })
</script>

<template>
  <ElConfigProvider :locale="zhCN as any" :button="{ autoInsertSpace: true }">
    <slot />
  </ElConfigProvider>
</template>
