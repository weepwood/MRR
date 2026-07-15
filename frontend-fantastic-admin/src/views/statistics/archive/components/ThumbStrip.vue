<script setup lang="ts">
import type { GalleryImage, ViewMode } from '../types'
import { useThumbLayout } from '../composables/useThumbLayout'
import { getTypeLabel } from '../constants'

defineOptions({ name: 'ThumbStrip' })

const props = withDefaults(defineProps<{
  images: GalleryImage[]
  selectedIndex: number
  isSelected: (img: GalleryImage) => boolean
  thumbnailSize?: number
  preloadCount?: number
}>(), {
  thumbnailSize: 200,
  preloadCount: 20,
})

const emit = defineEmits<{
  select: [index: number]
  toggle: [img: GalleryImage]
}>()

const thumbsContainer = ref<HTMLElement | null>(null)
const thumbRefs = ref<(HTMLElement | null)[]>([])
const failedImageKeys = ref<Set<string>>(new Set())
const viewMode = defineModel<ViewMode>('viewMode', { default: 'thumb' })
const thumbnailSize = toRef(props, 'thumbnailSize')
const preloadCount = toRef(props, 'preloadCount')

const { thumbItemWidth, pageSize, visibleCount, resetVisible } = useThumbLayout(
  thumbsContainer,
  viewMode,
  thumbnailSize,
  preloadCount,
)

const displayed = computed(() => props.images.slice(0, visibleCount.value))

function selectItem(index: number) {
  emit('select', index)
}

function toggleItem(img: GalleryImage, event: Event) {
  event.stopPropagation()
  emit('toggle', img)
}

function imageKey(img: GalleryImage, index: number) {
  return String(img.imageUrl || img.id || img.filename || index)
}

function isImageFailed(img: GalleryImage, index: number) {
  return !img.imageUrl || failedImageKeys.value.has(imageKey(img, index))
}

function onImageError(img: GalleryImage, index: number) {
  const next = new Set(failedImageKeys.value)
  next.add(imageKey(img, index))
  failedImageKeys.value = next
}

function onItemKeydown(index: number, event: KeyboardEvent) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    selectItem(index)
  }
}

function onToggleKeydown(img: GalleryImage, event: KeyboardEvent) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    event.stopPropagation()
    emit('toggle', img)
  }
}

function scrollToIndex(index: number, smooth = true) {
  if (index >= visibleCount.value) {
    visibleCount.value = Math.max(visibleCount.value, index + pageSize.value)
  }
  nextTick(() => {
    const container = thumbsContainer.value
    const target = thumbRefs.value[index]
    if (!container || !target) {
      return
    }
    const top = target.offsetTop - (container.clientHeight - target.clientHeight) / 2
    const scrollTop = Math.max(0, top)
    if (smooth && 'scrollBehavior' in document.documentElement.style) {
      container.scrollTo({ top: scrollTop, behavior: 'smooth' })
    }
    else {
      container.scrollTop = scrollTop
    }
  })
}

defineExpose({ resetVisible, scrollToIndex })
</script>

<template>
  <section class="thumb-panel" :class="viewMode">
    <div ref="thumbsContainer" class="thumb-strip" :class="viewMode">
      <div
        v-for="(img, index) in displayed"
        :key="img.id || img.filename || index"
        :ref="(el: any) => { thumbRefs[index] = el }"
        class="thumb-item"
        :class="{ active: index === props.selectedIndex, checked: props.isSelected(img) }"
        :style="viewMode === 'thumb' ? { width: `${thumbItemWidth}px` } : {}"
        role="button"
        :aria-current="index === props.selectedIndex ? 'true' : undefined"
        :aria-label="`第 ${index + 1} 张影像，${getTypeLabel(img.btype)}`"
        tabindex="0"
        @click="selectItem(index)"
        @keydown="onItemKeydown(index, $event)"
      >
        <span class="thumb-check" :class="{ checked: props.isSelected(img) }" role="checkbox" :aria-checked="props.isSelected(img)" tabindex="0" @click="toggleItem(img, $event)" @keydown="onToggleKeydown(img, $event)">
          <svg v-if="props.isSelected(img)" viewBox="0 0 16 16" width="12" height="12"><path d="M13.485 4.485a1 1 0 0 1 0 1.415l-6.5 6.5a1 1 0 0 1-1.414 0l-3-3a1 1 0 1 1 1.414-1.414L6.278 10.586l5.793-5.793a1 1 0 0 1 1.414 0z" fill="currentColor" /></svg>
        </span>
        <div
          v-if="viewMode === 'thumb' && isImageFailed(img, index)"
          class="thumb-image-placeholder"
          role="img"
          :aria-label="`第 ${index + 1} 张影像加载失败`"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 5.5A1.5 1.5 0 0 1 5.5 4h13A1.5 1.5 0 0 1 20 5.5v9.88l-3.03-3.03a1 1 0 0 0-1.41 0l-1.2 1.2-3.34-3.34a1 1 0 0 0-1.41 0L4 15.82V5.5Zm0 13.14 6.32-6.32 3.34 3.34a1 1 0 0 0 1.41 0l1.2-1.2L20 18.2v.3a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5v.14ZM15.5 9A1.5 1.5 0 1 0 15.5 6a1.5 1.5 0 0 0 0 3Z" fill="currentColor" />
          </svg>
          <span>加载失败</span>
        </div>
        <img
          v-else-if="viewMode === 'thumb'"
          :src="img.imageUrl"
          :alt="`第 ${index + 1} 张影像缩略图`"
          loading="lazy"
          @error="onImageError(img, index)"
        >
        <div class="thumb-meta">
          <span class="thumb-page">P{{ img.pages ?? '-' }}</span>
          <small>{{ getTypeLabel(img.btype) }}</small>
        </div>
      </div>
      <div v-if="!props.images.length" class="empty-list">
        暂无影像
      </div>
      <div v-else-if="visibleCount < props.images.length" class="load-hint">
        滚动加载更多（{{ visibleCount }}/{{ props.images.length }}）
      </div>
    </div>
  </section>
