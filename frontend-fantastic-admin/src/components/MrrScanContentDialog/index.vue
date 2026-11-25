<script setup lang="ts">
import { ElDialog } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { getReviewImage } from '@/api/modules/classification'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'

const props = withDefaults(defineProps<{ scanId: number | null, inline?: boolean }>(), { inline: false })
const emit = defineEmits<{ close: [] }>()
const source = ref('')
const loading = ref(false)
const failed = ref(false)
const zoom = ref(100)
const rotation = ref(0)
const viewport = ref<HTMLElement>()
const natural = ref({ width: 0, height: 0 })
const viewportSize = ref({ width: 0, height: 0 })
let observer: ResizeObserver | undefined
const geometry = computed(() => {
  const { width, height } = natural.value
  const { width: availableWidth, height: availableHeight } = viewportSize.value
  if (!width || !height || availableWidth <= 0 || availableHeight <= 0) { return null }
  const sideways = rotation.value % 180 !== 0
  const fit = Math.min((availableWidth - 24) / (sideways ? height : width), (availableHeight - 24) / (sideways ? width : height), 1)
  const factor = Math.max(0.001, fit) * zoom.value / 100
  const w = width * factor
  const h = height * factor
  return { width: w, height: h, boxWidth: sideways ? h : w, boxHeight: sideways ? w : h }
})
function measure() {
  if (viewport.value) { viewportSize.value = { width: viewport.value.clientWidth, height: viewport.value.clientHeight } }
}
watch(viewport, (element) => {
  observer?.disconnect()
  observer = undefined
  if (!element) { return }
  measure()
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(measure)
    observer.observe(element)
  }
}, { flush: 'post' })
async function centerImage() {
  await nextTick()
  const el = viewport.value
  if (el) {
    el.scrollLeft = (el.scrollWidth - el.clientWidth) / 2
    el.scrollTop = (el.scrollHeight - el.clientHeight) / 2
  }
}
function decoded(event: Event) {
  const image = event.target as HTMLImageElement
  if (image.getAttribute('src') !== source.value) { return }
  natural.value = { width: image.naturalWidth, height: image.naturalHeight }
  measure()
  void centerImage()
}
function changeZoom(delta: number) {
  zoom.value = Math.min(300, Math.max(50, zoom.value + delta))
  void centerImage()
}
function rotate() {
  rotation.value = (rotation.value + 90) % 360
  void centerImage()
}
function resetView() {
  zoom.value = 100
  rotation.value = 0
  void centerImage()
}
let generation = 0
let controller: AbortController | undefined

function clear() {
  generation++
  controller?.abort()
  controller = undefined
  if (source.value) {
    URL.revokeObjectURL(source.value)
  }
  source.value = ''
  natural.value = { width: 0, height: 0 }
  zoom.value = 100
  rotation.value = 0
  loading.value = false
  failed.value = false
}

async function load() {
  clear()
  const id = props.scanId
  if (!id || id <= 0) {
    return
  }
  const current = generation
  loading.value = true
  controller = new AbortController()
  try {
    const blob = await getReviewImage(id, controller.signal)
    if (generation !== current) {
      return
    }
    if (!(blob instanceof Blob) || !blob.size || !blob.type.startsWith('image/')) {
      throw new Error('Invalid image')
    }
    source.value = URL.createObjectURL(blob)
  }
  catch {
    if (generation === current) {
      failed.value = true
    }
  }
  finally {
    if (generation === current) {
      loading.value = false
      controller = undefined
    }
  }
}

function close() {
  clear()
  emit('close')
}

watch(() => props.scanId, () => { void load() }, { immediate: true })
watch(() => useUserStore().token, close, { flush: 'sync' })
onBeforeUnmount(() => { observer?.disconnect(); clear() })
</script>

<template>
  <component
    :is="inline ? 'section' : ElDialog"
    v-bind="inline ? { 'aria-label': '原图核对', 'class': 'scan-inline' } : { modelValue: scanId !== null, title: `影像核对 #${scanId ?? ''}`, width: 'min(880px, 94vw)', appendToBody: true, destroyOnClose: true }"
    @update:model-value="!$event && close()"
  >
    <AppLoading v-if="loading" />
    <AppError v-else-if="failed" message="影像不可读或已变化，请重试" @retry="load()" />
    <div v-else-if="source" class="scan-viewer">
      <div class="scan-viewer-toolbar" role="group" aria-label="原图显示控制">
        <el-button size="small" :disabled="!geometry || zoom <= 50" @click="changeZoom(-25)">
          缩小
        </el-button>
        <span aria-live="polite" class="scan-viewer-percent">{{ zoom }}%</span>
        <el-button size="small" :disabled="!geometry || zoom >= 300" @click="changeZoom(25)">
          放大
        </el-button>
        <el-button size="small" :disabled="!geometry" @click="rotate">
          旋转 90°
        </el-button>
        <el-button size="small" :disabled="!geometry" @click="resetView">
          适应窗口
        </el-button>
        <span class="scan-viewer-angle">{{ rotation }}°</span>
      </div>
      <div ref="viewport" class="scan-viewport" tabindex="0" role="region" aria-label="原图滚动查看区">
        <div class="scan-canvas" :style="geometry ? { minWidth: `${geometry.boxWidth + 24}px`, minHeight: `${geometry.boxHeight + 24}px` } : undefined">
          <div class="scan-image-box" :style="geometry ? { width: `${geometry.boxWidth}px`, height: `${geometry.boxHeight}px` } : undefined">
            <img :key="source" :src="source" alt="待核对影像" class="scan-content" :class="{ 'scan-content--positioned': geometry }" :style="geometry ? { width: `${geometry.width}px`, height: `${geometry.height}px`, transform: `translate(-50%, -50%) rotate(${rotation}deg)` } : undefined" @load="decoded" @error="failed = true">
          </div>
        </div>
      </div>
      <p class="scan-viewer-hint">
        100% 为适应窗口，放大后可滚动查看；旋转仅改变显示，不修改原文件。
      </p>
    </div>
  </component>
</template>

<style scoped>
.scan-inline {
  display: grid;
  place-items: center stretch;
  min-width: 0;
  min-height: 240px;
  background: var(--el-fill-color-light);
  border-radius: var(--mrr-radius-card);
}

.scan-viewer {
  min-width: 0;
  max-width: 100%;
}

.scan-viewer-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-1);
  align-items: center;
  padding: var(--mrr-space-2);
  background: var(--el-fill-color-blank);
  border-bottom: 1px solid var(--el-border-color-light);
}

.scan-viewer-toolbar :deep(.el-button + .el-button) {
  margin-left: 0;
}

.scan-viewer-percent,
.scan-viewer-angle,
.scan-viewer-hint {
  font-size: var(--mrr-font-size-xs);
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);
}

.scan-viewport {
  width: 100%;
  height: clamp(240px, 54vh, 640px);
  overflow: auto;
  background: var(--el-fill-color-light);
}

.scan-viewport:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: -2px;
}

.scan-canvas {
  display: grid;
  place-items: center;
  min-height: 100%;
}

.scan-image-box {
  position: relative;
  max-width: none;
}

.scan-content--positioned {
  position: absolute;
  top: 50%;
  left: 50%;
  max-width: none !important;
  max-height: none !important;
}

.scan-viewer-hint {
  padding: var(--mrr-space-2);
  margin: 0;
}

.scan-content {
  display: block;
  max-width: 100%;
  max-height: 70vh;
  margin-inline: auto;
  object-fit: contain;
}
</style>
