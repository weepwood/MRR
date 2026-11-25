<script setup lang="ts">
import { ElDialog } from 'element-plus'
import { Maximize, Minus, Plus, RotateCcw, RotateCw, Scan, StretchHorizontal } from 'lucide-vue-next'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { getReviewImage } from '@/api/modules/classification'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'

const props = withDefaults(defineProps<{ scanId: number | null, inline?: boolean, focused?: boolean }>(), { inline: false })
const emit = defineEmits<{ close: [] }>()
const source = ref('')
const loading = ref(false)
const failed = ref(false)
const zoom = ref(100)
const viewMode = ref<'window' | 'width' | 'actual'>('window')
const rotation = ref(0)
const viewport = ref<HTMLElement>()
const imageBox = ref<HTMLElement>()
const dragging = ref(false)
let pan: { pointerId: number, x: number, y: number, left: number, top: number } | undefined
let viewRevision = 0
let generation = 0
let controller: AbortController | undefined
const natural = ref({ width: 0, height: 0 })
const viewportSize = ref({ width: 0, height: 0 })
let observer: ResizeObserver | undefined
const geometry = computed(() => {
  const { width, height } = natural.value
  const { width: availableWidth, height: availableHeight } = viewportSize.value
  if (!width || !height || availableWidth <= 0 || availableHeight <= 0) { return null }
  const sideways = rotation.value % 180 !== 0
  const widthFit = Math.min((availableWidth - 24) / (sideways ? height : width), 1)
  const heightFit = (availableHeight - 24) / (sideways ? width : height)
  const fit = viewMode.value === 'actual' ? 1 : viewMode.value === 'width' ? widthFit : Math.min(widthFit, heightFit)
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
  const revision = ++viewRevision
  const current = generation
  await nextTick()
  const el = viewport.value
  if (el && current === generation && revision === viewRevision) {
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
async function changeZoom(delta: number, anchor?: { x: number, y: number }) {
  if (!geometry.value) { return }
  const next = Math.min(300, Math.max(50, zoom.value + delta))
  if (next === zoom.value) { return }
  const el = viewport.value
  const box = imageBox.value
  const bounds = box?.getBoundingClientRect()
  const view = el?.getBoundingClientRect()
  if (!el || !box || !bounds || !view || !bounds.width || !bounds.height) {
    zoom.value = next
    void centerImage()
    return
  }
  const point = anchor ?? { x: view.left + el.clientWidth / 2, y: view.top + el.clientHeight / 2 }
  const u = Math.min(1, Math.max(0, (point.x - bounds.left) / bounds.width))
  const v = Math.min(1, Math.max(0, (point.y - bounds.top) / bounds.height))
  const x = bounds.left + u * bounds.width
  const y = bounds.top + v * bounds.height
  const current = generation
  const revision = ++viewRevision
  zoom.value = next
  await nextTick()
  if (generation !== current || viewRevision !== revision || viewport.value !== el || imageBox.value !== box) { return }
  const updated = box.getBoundingClientRect()
  el.scrollLeft += updated.left + u * updated.width - x
  el.scrollTop += updated.top + v * updated.height - y
}
function wheelZoom(event: WheelEvent) {
  if (!geometry.value || (!event.ctrlKey && !event.metaKey) || !event.deltaY) { return }
  event.preventDefault()
  void changeZoom(event.deltaY < 0 ? 25 : -25, { x: event.clientX, y: event.clientY })
}
function keyboardView(event: KeyboardEvent) {
  if (!geometry.value || event.ctrlKey || event.metaKey || event.altKey) { return }
  if (event.key === '+' || event.key === '=') {
    event.preventDefault()
    void changeZoom(25)
  }
  else if (event.key === '-') {
    event.preventDefault()
    void changeZoom(-25)
  }
  else if (event.key.toLowerCase() === 'w' || event.key === '1') {
    event.preventDefault()
    setViewMode(event.key === '1' ? 'actual' : 'width')
  }
  else if (event.key.toLowerCase() === 'r') {
    event.preventDefault()
    rotate(event.shiftKey ? -90 : 90)
  }
  else if (event.key === '0') {
    event.preventDefault()
    resetView()
  }
}
function stopPan(event?: PointerEvent) {
  if (event && pan?.pointerId !== event.pointerId) { return }
  const active = pan
  pan = undefined
  dragging.value = false
  const el = viewport.value
  if (active && el?.hasPointerCapture?.(active.pointerId)) { el.releasePointerCapture(active.pointerId) }
}
function beginPan(event: PointerEvent) {
  const el = viewport.value
  if (!geometry.value || !el || event.pointerType !== 'mouse' || event.button !== 0 || pan
    || !(event.target instanceof Element) || !event.target.closest('.scan-canvas')
    || (el.scrollWidth <= el.clientWidth && el.scrollHeight <= el.clientHeight)) { return }
  event.preventDefault()
  el.focus({ preventScroll: true })
  el.setPointerCapture(event.pointerId)
  pan = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, left: el.scrollLeft, top: el.scrollTop }
  dragging.value = true
}
function movePan(event: PointerEvent) {
  const el = viewport.value
  if (!pan || pan.pointerId !== event.pointerId || !el) { return }
  if (!(event.buttons & 1)) { stopPan(event); return }
  el.scrollLeft = pan.left - (event.clientX - pan.x)
  el.scrollTop = pan.top - (event.clientY - pan.y)
}
function decodeFailed(event: Event) {
  const image = event.target as HTMLImageElement
  if (!source.value || image.getAttribute('src') !== source.value) { return }
  clear()
  failed.value = true
}
function setViewMode(mode: typeof viewMode.value) {
  stopPan()
  viewMode.value = mode
  zoom.value = 100
  void centerImage()
}
function rotate(delta = 90) {
  stopPan()
  rotation.value = (rotation.value + delta + 360) % 360
  void centerImage()
}
function resetView() {
  stopPan()
  zoom.value = 100
  viewMode.value = 'window'
  rotation.value = 0
  void centerImage()
}
function clear() {
  stopPan()
  viewRevision++
  generation++
  controller?.abort()
  controller = undefined
  if (source.value) {
    URL.revokeObjectURL(source.value)
  }
  source.value = ''
  natural.value = { width: 0, height: 0 }
  zoom.value = 100
  viewMode.value = 'window'
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
    <div v-else-if="source" class="scan-viewer" :class="{ 'scan-viewer--focused': focused }">
      <div class="scan-viewer-toolbar" role="group" aria-label="原图显示控制">
        <div class="scan-tool-group" role="group" aria-label="缩放">
          <el-button size="small" :icon="Minus" :disabled="!geometry || zoom <= 50" @click="changeZoom(-25)">
            缩小
          </el-button>
          <span aria-live="polite" class="scan-viewer-percent">{{ zoom }}%</span>
          <el-button size="small" :icon="Plus" :disabled="!geometry || zoom >= 300" @click="changeZoom(25)">
            放大
          </el-button>
        </div>
        <div class="scan-tool-group" role="group" aria-label="旋转">
          <el-button size="small" :icon="RotateCw" :disabled="!geometry" @click="rotate()">
            旋转 90°
          </el-button>
          <el-button size="small" :icon="RotateCcw" :disabled="!geometry" @click="rotate(-90)">
            逆时针旋转
          </el-button>
          <span class="scan-viewer-angle">{{ rotation }}°</span>
        </div>
        <div class="scan-tool-group" role="group" aria-label="显示模式">
          <el-button size="small" :icon="Maximize" :disabled="!geometry" :aria-pressed="viewMode === 'window'" @click="resetView">
            适应窗口
          </el-button>
          <el-button size="small" :icon="StretchHorizontal" :disabled="!geometry" :aria-pressed="viewMode === 'width'" @click="setViewMode('width')">
            适应宽度
          </el-button>
          <el-button size="small" :icon="Scan" :disabled="!geometry" :aria-pressed="viewMode === 'actual'" @click="setViewMode('actual')">
            实际大小
          </el-button>
        </div>
      </div>
      <div ref="viewport" class="scan-viewport" :class="{ 'is-pannable': viewMode !== 'window' || zoom > 100, 'is-dragging': dragging }" tabindex="0" role="region" aria-label="原图滚动查看区" @wheel="wheelZoom" @keydown="keyboardView" @pointerdown="beginPan" @pointermove="movePan" @pointerup="stopPan" @pointercancel="stopPan" @lostpointercapture="stopPan">
        <div class="scan-canvas" :style="geometry ? { minWidth: `${geometry.boxWidth + 24}px`, minHeight: `${geometry.boxHeight + 24}px` } : undefined">
          <div ref="imageBox" class="scan-image-box" :style="geometry ? { width: `${geometry.boxWidth}px`, height: `${geometry.boxHeight}px` } : undefined">
            <img :key="source" :src="source" alt="待核对影像" class="scan-content" :class="{ 'scan-content--positioned': geometry }" :style="geometry ? { width: `${geometry.width}px`, height: `${geometry.height}px`, transform: `translate(-50%, -50%) rotate(${rotation}deg)` } : undefined" draggable="false" @load="decoded" @error="decodeFailed">
          </div>
        </div>
      </div>
      <p class="scan-viewer-hint">
        100% 相对于当前显示模式；实际大小为原图像素与 CSS 像素 1:1。可拖动或滚动；Ctrl / ⌘ + 滚轮缩放，聚焦查看区后 + / − 缩放，W 适宽、1 实际大小、R / Shift+R 旋转、0 复位。仅改变显示，不修改原文件。
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

.scan-tool-group {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-1);
  align-items: center;
  min-width: 0;
  padding-inline-end: var(--mrr-space-2);
  border-inline-end: 1px solid var(--mrr-border);
}

.scan-tool-group:last-child {
  padding-inline-end: 0;
  border-inline-end: 0;
}

/* Icon and label remain visible even when the nested workspace is very narrow. */
.scan-tool-group :deep(.el-button) {
  min-width: 0;
  max-width: 100%;
  height: auto;
  min-height: 24px;
  white-space: normal;
}

.scan-tool-group :deep(.el-button > span) {
  min-width: 0;
  overflow-wrap: anywhere;
  white-space: normal;
}

.scan-tool-group :deep(.el-button .el-icon) {
  flex-shrink: 0;
}

.scan-viewer-toolbar :deep(.el-button + .el-button) {
  margin-left: 0;
}

.scan-viewer-toolbar :deep(.el-button[aria-pressed="true"]) {
  color: var(--mrr-primary);
  background: var(--mrr-tone-primary-soft);
  border-color: var(--mrr-tone-primary-border);
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

.scan-viewer--focused .scan-viewport {
  height: clamp(260px, 64vh, 820px);
}

.scan-viewport.is-pannable {
  cursor: grab;
}

.scan-viewport.is-dragging {
  cursor: grabbing;
  user-select: none;
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
