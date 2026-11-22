<script setup lang="ts">
import type { ArchiveWallDensity } from '../composables/useArchiveLocalPreferences'
import type { GalleryImage } from '../types'
import { ElImageViewer } from 'element-plus'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import AppEmpty from '@/components/AppEmpty/index.vue'
import { Z_INDEX } from '@/constants/z-index'
import { isMotionEnabled } from '@/utils/motion-preference'
import { getTypeLabel } from '../constants'
import { isFocusSelectSuppressed, suppressFocusSelect } from '../utils/viewer-close-focus-guard'
import PreviewActionBar from './PreviewActionBar.vue'
import PreviewViewerToolbar from './PreviewViewerToolbar.vue'
import 'element-plus/es/components/image-viewer/style/css'

const props = withDefaults(defineProps<{
  images: GalleryImage[]
  selectedIndex: number
  isSelected: (image: GalleryImage) => boolean
  cardWidth?: number
  density?: ArchiveWallDensity
  showMeta?: boolean
  selectionEnabled?: boolean
  savingType?: boolean
  loading?: boolean
}>(), {
  cardWidth: 240,
  density: 'comfortable',
  showMeta: true,
  selectionEnabled: true,
  savingType: false,
  loading: false,
})

const emit = defineEmits<{
  select: [index: number]
  toggle: [image: GalleryImage]
  saveType: [image: GalleryImage, type: number]
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
const imageViewerRef = ref<{ setActiveItem: (index: number) => void } | null>(null)
let loadObserver: IntersectionObserver | null = null
let resizeObserver: ResizeObserver | null = null

const displayedImages = computed(() => props.images.slice(0, visibleCount.value))
const previewList = computed(() => props.images.map(image => image.imageUrl || ''))
const viewerImage = computed(() => props.images[imageViewerIndex.value] || null)
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

/* 加载完成打标：写目标元素 class，不走响应式状态，平铺墙整批图片加载零重渲染。 */
function onImgLoad(event: Event) {
  const target = event.target as HTMLImageElement
  if (target.naturalWidth > 0) {
    target.classList.add('is-loaded')
  }
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

function onCardFocus(index: number) {
  // 全屏查看器关闭时 focus-trap 会还原焦点到打开前的卡片，
  // 还原引发的 focus 不切换预览（避免 P1→P5 关闭后回跳 P1）
  if (isFocusSelectSuppressed()) {
    return
  }
  selectImage(index)
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
  // focus-trap 释放时会把焦点还原到打开前的卡片并触发 @focus 回选旧图（P1→P5 关闭回跳 P1），
  // 短暂抑制卡片 @focus 的预览切换，让预览停留在关闭时的那张。
  suppressFocusSelect()
}

/* 页面级快捷键（空格开/关全屏）通过 ref 调用：不带参时默认打开当前预览卡 */
defineExpose({
  openImageViewer: (index?: number) => openImageViewer(index ?? props.selectedIndex),
  closeImageViewer,
})

function handleImageViewerSwitch(index: number) {
  imageViewerIndex.value = index
  selectImage(index)
}

/** 全屏里的翻页工具条：同步卡片选中态，并驱动 ElImageViewer 切到同一张。 */
function navigateViewer(delta: number) {
  if (!props.images.length) {
    return
  }
  const next = Math.min(props.images.length - 1, Math.max(0, imageViewerIndex.value + delta))
  if (next === imageViewerIndex.value) {
    return
  }
  imageViewerIndex.value = next
  selectImage(next)
  nextTick(() => imageViewerRef.value?.setActiveItem(next))
}

function toggleViewerImage() {
  const image = viewerImage.value
  if (!image || !props.selectionEnabled) {
    return
  }
  emit('toggle', image)
}

function toggleImage(image: GalleryImage | undefined, index: number) {
  if (!image || !props.selectionEnabled) {
    return
  }
  selectImage(index)
  emit('toggle', image)
}

function onMetaClick(image: GalleryImage | undefined, index: number, event: MouseEvent) {
  // 悬浮条整块可点（与缩略图条一致）：点页码/类型文字等同点勾选框；
  // 未开启选择时条只是展示，放行点击不做拦截。
  if (!props.selectionEnabled || !image) {
    return
  }
  event.stopPropagation()
  toggleImage(image, index)
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
    cardRefs.value[nextIndex]?.scrollIntoView({ behavior: isMotionEnabled() ? 'smooth' : 'auto', block: 'nearest', inline: 'nearest' })
  })
}

