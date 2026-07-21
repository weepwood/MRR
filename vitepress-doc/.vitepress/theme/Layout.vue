<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import DefaultTheme from 'vitepress/theme'

const motionQuery = '(pointer: fine) and (prefers-reduced-motion: no-preference)'
const screenshotPreview = ref<{ src: string, alt: string } | null>(null)

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

function openScreenshotPreview(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof HTMLImageElement) || !target.matches(".vp-doc img[src*='/screenshots/v1/']")) {
    return
  }

  screenshotPreview.value = { src: target.currentSrc || target.src, alt: target.alt }
}

function closeScreenshotPreview() {
  screenshotPreview.value = null
}

function handlePreviewKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    closeScreenshotPreview()
  }
}

onMounted(() => {
  window.addEventListener('pointermove', updateHeroBackground)
  document.addEventListener('click', openScreenshotPreview)
  window.addEventListener('keydown', handlePreviewKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', updateHeroBackground)
  document.removeEventListener('click', openScreenshotPreview)
  window.removeEventListener('keydown', handlePreviewKeydown)
})
</script>

<template>
  <DefaultTheme.Layout />
  <Teleport to="body">
    <div
      v-if="screenshotPreview"
      class="screenshot-preview"
      role="dialog"
      aria-modal="true"
      :aria-label="screenshotPreview.alt || '界面截图预览'"
      @click.self="closeScreenshotPreview"
    >
      <button type="button" class="screenshot-preview-close" aria-label="关闭预览" @click="closeScreenshotPreview">
        <span aria-hidden="true">×</span>
      </button>
      <img :src="screenshotPreview.src" :alt="screenshotPreview.alt">
    </div>
  </Teleport>
</template>
