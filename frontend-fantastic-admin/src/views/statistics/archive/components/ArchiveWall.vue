<script setup lang="ts">
import type { ArchiveWallDensity } from '../composables/useArchiveLocalPreferences'
import type { GalleryImage } from '../types'
import { ElImageViewer } from 'element-plus'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { getTypeLabel } from '../constants'
import 'element-plus/es/components/image-viewer/style/css'

const props = withDefaults(defineProps<{
  images: GalleryImage[]
  selectedIndex: number
  isSelected: (image: GalleryImage) => boolean
  cardWidth?: number
  density?: ArchiveWallDensity
  showMeta?: boolean
  selectionEnabled?: boolean
  loading?: boolean
}>(), {
  cardWidth: 240,
  density: 'comfortable',
  showMeta: true,
  selectionEnabled: true,
  loading: false,
})

const emit = defineEmits<{
  select: [index: number]
  toggle: [image: GalleryImage]
}>()

const INITIAL_VISIBLE_COUNT = 60
const LOAD_MORE_COUNT = 40
const wallScroller = ref<HTMLElement | null>(null)
const loadSentinel = ref<HTMLElement | null>(null)
const cardRefs = ref<(HTMLElement | null)[]>([])
const failedImageKeys = ref<Set<string>>(new Set())
const visibleCount = ref(Math.max(INITIAL_VISIBLE_COUNT, props.selectedIndex + 1))
const columnCount = ref(1)
const isImageViewerOpen = ref(false)
const imageViewerIndex = ref(0)
let loadObserver: IntersectionObserver | null = null
let resizeObserver: ResizeObserver | null = null

const displayedImages = computed(() => props.images.slice(0, visibleCount.value))
const previewList = computed(() => props.images.map(image => image.imageUrl || ''))
const wallGap = computed(() => ({
  compact: 6,
  comfortable: 10,
  spacious: 16,
})[props.density])
const wallStyle = computed(() => ({
  '--archive-wall-card-width': `${props.cardWidth}px`,
  '--archive-wall-gap': `${wallGap.value}px`,
}))

function imageKey(image: GalleryImage, index: number) {
  return String(image.imageUrl || image.id || image.filename || index)
}

function isImageFailed(image: GalleryImage, index: number) {
  return !image.imageUrl || failedImageKeys.value.has(imageKey(image, index))
}

function markImageFailed(image: GalleryImage, index: number) {
  const next = new Set(failedImageKeys.value)
  next.add(imageKey(image, index))
  failedImageKeys.value = next
}

function ensureVisible(index: number) {
  if (index >= visibleCount.value) {
    visibleCount.value = Math.min(props.images.length, index + LOAD_MORE_COUNT)
  }
}

function selectImage(index: number) {
  ensureVisible(index)
  emit('select', index)
}

function openImageViewer(index: number) {
  const image = props.images[index]
  if (!image || isImageFailed(image, index)) {
    return
  }
  selectImage(index)
  imageViewerIndex.value = index
  isImageViewerOpen.value = true
}

function closeImageViewer() {
  isImageViewerOpen.value = false
}

function handleImageViewerSwitch(index: number) {
  imageViewerIndex.value = index
  selectImage(index)
}

function toggleImage(image: GalleryImage | undefined, index: number) {
  if (!image || !props.selectionEnabled) {
    return
  }
  selectImage(index)
  emit('toggle', image)
}

function updateColumnCount() {
  const container = wallScroller.value
  if (!container) {
    return
  }
  columnCount.value = Math.max(1, Math.floor((container.clientWidth + wallGap.value) / (props.cardWidth + wallGap.value)))
}

function focusCard(index: number) {
  if (!props.images.length) {
    return
  }
  const nextIndex = Math.min(props.images.length - 1, Math.max(0, index))
  ensureVisible(nextIndex)
  selectImage(nextIndex)
  nextTick(() => {
    cardRefs.value[nextIndex]?.focus({ preventScroll: true })
    cardRefs.value[nextIndex]?.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' })
  })
}

function onItemKeydown(index: number, event: KeyboardEvent) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    toggleImage(props.images[index], index)
    return
  }

  let nextIndex: number | null = null
  if (event.key === 'ArrowLeft') {
    nextIndex = index - 1
  }
  else if (event.key === 'ArrowRight') {
    nextIndex = index + 1
  }
  else if (event.key === 'ArrowUp') {
    nextIndex = index - columnCount.value
  }
  else if (event.key === 'ArrowDown') {
    nextIndex = index + columnCount.value
  }
  else if (event.key === 'Home') {
    nextIndex = 0
  }
  else if (event.key === 'End') {
    nextIndex = props.images.length - 1
  }

  if (nextIndex !== null) {
    event.preventDefault()
    focusCard(nextIndex)
  }
}

