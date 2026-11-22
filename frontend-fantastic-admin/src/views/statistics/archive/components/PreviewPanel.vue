<script setup lang="ts">
import type { ArchiveFitMode } from '../composables/useArchiveLocalPreferences'
import type { GalleryImage } from '../types'
import type { ArchivePreviewMode } from '@/utils/system-settings'
import { ElImageViewer } from 'element-plus'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import AppEmpty from '@/components/AppEmpty/index.vue'
import { isMotionEnabled } from '@/utils/motion-preference'
import { suppressFocusSelect } from '../utils/viewer-close-focus-guard'
import PreviewActionBar from './PreviewActionBar.vue'
import PreviewViewerToolbar from './PreviewViewerToolbar.vue'
import 'element-plus/es/components/image-viewer/style/css'

defineOptions({ name: 'PreviewPanel' })

const props = withDefaults(defineProps<{
  image: GalleryImage | null
  previewList: string[]
  index: number
  total: number
  isSelected: boolean
  showSelectionStatus?: boolean
  savingType?: boolean
  loading?: boolean
  fitMode?: ArchiveFitMode
  previewScale?: number
  emptyDescription?: string
}>(), {
  savingType: false,
  showSelectionStatus: true,
  loading: false,
  fitMode: 'height',
  previewScale: 100,
  emptyDescription: '请选择影像',
})

const emit = defineEmits<{
  toggle: []
  saveType: [image: GalleryImage, type: number]
  navigate: [delta: number]
  select: [index: number]
}>()

const displayMode = defineModel<ArchivePreviewMode>('displayMode', { default: 'single' })
const previewScroller = ref<HTMLElement | null>(null)
const pageRefs = ref<(HTMLElement | null)[]>([])
const failedImageUrls = ref<Set<string>>(new Set())
const isImageViewerOpen = ref(false)
const imageViewerIndex = ref(0)
const imageViewerRef = ref<{ setActiveItem: (index: number) => void } | null>(null)
let touchStartX = 0
let pageObserver: IntersectionObserver | null = null
/* 滚轮连续触发会一次跳多页：一次手势只允许切换一张（300ms 锁） */
let wheelNavLockUntil = 0
/* 滚动驱动的选中（IntersectionObserver）不回滚对齐，外部选中（点缩略图/键盘）才居中 */
let scrollDrivenSelection = false

const scrollPreviewStyle = computed(() => ({
  '--scroll-preview-scale': String(props.previewScale / 100),
}))

function handleWheelNav(event: WheelEvent) {
  if (Date.now() < wheelNavLockUntil) {
    return
  }
  const delta = Math.abs(event.deltaY) >= Math.abs(event.deltaX) ? event.deltaY : event.deltaX
  if (!delta) {
    return
  }
  event.preventDefault()
  wheelNavLockUntil = Date.now() + 300
  emit('navigate', delta > 0 ? 1 : -1)
}

function handleTouchStart(event: TouchEvent) {
  touchStartX = event.touches[0]?.clientX ?? 0
}

function handleTouchEnd(event: TouchEvent) {
  const endX = event.changedTouches[0]?.clientX ?? touchStartX
  if (Math.abs(endX - touchStartX) < 48) {
    return
  }
  emit('navigate', endX < touchStartX ? 1 : -1)
}

function handlePreviewSwitch(index: number) {
  if (index !== props.index) {
    emit('select', index)
  }
}

function openImageViewer() {
  imageViewerIndex.value = props.index
  isImageViewerOpen.value = true
}

function closeImageViewer() {
  isImageViewerOpen.value = false
  // focus-trap 释放时会把焦点还原到打开前的卡片并触发 @focus 回选旧图（P1→P5 关闭回跳 P1），
  // 短暂抑制卡片 @focus 的预览切换，让预览停留在关闭时的那张。
  suppressFocusSelect()
}

/* 页面级快捷键（空格开/关全屏）通过 ref 调用 */
defineExpose({ openImageViewer, closeImageViewer })

