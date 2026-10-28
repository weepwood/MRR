<script setup lang="ts">
import { isMotionEnabled } from '@/utils/motion-preference'

defineOptions({
  name: 'ColorScheme',
})

const settingsStore = useSettingsStore()

function toggleColorScheme(event: MouseEvent) {
  if (!document.startViewTransition || !isMotionEnabled()) {
    settingsStore.currentColorScheme && settingsStore.setColorScheme(settingsStore.currentColorScheme === 'dark' ? 'light' : 'dark')
    return
  }
  const target = event.target as HTMLElement
  const { left, top, width, height } = target.getBoundingClientRect()
  const x = left + width / 2
  const y = top + height / 2
  const endRadius = Math.hypot(
    Math.max(x, innerWidth - x),
    Math.max(y, innerHeight - y),
  )
  const ratioX = (100 * x) / innerWidth
  const ratioY = (100 * y) / innerHeight
  const referR = Math.hypot(innerWidth, innerHeight) / Math.SQRT2
  const ratioR = (100 * endRadius) / referR
  const transition = document.startViewTransition(async () => {
    settingsStore.currentColorScheme && settingsStore.setColorScheme(settingsStore.currentColorScheme === 'dark' ? 'light' : 'dark')
    await nextTick()
  })
  transition.ready.then(() => {
    const clipPath = [
      `circle(0% at ${ratioX}% ${ratioY}%)`,
      `circle(${ratioR}% at ${ratioX}% ${ratioY}%)`,
    ]
    document.documentElement.animate(
      {
        clipPath: settingsStore.currentColorScheme === 'light' ? clipPath : clipPath.toReversed(),
      },
      {
        duration: 500,
        easing: 'ease-in-out',
        fill: 'both',
        pseudoElement: settingsStore.currentColorScheme === 'light' ? '::view-transition-new(root)' : '::view-transition-old(root)',
      },
    )
  })
}
</script>

<template>
  <FaButton variant="ghost" size="icon" @click="toggleColorScheme">
    <FaIcon
      :name="{
        light: 'i-lucide:sun',
        dark: 'i-lucide:moon',
      }[settingsStore.currentColorScheme!]" class="size-4"
    />
  </FaButton>
</template>