function observeLoadSentinel() {
  loadObserver?.disconnect()
  loadObserver = null
  if (!loadSentinel.value || typeof IntersectionObserver === 'undefined') {
    return
  }
  loadObserver = new IntersectionObserver((entries) => {
    if (entries.some(entry => entry.isIntersecting) && visibleCount.value < props.images.length) {
      visibleCount.value = Math.min(props.images.length, visibleCount.value + LOAD_MORE_COUNT)
    }
  }, { root: wallScroller.value, rootMargin: '320px 0px' })
  loadObserver.observe(loadSentinel.value)
}

watch(() => props.images, () => {
  visibleCount.value = Math.max(INITIAL_VISIBLE_COUNT, props.selectedIndex + 1)
  failedImageKeys.value = new Set()
  cardRefs.value = []
  nextTick(observeLoadSentinel)
})

watch(() => props.selectedIndex, index => ensureVisible(index))
watch([() => props.cardWidth, () => props.density], () => nextTick(updateColumnCount))
watch(displayedImages, () => nextTick(observeLoadSentinel))

onMounted(() => {
  observeLoadSentinel()
  updateColumnCount()
  if (typeof ResizeObserver !== 'undefined' && wallScroller.value) {
    resizeObserver = new ResizeObserver(updateColumnCount)
    resizeObserver.observe(wallScroller.value)
  }
})

onUnmounted(() => {
  loadObserver?.disconnect()
  resizeObserver?.disconnect()
})
</script>

<template>
  <section
    ref="wallScroller"
    v-loading="props.loading"
    class="archive-wall"
    :style="wallStyle"
    aria-label="影像全景平铺"
  >
    <div v-if="props.images.length" class="archive-wall-grid">
      <article
        v-for="(image, index) in displayedImages"
        :key="image.id || image.filename || index"
        :ref="(element: any) => { cardRefs[index] = element }"
        class="archive-wall-card"
        :class="{
          active: index === props.selectedIndex,
          checked: props.selectionEnabled && props.isSelected(image),
        }"
        role="button"
        :aria-current="index === props.selectedIndex ? 'true' : undefined"
        :aria-pressed="props.selectionEnabled ? props.isSelected(image) : undefined"
        :aria-label="`第 ${index + 1} 张影像，${getTypeLabel(image.btype)}。按 Enter 选中，点击图片查看大图`"
        tabindex="0"
        @focus="selectImage(index)"
        @keydown.stop="onItemKeydown(index, $event)"
      >
        <button
          v-if="props.selectionEnabled"
          type="button"
          class="archive-wall-check"
          :class="{ checked: props.isSelected(image) }"
          :aria-label="props.isSelected(image) ? `取消选择第 ${index + 1} 张影像` : `选择第 ${index + 1} 张影像`"
          :aria-pressed="props.isSelected(image)"
          @click.stop="toggleImage(image, index)"
        >
          <svg v-if="props.isSelected(image)" viewBox="0 0 16 16" width="12" height="12" aria-hidden="true">
            <path d="M13.485 4.485a1 1 0 0 1 0 1.415l-6.5 6.5a1 1 0 0 1-1.414 0l-3-3a1 1 0 1 1 1.414-1.414L6.278 10.586l5.793-5.793a1 1 0 0 1 1.414 0z" fill="currentColor" />
          </svg>
        </button>

        <div class="archive-wall-image-stage" @click="openImageViewer(index)">
          <div
            v-if="isImageFailed(image, index)"
            class="archive-wall-placeholder"
            role="img"
            :aria-label="`第 ${index + 1} 张影像加载失败`"
          >
            <svg viewBox="0 0 48 48" aria-hidden="true">
              <path d="M8 11a3 3 0 0 1 3-3h26a3 3 0 0 1 3 3v19.76l-6.06-6.06a2 2 0 0 0-2.82 0l-2.4 2.4-6.68-6.68a2 2 0 0 0-2.82 0L8 31.64V11Zm0 26.28 12.64-12.64 6.68 6.68a2 2 0 0 0 2.82 0l2.4-2.4L40 36.4v.6a3 3 0 0 1-3 3H11a3 3 0 0 1-3-3v.28ZM31 18a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z" fill="currentColor" />
            </svg>
            <strong>加载失败</strong>
          </div>
          <img
            v-else
            :src="image.imageUrl"
            :alt="`第 ${index + 1} 张影像`"
            loading="lazy"
            decoding="async"
            @error="markImageFailed(image, index)"
          >
          <span v-if="!isImageFailed(image, index)" class="archive-wall-zoom-hint">查看大图</span>
        </div>

        <footer v-if="props.showMeta" class="archive-wall-meta">
          <strong>P{{ image.pages ?? index + 1 }}</strong>
          <span>{{ getTypeLabel(image.btype) }}</span>
        </footer>
      </article>
    </div>

    <el-empty v-else description="当前类型暂无影像" />

    <div
      v-if="displayedImages.length < props.images.length"
      ref="loadSentinel"
      class="archive-wall-load-sentinel"
    >
      正在加载更多（{{ displayedImages.length }}/{{ props.images.length }}）
    </div>

    <ElImageViewer
      v-if="isImageViewerOpen"
      :url-list="previewList"
      :initial-index="imageViewerIndex"
      :infinite="false"
      :hide-on-click-modal="false"
      :z-index="3000"
      teleported
      show-progress
      @close="closeImageViewer"
      @switch="handleImageViewerSwitch"
    />
  </section>