function handleImageViewerSwitch(index: number) {
  imageViewerIndex.value = index
  handlePreviewSwitch(index)
}

function syncCurrentPage(index: number) {
  const target = pageRefs.value[index]
  if (!target) {
    return
  }
  target.scrollIntoView({ behavior: isMotionEnabled() ? 'smooth' : 'auto', block: 'center' })
}

function imageUnavailable(imageUrl: string | undefined) {
  return !imageUrl || failedImageUrls.value.has(imageUrl)
}

function markImageFailed(imageUrl: string | undefined) {
  if (!imageUrl) {
    return
  }
  const next = new Set(failedImageUrls.value)
  next.add(imageUrl)
  failedImageUrls.value = next
}

/**
 * 图片加载完成打标：直接写目标元素 class，不进响应式状态，
 * 几百张连续页加载不会触发任何组件重渲染。慢速网络下加载占位靠该 class 退场。
 */
function onImgLoad(event: Event) {
  const target = event.target as HTMLImageElement
  if (target.naturalWidth > 0) {
    target.classList.add('is-loaded')
  }
}

function observePages() {
  pageObserver?.disconnect()
  pageObserver = null
  nextTick(() => {
    if (displayMode.value !== 'scroll' || !previewScroller.value || typeof IntersectionObserver === 'undefined') {
      return
    }
    pageObserver = new IntersectionObserver((entries) => {
      const visiblePage = entries
        .filter(entry => entry.isIntersecting)
        .sort((first, second) => second.intersectionRatio - first.intersectionRatio)[0]
      const index = Number((visiblePage?.target as HTMLElement | undefined)?.dataset.index)
      if (Number.isInteger(index) && index !== props.index) {
        // 用户手动滚动导致的选中变化：标记后跳过 watch 里的 scrollIntoView，避免打断滚动
        scrollDrivenSelection = true
        emit('select', index)
      }
    }, { root: previewScroller.value, threshold: 0.6 })
    pageRefs.value.forEach((page) => {
      if (page) {
        pageObserver?.observe(page)
      }
    })
    syncCurrentPage(props.index)
  })
}

watch(() => props.index, (index) => {
  if (isImageViewerOpen.value && index !== imageViewerIndex.value) {
    imageViewerIndex.value = index
    nextTick(() => imageViewerRef.value?.setActiveItem(index))
  }
  if (displayMode.value === 'scroll') {
    if (scrollDrivenSelection) {
      // 滚动中观察器报上来的选中：页面已在正确位置，不做居中对齐
      scrollDrivenSelection = false
      return
    }
    nextTick(() => syncCurrentPage(index))
  }
})

watch(displayMode, observePages)

onMounted(observePages)

onUnmounted(() => {
  pageObserver?.disconnect()
  pageObserver = null
})
</script>