</template>

<style scoped>
.thumb-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.thumb-strip {
  box-sizing: border-box;
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 6px;
  align-content: flex-start;
  width: 100%;
  min-width: 0;
  padding: 8px;
  overflow-y: auto;
  scrollbar-gutter: stable;
  background: var(--surface-muted);
  border: 1px solid var(--divider);
  border-radius: 10px;
}

.thumb-strip.list {
  flex-flow: column nowrap;
}

.thumb-strip.list .thumb-item {
  grid-template-columns: 20px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  width: 100% !important;
  min-height: 42px;
  padding: 8px 10px;
}

.thumb-strip.list .thumb-check {
  position: static;
}

.thumb-item {
  position: relative;
  box-sizing: border-box;
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 6px;
  cursor: pointer;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 7px;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.thumb-item:focus-visible,
.thumb-check:focus-visible {
  outline: 2px solid hsl(var(--primary));
  outline-offset: 2px;
}

.thumb-item:hover {
  border-color: hsl(var(--primary) / 50%);
}

.thumb-item.active {
  border-color: hsl(var(--primary));
  box-shadow: 0 0 0 2px hsl(var(--primary) / 18%);
}

.thumb-item.checked {
  border-color: hsl(var(--primary));
  box-shadow: 0 0 0 2px hsl(var(--primary) / 0.25%);
}

.thumb-check {
  position: absolute;
  top: 4px;
  left: 4px;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  color: #fff;
  background: rgb(255 255 255 / 88%);
  border: 1.5px solid var(--divider);
  border-radius: 4px;
  transition: color 0.15s, background-color 0.15s, border-color 0.15s;
}

.thumb-check.checked {
  color: #fff;
  background: hsl(var(--primary));
  border-color: hsl(var(--primary));
}

.thumb-item img,
.thumb-image-placeholder {
  box-sizing: border-box;
  width: 100%;
  border-radius: 5px;
}

.thumb-item img {
  display: block;
  height: auto;
  object-fit: contain;
  background: var(--surface-alt);
}

.thumb-image-placeholder {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  justify-content: center;
  aspect-ratio: 3 / 4;
  padding: 10px;
  font-size: 11px;
  color: var(--text-tertiary);
  text-align: center;
  background:
    linear-gradient(135deg, hsl(var(--primary) / 4%), transparent 60%),
    var(--surface-alt);
  border: 1px dashed var(--divider);
}

.thumb-image-placeholder svg {
  width: 26px;
  height: 26px;
  opacity: 0.62;
}

.thumb-meta {
  display: flex;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.thumb-page {
  flex: none;
  font-size: 12px;
  font-weight: 800;
  color: var(--text-primary);
}

.thumb-item small {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.empty-list,
.load-hint {
  grid-column: 1 / -1;
  padding: 16px 0;
  font-size: 12px;
  color: var(--text-tertiary);
  text-align: center;
}

@media (width <= 1100px) {
  .thumb-panel {
    max-height: 240px;
  }

  .thumb-panel.list {
    max-height: 220px;
  }
}
</style>