</template>

<style scoped>
.archive-wall {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: 10px 10px 92px;
  overflow: auto;
  background:
    radial-gradient(circle at top right, hsl(var(--primary) / 5%), transparent 32%),
    var(--surface-muted);
  border: 1px solid var(--divider);
  border-radius: 14px;
}

.archive-wall-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(var(--archive-wall-card-width), 100%), 1fr));
  gap: var(--archive-wall-gap);
  align-items: start;
}

.archive-wall-card {
  position: relative;
  box-sizing: border-box;
  min-width: 0;
  overflow: hidden;
  outline: none;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 9px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}

.archive-wall-card:hover {
  border-color: var(--mrr-navigation-active-border);
  box-shadow: 0 8px 20px rgb(0 0 0 / 8%);
  transform: translateY(-1px);
}

.archive-wall-card:focus-visible,
.archive-wall-card.active,
.archive-wall-card.checked {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgb(64 158 255 / 18%);
}

.archive-wall-check {
  position: absolute;
  top: 7px;
  left: 7px;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  color: #fff;
  cursor: pointer;
  background: rgb(255 255 255 / 90%);
  border: 1.5px solid var(--divider);
  border-radius: 6px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 10%);
}

.archive-wall-check.checked {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.archive-wall-check:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.archive-wall-image-stage {
  position: relative;
  display: grid;
  place-items: center;
  min-height: 320px;
  aspect-ratio: 3 / 4;
  overflow: hidden;
  cursor: zoom-in;
  background:
    linear-gradient(135deg, hsl(var(--primary) / 4%), transparent 62%),
    var(--surface-alt);
}

.archive-wall-image-stage img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  transition: transform 0.18s ease;
}

.archive-wall-card:hover .archive-wall-image-stage img {
  transform: scale(1.01);
}

.archive-wall-zoom-hint {
  position: absolute;
  right: 8px;
  bottom: 8px;
  padding: 4px 7px;
  font-size: 11px;
  color: #fff;
  pointer-events: none;
  background: rgb(0 0 0 / 56%);
  border-radius: 999px;
  opacity: 0;
  transform: translateY(3px);
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.archive-wall-card:hover .archive-wall-zoom-hint,
.archive-wall-card:focus-visible .archive-wall-zoom-hint {
  opacity: 1;
  transform: translateY(0);
}

.archive-wall-placeholder {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
}

.archive-wall-placeholder svg {
  width: 42px;
  height: 42px;
  opacity: 0.6;
}

.archive-wall-placeholder strong {
  font-size: 12px;
}

.archive-wall-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
  padding: 8px 9px;
  border-top: 1px solid var(--divider);
}

.archive-wall-meta strong {
  flex: none;
  font-size: 12px;
  color: var(--text-primary);
}

.archive-wall-meta span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.archive-wall-load-sentinel {
  padding: 20px 0 4px;
  font-size: 12px;
  color: var(--text-tertiary);
  text-align: center;
}

@media (max-width: 720px) {
  .archive-wall {
    padding: 8px 8px 112px;
  }
}
</style>