<template>
  <div v-loading="props.loading" class="preview-panel" @touchstart.passive="handleTouchStart" @touchend="handleTouchEnd">
    <template v-if="props.image">
      <PreviewActionBar
        :image="props.image"
        :index="props.index"
        :total="props.total"
        :is-selected="props.isSelected"
        :show-selection-status="props.showSelectionStatus"
        :saving-type="props.savingType"
        @save-type="(image, type) => emit('saveType', image, type)"
        @navigate="emit('navigate', $event)"
        @toggle="emit('toggle')"
      />

      <div v-if="displayMode === 'single'" class="preview-stage single-stage" :class="`is-fit-${props.fitMode}`" @wheel="handleWheelNav">
        <div v-if="imageUnavailable(props.image.imageUrl)" class="preview-image-placeholder" role="img" :aria-label="`第 ${props.index + 1} 张影像加载失败`">
          <svg viewBox="0 0 48 48" aria-hidden="true">
            <path d="M8 11a3 3 0 0 1 3-3h26a3 3 0 0 1 3 3v19.76l-6.06-6.06a2 2 0 0 0-2.82 0l-2.4 2.4-6.68-6.68a2 2 0 0 0-2.82 0L8 31.64V11Zm0 26.28 12.64-12.64 6.68 6.68a2 2 0 0 0 2.82 0l2.4-2.4L40 36.4v.6a3 3 0 0 1-3 3H11a3 3 0 0 1-3-3v.28ZM31 18a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z" fill="currentColor" />
          </svg>
          <strong>影像加载失败</strong>
          <span>当前图片暂不可用，请检查影像服务或文件地址</span>
        </div>
        <img
          v-else
          :key="props.image.imageUrl"
          class="preview-image fit-image"
          :class="`fit-${props.fitMode}`"
          :src="props.image.imageUrl"
          :alt="`第 ${props.index + 1} 张影像`"
          @click="openImageViewer"
          @load="onImgLoad"
          @error="markImageFailed(props.image.imageUrl)"
        >
        <span class="img-loading-tip" aria-hidden="true">影像加载中…</span>
      </div>

      <ElImageViewer
        v-if="isImageViewerOpen"
        ref="imageViewerRef"
        :url-list="props.previewList"
        :initial-index="imageViewerIndex"
        :infinite="false"
        hide-on-click-modal
        :z-index="3000"
        teleported
        @close="closeImageViewer"
        @switch="handleImageViewerSwitch"
      >
        <template #toolbar="{ actions, reset, activeIndex }">
          <PreviewViewerToolbar
            :actions="actions"
            :reset="reset"
            :active-index="activeIndex"
            :total="props.previewList.length"
          />
        </template>
      </ElImageViewer>

      <div v-if="displayMode === 'scroll'" ref="previewScroller" class="preview-stage" :style="scrollPreviewStyle">
        <article
          v-for="(imageUrl, pageIndex) in props.previewList"
          :key="imageUrl || pageIndex"
          :ref="(element: any) => { pageRefs[pageIndex] = element }"
          class="continuous-page"
          :class="`is-fit-${props.fitMode}`"
          :data-index="pageIndex"
        >
          <div v-if="imageUnavailable(imageUrl)" class="preview-image-placeholder continuous-placeholder" role="img" :aria-label="`第 ${pageIndex + 1} 张影像加载失败`">
            <svg viewBox="0 0 48 48" aria-hidden="true">
              <path d="M8 11a3 3 0 0 1 3-3h26a3 3 0 0 1 3 3v19.76l-6.06-6.06a2 2 0 0 0-2.82 0l-2.4 2.4-6.68-6.68a2 2 0 0 0-2.82 0L8 31.64V11Zm0 26.28 12.64-12.64 6.68 6.68a2 2 0 0 0 2.82 0l2.4-2.4L40 36.4v.6a3 3 0 0 1-3 3H11a3 3 0 0 1-3-3v.28ZM31 18a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z" fill="currentColor" />
            </svg>
            <strong>P{{ pageIndex + 1 }} 加载失败</strong>
            <span>当前图片暂不可用</span>
          </div>
          <img
            v-else
            class="preview-image fit-scroll-image"
            :class="`fit-${props.fitMode}`"
            :src="imageUrl"
            :alt="`第 ${pageIndex + 1} 张影像`"
            loading="lazy"
            @load="onImgLoad"
            @error="markImageFailed(imageUrl)"
          >
          <span class="img-loading-tip" aria-hidden="true">P{{ pageIndex + 1 }} 加载中…</span>
        </article>
      </div>
    </template>
    <AppEmpty v-else icon="i-lucide:image-off" :description="props.emptyDescription" />
  </div>
</template>

