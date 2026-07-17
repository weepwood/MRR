<script setup lang="ts">
import type { ArchiveFitMode } from '../composables/useArchiveLocalPreferences'
import type { GalleryImage } from '../types'
import type { ArchivePreviewMode } from '@/utils/system-settings'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { ElImageViewer, ElMessageBox } from 'element-plus'
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { TYPE_OPTIONS } from '../constants'
import 'element-plus/es/components/image-viewer/style/css'
import 'element-plus/es/components/message-box/style/css'

defineOptions({ name: 'PreviewPanel' })

const props = withDefaults(defineProps<{
  image: GalleryImage | null
  previewList: string[]
  index: number
  total: number
  isSelected: boolean
  savingType?: boolean
  loading?: boolean
  fitMode?: ArchiveFitMode
  emptyDescription?: string
}>(), {
  savingType: false,
  loading: false,
  fitMode: 'height',
  emptyDescription: '请选择影像',
})

const emit = defineEmits<{
  toggle: []
  saveType: [type: number]
  navigate: [delta: number]
  select: [index: number]
}>()

const displayMode = defineModel<ArchivePreviewMode>('displayMode', { default: 'single' })
const previewScroller = ref<HTMLElement | null>(null)
const pageRefs = ref<(HTMLElement | null)[]>([])
const pendingType = ref(0)
const failedImageUrls = ref<Set<string>>(new Set())
const isImageViewerOpen = ref(false)
const imageViewerIndex = ref(0)
const imageViewerRef = ref<{ setActiveItem: (index: number) => void } | null>(null)
let touchStartX = 0
let pageObserver: IntersectionObserver | null = null

const currentType = computed({
  get: () => Number(props.image?.btype || 0),
  set: (value: number) => emit('saveType', value),
})

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
}

function handleImageViewerSwitch(index: number) {
  imageViewerIndex.value = index
  handlePreviewSwitch(index)
}

function syncCurrentPage(index: number) {
  const target = pageRefs.value[index]
  if (!target) {
    return
  }
  target.scrollIntoView({ behavior: 'smooth', block: 'center' })
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

async function confirmType() {
  if (pendingType.value === currentType.value) {
    return
  }
  const label = TYPE_OPTIONS.find(item => item.value === pendingType.value)?.label || '所选分类'
  try {
    await ElMessageBox.confirm(
      `确认将当前影像分类切换为“${label}”吗？`,
      '确认分类变更',
      { confirmButtonText: '确认切换', cancelButtonText: '取消', type: 'warning' },
    )
    currentType.value = pendingType.value
  }
  catch {
    pendingType.value = currentType.value
  }
}

watch(() => props.index, (index) => {
  if (isImageViewerOpen.value && index !== imageViewerIndex.value) {
    imageViewerIndex.value = index
    nextTick(() => imageViewerRef.value?.setActiveItem(index))
  }
  if (displayMode.value === 'scroll') {
    nextTick(() => syncCurrentPage(index))
  }
})

watch(() => props.image?.btype, () => {
  pendingType.value = currentType.value
}, { immediate: true })

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
      <div class="preview-toolbar">
        <el-select
          v-model="pendingType"
          class="preview-type-select"
          aria-label="影像分类"
          :loading="props.savingType"
          size="small"
          @change="confirmType"
        >
          <el-option v-for="item in TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>

        <div class="preview-controls">
          <div class="page-navigation" aria-label="影像翻页">
            <el-button circle size="small" :icon="ArrowLeft" :disabled="props.index === 0" aria-label="上一张影像" @click="emit('navigate', -1)" />
            <el-button circle size="small" :icon="ArrowRight" :disabled="props.index >= props.total - 1" aria-label="下一张影像" @click="emit('navigate', 1)" />
          </div>
          <el-button size="small" type="primary" :plain="!props.isSelected" @click="emit('toggle')">
            P{{ props.image.pages ?? '-' }} {{ props.isSelected ? '已选' : '选中' }}
          </el-button>
        </div>
      </div>

      <div v-if="displayMode === 'single'" class="preview-stage single-stage" :class="`is-fit-${props.fitMode}`">
        <div v-if="imageUnavailable(props.image.imageUrl)" class="preview-image-placeholder" role="img" :aria-label="`第 ${props.index + 1} 张影像加载失败`">
          <svg viewBox="0 0 48 48" aria-hidden="true">
            <path d="M8 11a3 3 0 0 1 3-3h26a3 3 0 0 1 3 3v19.76l-6.06-6.06a2 2 0 0 0-2.82 0l-2.4 2.4-6.68-6.68a2 2 0 0 0-2.82 0L8 31.64V11Zm0 26.28 12.64-12.64 6.68 6.68a2 2 0 0 0 2.82 0l2.4-2.4L40 36.4v.6a3 3 0 0 1-3 3H11a3 3 0 0 1-3-3v.28ZM31 18a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z" fill="currentColor" />
          </svg>
          <strong>影像加载失败</strong>
          <span>当前图片暂不可用，请检查影像服务或文件地址</span>
        </div>
        <img
          v-else
          class="preview-image fit-image"
          :class="`fit-${props.fitMode}`"
          :src="props.image.imageUrl"
          :alt="`第 ${props.index + 1} 张影像`"
          @click="openImageViewer"
          @error="markImageFailed(props.image.imageUrl)"
        />
      </div>

      <el-image-viewer
        v-if="isImageViewerOpen"
        ref="imageViewerRef"
        :url-list="props.previewList"
        :initial-index="imageViewerIndex"
        :infinite="false"
        :hide-on-click-modal="false"
        :z-index="3000"
        teleported
        show-progress
        @close="closeImageViewer"
        @switch="handleImageViewerSwitch"
      />

      <div v-if="displayMode === 'scroll'" ref="previewScroller" class="preview-stage">
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
            @error="markImageFailed(imageUrl)"
          >
        </article>
      </div>
    </template>
    <el-empty v-else :description="props.emptyDescription" />
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
  border-radius: 12px;
}

