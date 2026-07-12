<script setup lang="ts">
import type { GalleryImage } from '../types'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message-box/style/css'
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { normalizeText, TYPE_OPTIONS } from '../constants'

defineOptions({ name: 'PreviewPanel' })

const props = defineProps<{
  image: GalleryImage | null
  previewList: string[]
  index: number
  total: number
  isSelected: boolean
  savingType?: boolean
  loading?: boolean
}>()

const emit = defineEmits<{
  toggle: []
  saveType: [type: number]
  navigate: [delta: number]
  select: [index: number]
}>()

const previewScroller = ref<HTMLElement | null>(null)
const pageRefs = ref<(HTMLElement | null)[]>([])
const displayMode = ref<'single' | 'scroll'>('single')
const pendingType = ref(0)
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

function syncCurrentPage(index: number) {
  const target = pageRefs.value[index]
  if (!target) {
    return
  }
  target.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function onImageError(event: Event) {
  const target = event.target as HTMLImageElement
  target.style.opacity = '0.35'
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
  if (displayMode.value === 'scroll') {
    nextTick(() => syncCurrentPage(index))
  }
})

watch(() => props.image?.btype, () => {
  pendingType.value = currentType.value
}, { immediate: true })

watch(displayMode, () => {
  observePages()
})

onMounted(observePages)

onUnmounted(() => {
  pageObserver?.disconnect()
  pageObserver = null
})
</script>

<template>
  <div v-loading="props.loading" class="preview-panel" @touchstart.passive="handleTouchStart" @touchend="handleTouchEnd">
    <template v-if="props.image">
      <el-segmented
        v-model="displayMode"
        class="display-mode"
        size="small"
        :options="[
          { label: '单页', value: 'single' },
          { label: '滚动', value: 'scroll' },
        ]"
      />

      <div v-if="displayMode === 'single'" class="preview-stage single-stage">
        <img class="preview-image" :src="props.image.imageUrl" :alt="`第 ${props.index + 1} 张影像`" @error="onImageError">
      </div>

      <div v-else ref="previewScroller" class="preview-stage">
        <article
          v-for="(imageUrl, pageIndex) in props.previewList"
          :key="imageUrl || pageIndex"
          :ref="(element: any) => { pageRefs[pageIndex] = element }"
          class="continuous-page"
          :class="{ active: pageIndex === props.index }"
          :data-index="pageIndex"
        >
          <img class="preview-image" :src="imageUrl" :alt="`第 ${pageIndex + 1} 张影像`" loading="lazy" @error="onImageError">
        </article>
      </div>

      <el-button
        class="image-nav image-nav-prev"
        circle
        :icon="ArrowLeft"
        :disabled="props.index === 0"
        aria-label="上一张影像"
        @click="emit('navigate', -1)"
      />
      <el-button
        class="image-nav image-nav-next"
        circle
        :icon="ArrowRight"
        :disabled="props.index >= props.total - 1"
        aria-label="下一张影像"
        @click="emit('navigate', 1)"
      />

      <div class="preview-info">
        <strong>P{{ props.image.pages ?? '-' }}</strong>
        <span>{{ normalizeText(props.image.filename) }}</span>
        <span class="image-position">{{ props.index + 1 }} / {{ props.total }}</span>
      </div>

      <el-button class="selection-fab" size="small" :type="props.isSelected ? 'success' : 'default'" @click="emit('toggle')">
        {{ props.isSelected ? '已选' : '选中' }}
      </el-button>

      <div class="type-fab">
        <el-select v-model="pendingType" :loading="props.savingType" size="small" @change="confirmType">
          <el-option
            v-for="item in TYPE_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
    </template>
    <el-empty v-else description="请选择影像" />
  </div>
</template>

<style scoped>
.preview-panel {
  position: relative;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.preview-image {
  display: block;
  width: min(100%, 980px);
  height: auto;
  margin: 0 auto;
  background: #fff;
  box-shadow: 0 2px 8px rgb(0 0 0 / 10%);
}

.preview-stage {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: 16px 68px 72px;
  overflow-y: auto;
  background: var(--surface-alt);
  border: 1px solid var(--divider);
  border-radius: 12px;
}

.single-stage {
  display: grid;
  place-items: center;
  overflow: hidden;
}

.single-stage .preview-image {
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.continuous-page {
  position: relative;
  padding: 8px;
  margin-bottom: 16px;
  border: 1px solid transparent;
  border-radius: 8px;
}

.continuous-page.active {
  border-color: hsl(var(--primary) / 45%);
  box-shadow: 0 0 0 2px hsl(var(--primary) / 12%);
}

.image-nav,
.selection-fab,
.type-fab,
.preview-info,
.display-mode {
  position: absolute;
}

.image-nav {
  top: 50%;
  z-index: 2;
  transform: translateY(-50%);
}

.image-nav-prev {
  left: 16px;
}

.image-nav-next {
  right: 16px;
}

.preview-info {
  bottom: 16px;
  left: 16px;
  display: flex;
  gap: 8px;
  align-items: center;
  max-width: calc(100% - 280px);
  padding: 7px 10px;
  font-size: 13px;
  color: var(--text-primary);
  background: hsl(var(--card) / 95%);
  border: 1px solid var(--divider);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgb(0 0 0 / 6%), 0 1px 3px rgb(0 0 0 / 4%);
}

.selection-fab {
  top: 16px;
  right: 16px;
}

.display-mode {
  top: 16px;
  left: 16px;
  z-index: 2;
}

.type-fab {
  right: 16px;
  bottom: 16px;
  display: flex;
  gap: 6px;
  align-items: center;
  width: 180px;
}

.type-fab :deep(.el-select) {
  flex: 1;
}

.preview-info strong {
  margin-right: 4px;
  padding: 2px 6px;
  font-size: 12px;
  color: var(--text-primary);
  background: var(--surface-alt);
  border-radius: 4px;
}

.image-position {
  padding-left: 8px;
  color: var(--text-secondary);
  border-left: 1px solid var(--divider);
}

.preview-info > span:not(.image-position) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (width <= 720px) {
  .preview-stage {
    padding-right: 52px;
    padding-left: 52px;
  }

  .preview-info {
    max-width: calc(100% - 32px);
  }

  .selection-fab {
    right: 16px;
    bottom: 62px;
    top: auto;
  }
}
</style>
