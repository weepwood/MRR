<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import DefaultTheme from 'vitepress/theme'

const motionQuery = '(pointer: fine) and (prefers-reduced-motion: no-preference)'

function updateHeroBackground(event: PointerEvent) {
  const hero = document.querySelector<HTMLElement>('.VPHero')
  if (!hero || !window.matchMedia(motionQuery).matches) {
    return
  }

  const bounds = hero.getBoundingClientRect()
  const isInsideHero = event.clientX >= bounds.left && event.clientX <= bounds.right
    && event.clientY >= bounds.top && event.clientY <= bounds.bottom
  const offsetX = isInsideHero ? ((event.clientX - bounds.left) / bounds.width - 0.5) * 24 : 0
  const offsetY = isInsideHero ? ((event.clientY - bounds.top) / bounds.height - 0.5) * 24 : 0

  hero.style.setProperty('--mrr-hero-bg-x', `${offsetX.toFixed(2)}px`)
  hero.style.setProperty('--mrr-hero-bg-y', `${offsetY.toFixed(2)}px`)
}

onMounted(() => window.addEventListener('pointermove', updateHeroBackground))
onBeforeUnmount(() => window.removeEventListener('pointermove', updateHeroBackground))
</script>

<template>
  <DefaultTheme.Layout />
</template>