.single-stage {
  display: grid;
  place-items: center;
  overflow: hidden;
}

.preview-image {
  display: block;
  height: auto;
  margin: 0 auto;
  background: #fff;
  box-shadow: 0 2px 8px rgb(0 0 0 / 10%);
}

.single-stage .fit-image {
  cursor: zoom-in;
  background: transparent;
  box-shadow: none;
}

.single-stage .fit-image.fit-width {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  height: auto;
  max-height: 100%;
}

.single-stage .fit-image.fit-height {
  width: auto;
  max-width: 100%;
  height: 100%;
  min-height: 0;
  max-height: 100%;
}

.fit-scroll-image.fit-width {
  width: min(100%, 980px);
}

.fit-scroll-image.fit-height {
  width: auto;
  max-width: none;
  height: min(64vh, 720px);
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
  border-radius: 12px;
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

.continuous-page {
  position: relative;
  width: max-content;
  min-width: 100%;
  padding: 8px;
  margin-bottom: 16px;
}

.continuous-page.is-fit-width {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
}

.preview-toolbar {
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 3001;
  display: grid;
  gap: 6px;
  width: min(180px, calc(100vw - 32px));
  min-width: 0;
  padding: 8px;
  background: hsl(var(--card) / 92%);
  border: 1px solid var(--divider);
  border-radius: 10px;
  box-shadow: 0 4px 12px rgb(0 0 0 / 6%);
}

.preview-controls,
.page-navigation {
  display: flex;
  align-items: center;
}

.preview-controls {
  gap: 6px;
  justify-content: space-between;
  width: 100%;
}

.page-navigation {
  flex: none;
  gap: 2px;
}

.preview-type-select {
  width: 100%;
}

.preview-type-select :deep(.el-select__wrapper) {
  min-height: 24px;
}

@media (width <= 720px) {
  .preview-stage {
    padding: 16px;
  }

  .preview-toolbar {
    right: 8px;
    bottom: 8px;
    width: min(180px, calc(100vw - 16px));
  }

  .preview-controls {
    gap: 4px;
  }

  .preview-image-placeholder {
    min-height: 320px;
    padding: 20px;
  }
}
</style>