function onItemKeydown(index: number, event: KeyboardEvent) {
  // Enter/Space 已由页面级快捷键统一接管（回车=勾选当前预览图、空格=开关全屏），
  // 不能在这里拦冒泡；卡片只负责方向键/Home/End 的网格移动（拦截冒泡防止页面重复导航）。
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
    // 只拦方向键/Home/End 的冒泡（防页面级监听重复导航）；Enter/Space 必须放行给全局快捷键
    event.stopPropagation()
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

watch(() => props.selectedIndex, (index) => {
  ensureVisible(index)
  // 键盘左右键等外部翻页也要带动已打开的全屏查看器，避免工具条显示的页码与画面对不上
  if (isImageViewerOpen.value && index !== imageViewerIndex.value) {
    imageViewerIndex.value = index
    nextTick(() => imageViewerRef.value?.setActiveItem(index))
  }
})
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
        @focus="onCardFocus(index)"
        @keydown="onItemKeydown(index, $event)"
      >
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
            :key="image.imageUrl"
            :src="image.imageUrl"
            :alt="`第 ${index + 1} 张影像`"
            loading="lazy"
            decoding="async"
            @load="onImgLoad"
            @error="markImageFailed(image, index)"
          >
        </div>

        <!-- meta 悬浮在影像左下角，结构与缩略图条一致：勾选框 + 页码 + 类型 -->
        <footer
          v-if="props.showMeta || props.selectionEnabled"
          class="archive-wall-meta"
          :class="{ 'wall-meta--interactive': props.selectionEnabled }"
          @click="onMetaClick(image, index, $event)"
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
            <svg v-if="props.isSelected(image)" viewBox="0 0 16 16" width="10" height="10" aria-hidden="true">
              <path d="M13.485 4.485a1 1 0 0 1 0 1.415l-6.5 6.5a1 1 0 0 1-1.414 0l-3-3a1 1 0 1 1 1.414-1.414L6.278 10.586l5.793-5.793a1 1 0 0 1 1.414 0z" fill="currentColor" />
            </svg>
          </button>
          <template v-if="props.showMeta">
            <strong>P{{ image.pages ?? index + 1 }}</strong>
            <span>{{ getTypeLabel(image.btype) }}</span>
          </template>
        </footer>
      </article>
    </div>

    <AppEmpty v-else icon="i-lucide:image-off" description="当前类型暂无影像" />

    <div
      v-if="displayedImages.length < props.images.length"
      ref="loadSentinel"
      class="archive-wall-load-sentinel"
    >
      正在加载更多（{{ displayedImages.length }}/{{ props.images.length }}）
    </div>

    <ElImageViewer
      v-if="isImageViewerOpen"
      ref="imageViewerRef"
      :url-list="previewList"
      :initial-index="imageViewerIndex"
      :infinite="false"
      hide-on-click-modal
      :z-index="Z_INDEX.viewer"
      teleported
      @close="closeImageViewer"
      @switch="handleImageViewerSwitch"
    >
      <!-- 底部工具条与右侧预览面板共用一份：页码放进胶囊内部，不再单独漂浮 -->
      <template #toolbar="{ actions, reset, activeIndex }">
        <PreviewViewerToolbar
          :actions="actions"
          :reset="reset"
          :active-index="activeIndex"
          :total="previewList.length"
        />
      </template>
    </ElImageViewer>

    <!-- 全屏查看时同一套「分类切换 + 翻页 + 选中」工具条：平铺模式没有 PreviewPanel，
         这里必须自己挂一份，否则点开大图就只能看不能改。 -->
    <PreviewActionBar
      v-if="isImageViewerOpen && viewerImage"
      :image="viewerImage"
      :index="imageViewerIndex"
      :total="props.images.length"
      :is-selected="props.isSelected(viewerImage)"
      :show-selection-status="props.selectionEnabled"
      :saving-type="props.savingType"
      @save-type="(image, type) => emit('saveType', image, type)"
      @navigate="navigateViewer"
      @toggle="toggleViewerImage"
    />
  </section>
</template>

<style scoped>
.archive-wall {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: var(--mrr-space-3) var(--mrr-space-3) 92px;
  overflow: auto;
  background:
    radial-gradient(circle at top right, hsl(var(--primary) / 5%), transparent 32%),
    var(--surface-muted);
  border: 1px solid var(--divider);
  border-radius: var(--mrr-radius-card);
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
  border-radius: var(--mrr-radius-card);
  transition: border-color var(--mrr-motion-feedback) var(--mrr-ease-out), box-shadow var(--mrr-motion-feedback) var(--mrr-ease-out), transform var(--mrr-motion-press) var(--mrr-ease-out);
}

.archive-wall-card:hover {
  border-color: var(--mrr-navigation-active-border);
  box-shadow: 0 8px 20px rgb(0 0 0 / 8%);
  transform: var(--mrr-motion-decor, translateY(-1px));
}

/* 勾选态用「面」：淡蓝底填充，外圈不加强调描边 */
.archive-wall-card.checked {
  background: var(--mrr-navigation-active);
}

/* 预览选中态用「线」：底色不变，主题色实线双圈描边；叠加态自动 = 填充 + 实线圈。
 * 键盘焦点不再单独画圈：方向键/Tab 走 @focus → selectImage，预览跟随焦点，
 * active 圈就是唯一的焦点指示（base 的 outline:none 保持）。 */
.archive-wall-card.active {
  border-color: var(--color-primary);

  /* 平铺卡大且多为白底文档，1+1px 细圈贴在影像边缘不显眼：
     内圈加粗到 3px，外圈再罩一层 18% 主题色柔光，远看也能定位预览卡 */
  box-shadow:
    inset 0 0 0 2px var(--color-primary),
    0 0 0 3px hsl(var(--primary) / 18%);
}

/* 勾选框与缩略图条同一形态：行内 16px，常驻只有白描边，悬停才填充白色，勾选后主题色实底。 */
.archive-wall-check {
  position: relative; /* 行内占位，::after 命中区相对自身扩展 */
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  padding: 0;
  color: var(--mrr-primary-foreground);
  cursor: pointer;
  background: transparent;
  border: 1.5px solid rgb(255 255 255 / 85%);
  border-radius: var(--mrr-radius-sm);
  transition: color var(--mrr-motion-feedback) var(--mrr-ease-out), background-color var(--mrr-motion-feedback) var(--mrr-ease-out), border-color var(--mrr-motion-feedback) var(--mrr-ease-out);
}

/* 命中区比视觉块大一圈，但只在上下与左侧外扩，不盖住右侧页码的点击范围。 */
.archive-wall-check::after {
  position: absolute;
  inset: -4px -2px -4px -4px;
  content: "";
  border-radius: inherit;
}

.archive-wall-check:hover {
  background: rgb(255 255 255 / 86%);
  border-color: rgb(255 255 255 / 95%);
}

.archive-wall-check.checked {
  background: var(--color-primary);
  border-color: var(--color-primary);
  box-shadow: none;
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
}

/* 未加载完成时显示低频微光占位，加载后由图像内容覆盖（关动效时为静态占位底色） */
.archive-wall-image-stage img:not(.is-loaded) {
  background-color: var(--surface-alt);
  background-image: linear-gradient(100deg, transparent 30%, rgb(255 255 255 / 45%) 50%, transparent 70%);
  background-size: 220% 100%;
  animation: var(--mrr-motion-decor, wall-shimmer var(--mrr-motion-loop-soft, 1.5s) linear infinite);
}

@keyframes wall-shimmer {
  from { background-position: 180% 0; }
  to { background-position: -80% 0; }
}

/*
 * 悬停不再放大影像：1% 缩放会让扫描件文字发生亚像素重采样、短暂发虚，
 * 对「看清病案内容」是负收益。悬停反馈由卡片描边与投影承担。
 */

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

/*
 * meta 悬浮条与 ThumbStrip 同款：只锚定左下角、宽度贴内容（shrink-to-fit），
 * 72% 深遮罩上白字（白底文档不垫灰，对比度页码 6.3 / 类型名 5.4）。
 */
.archive-wall-meta {
  position: absolute;
  bottom: 11px;
  left: 11px;
  z-index: 2;
  display: flex;
  gap: var(--mrr-space-2);
  align-items: center;
  min-width: 0;
  max-width: calc(100% - 22px);
  padding: 4px 8px;
  background: rgb(15 23 42 / 72%);
  border-radius: var(--mrr-radius-sm);
}

/* 开启选择时整条可点（见 onMetaClick），悬停微亮给出可点暗示 */
.archive-wall-meta.wall-meta--interactive {
  cursor: pointer;
}

.archive-wall-meta.wall-meta--interactive:hover {
  background: rgb(15 23 42 / 82%);
}

.archive-wall-meta strong {
  flex: none;
  font-size: 12px;
  font-weight: 800;
  color: rgb(255 255 255 / 92%);
}

.archive-wall-meta span {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: rgb(255 255 255 / 82%);
  white-space: nowrap;
}

.archive-wall-load-sentinel {
  padding: 20px 0 4px;
  font-size: 12px;
  color: var(--text-tertiary);
  text-align: center;
}

/* stylelint-disable-next-line media-feature-range-notation -- Chrome 86 不支持范围媒体查询 */
@media (max-width: 720px) {
  .archive-wall {
    padding: 8px 8px 112px;
  }
}
</style>