<style scoped>
.preview-panel {
  position: relative;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.preview-stage {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: 24px;
  overflow: auto;
  background: var(--surface-alt);
  border: 1px solid var(--divider);
  border-radius: var(--mrr-radius-card);
}

.single-stage {
  position: relative;
  display: grid;
  grid-template-rows: minmax(0, 1fr);
  place-items: center;
  overflow: hidden;
}

/*
 * 慢速网络下的图片加载占位：img 未加载完成时背景显示低频微光，
 * 内容加载完直接覆盖背景，无需响应式状态参与。
 */
.preview-image:not(.is-loaded) {
  background-color: var(--surface-alt);
  background-image: linear-gradient(100deg, transparent 30%, rgb(255 255 255 / 45%) 50%, transparent 70%);
  background-size: 220% 100%;
  animation: var(--mrr-motion-decor, preview-shimmer var(--mrr-motion-loop-soft, 1.5s) linear infinite);
}

@keyframes preview-shimmer {
  from { background-position: 180% 0; }
  to { background-position: -80% 0; }
}

/* 加载提示与 img 相邻，img 完成加载（is-loaded）后相邻选择器自动失效隐藏 */
.img-loading-tip {
  position: absolute;
  top: 50%;
  left: 50%;
  display: none;
  font-size: 12px;
  color: var(--text-secondary);
  letter-spacing: 0.04em;
  white-space: nowrap;
  pointer-events: none;
  transform: translate(-50%, -50%);
}

.preview-image:not(.is-loaded) + .img-loading-tip {
  display: block;
}

.preview-image {
  display: block;
  height: auto;
  margin: 0 auto;
  background: var(--el-bg-color);
  box-shadow: 0 2px 8px rgb(0 0 0 / 10%);
}

.single-stage .fit-image {
  cursor: zoom-in;
  object-fit: contain; /* 尺寸盒与图片比例可能不一致（任一维先铺满后被另一维夹住），靠 contain 保证画面等比 */
  background: transparent;
  box-shadow: none;
}

/* 适应宽度：尺寸盒占满容器，图像等比落在盒内（小图会放大，永不裁切）。 */
.single-stage .fit-image.fit-width {
  width: 100%;
  max-width: 100%;
  height: 100%;
  max-height: 100%;
}

/* 适应高度：尺寸盒由图片等比决定，只在超出容器时缩小，不放大原尺寸图。 */
.single-stage .fit-image.fit-height {
  width: auto;
  max-width: 100%;
  height: auto;
  max-height: 100%;
}

/* 滚动模式：每页图片按容器最大宽度显示（乘以缩放设置），高度随比例自适应——不裁切不变形 */
.fit-scroll-image.fit-width,
.fit-scroll-image.fit-height {
  width: calc(100% * var(--scroll-preview-scale));
  height: auto;
}

.preview-image-placeholder {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  justify-content: center;
  width: min(100%, 720px);
  min-height: min(56vh, 560px);
  padding: 32px;
  margin: 0 auto;
  color: var(--text-secondary);
  text-align: center;
  background: radial-gradient(circle at top, hsl(var(--primary) / 8%), transparent 48%), var(--surface);
  border: 1px dashed var(--divider);
  border-radius: var(--mrr-radius-card);
}

.preview-image-placeholder svg {
  width: 52px;
  height: 52px;
  color: var(--text-tertiary);
  opacity: 0.72;
}

.preview-image-placeholder strong {
  font-size: 16px;
  color: var(--text-primary);
}

.preview-image-placeholder span {
  max-width: 420px;
  font-size: 13px;
  line-height: 1.6;
}

.continuous-placeholder {
  width: min(100%, 980px);
  min-height: 420px;
}

/* 图片按容器宽定宽后，页容器统一占满滚动宽度（不再按内容收缩，避免百分比宽度在 max-content 盒里循环解析） */
.continuous-page {
  position: relative;
  box-sizing: border-box;
  width: 100%;
  min-width: 0;

  /* 慢速网络下图片未就绪时给页容器保底高度，避免内容随加载逐页塌陷跳动 */
  min-height: 200px;
  padding: 8px;
  margin-bottom: 16px;
}

/* 全屏影像之上留一层悬浮工具条，样式与层级由 PreviewActionBar 自身维护。 */

@media (max-width: 720px) {
  .preview-stage {
    padding: 16px;
  }

  .preview-image-placeholder {
    min-height: 320px;
    padding: 20px;
  }
}
</style>
