<script setup lang="ts">
import type { GalleryImage, ViewMode } from '../types'
import { toRef } from 'vue'
import { useThumbLayout } from '../composables/useThumbLayout'
import { getTypeLabel } from '../constants'

defineOptions({ name: 'ThumbStrip' })

const props = defineProps<{
  images: GalleryImage[]
  viewMode: ViewMode
  selectedIndex: number
  isSelected: (img: GalleryImage) => boolean
}>()

const emit = defineEmits<{
  select: [index: number]
  toggle: [img: GalleryImage]
}>()

const thumbsContainer = ref<HTMLElement | null>(null)
const thumbRefs = ref<(HTMLElement | null)[]>([])

const viewModeRef = toRef(props, 'viewMode')
const { thumbItemWidth, pageSize, visibleCount, resetVisible } = useThumbLayout(thumbsContainer, viewModeRef)

const displayed = computed(() => props.images.slice(0, visibleCount.value))

function selectItem(index: number) {
  emit('select', index)
}

function toggleItem(img: GalleryImage, event: Event) {
  event.stopPropagation()
  emit('toggle', img)
}

function onImageError(event: Event) {
  const target = event.target as HTMLImageElement
  target.style.opacity = '0.35'
  target.alt = '图片加载失败'
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
  <div ref="thumbsContainer" class="thumb-strip" :class="props.viewMode">
    <div
      v-for="(img, index) in displayed"
      :key="img.id || img.filename || index"
      :ref="(el: any) => { thumbRefs[index] = el }"
      class="thumb-item"
      :class="{ active: index === props.selectedIndex, checked: props.isSelected(img) }"
      :style="props.viewMode === 'thumb' ? { width: `${thumbItemWidth}px` } : {}"
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
      <img
        v-if="props.viewMode === 'thumb'"
        :src="img.imageUrl"
        alt=""
        loading="lazy"
        @error="onImageError"
      >
      <span class="thumb-page">P{{ img.pages ?? '-' }}</span>
      <small>{{ getTypeLabel(img.btype) }}</small>
    </div>
    <div v-if="!props.images.length" class="empty-list">
      暂无影像
    </div>
    <div v-else-if="visibleCount < props.images.length" class="load-hint">
      滚动加载更多（{{ visibleCount }}/{{ props.images.length }}）
    </div>
  </div>
</template>

<style scoped>
.thumb-strip {
  box-sizing: border-box;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-content: flex-start;
  width: 100%;
  min-width: 0;
  padding: 8px;
  overflow-y: auto;
  background: var(--surface-muted);
  border: 1px solid var(--divider);
  border-radius: 10px;
}

.thumb-strip.list {
  flex-flow: column nowrap;
}

.thumb-strip.list .thumb-item {
  width: 100% !important;
}

.thumb-item {
  position: relative;
  display: grid;
  gap: 3px;
  min-width: 0;
  padding: 6px;
  cursor: pointer;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 6px;
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
  background: rgb(255 255 255 / 80%);
  border: 1.5px solid var(--divider);
  border-radius: 4px;
  transition: all 0.15s;
}

.thumb-check.checked {
  color: #fff;
  background: hsl(var(--primary));
  border-color: hsl(var(--primary));
}

.thumb-item img {
  width: 100%;
  height: 88px;
  aspect-ratio: 3 / 4;
  object-fit: cover;
  background: var(--surface-alt);
  border-radius: 4px;
}

.thumb-page {
  font-size: 12px;
  font-weight: 800;
  color: var(--text-primary);
}

.thumb-item small {
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
  .thumb-strip {
    max-height: 220px;
  }

  .thumb-strip.list {
    max-height: 200px;
  }
}
</style>
