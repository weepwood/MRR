<script setup lang="ts">
import type { GalleryImage } from '../types'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { ref, watch } from 'vue'
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
}>()

const imageLoading = ref(false)
const imageFailed = ref(false)
let touchStartX = 0

watch(() => props.image?.imageUrl, () => {
  imageLoading.value = Boolean(props.image?.imageUrl)
  imageFailed.value = false
}, { immediate: true })

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
</script>

<template>
  <div v-loading="props.loading" class="preview-panel" @touchstart.passive="handleTouchStart" @touchend="handleTouchEnd">
    <template v-if="props.image">
      <div class="preview-stage">
        <el-image
          v-if="!imageFailed"
          class="preview-image"
          :src="props.image.imageUrl"
          fit="contain"
          :preview-src-list="props.previewList"
          :initial-index="props.index"
          :preview-teleported="true"
          :hide-on-click-modal="false"
          @load="imageLoading = false"
          @error="imageFailed = true; imageLoading = false"
        />
        <el-empty v-else description="图片无法加载" />
        <div v-if="imageLoading" class="image-loading">
          正在加载影像
        </div>
      </div>
      <div class="preview-bar">
        <div class="preview-info">
          <strong>P{{ props.image.pages ?? '-' }}</strong>
          <span>{{ normalizeText(props.image.filename) }}</span>
          <span class="image-position">{{ props.index + 1 }} / {{ props.total }}</span>
        </div>
        <div class="preview-actions">
          <el-button circle :icon="ArrowLeft" :disabled="props.index === 0" aria-label="上一张影像" @click="emit('navigate', -1)" />
          <el-button circle :icon="ArrowRight" :disabled="props.index >= props.total - 1" aria-label="下一张影像" @click="emit('navigate', 1)" />
          <el-button size="small" :type="props.isSelected ? 'success' : 'default'" @click="emit('toggle')">
            {{ props.isSelected ? '已选' : '选中' }}
          </el-button>
          <div class="type-editor">
            <span>分类</span>
            <el-select
              v-model="currentType"
              :loading="props.savingType"
              size="small"
              style="width: 180px;"
            >
              <el-option
                v-for="item in TYPE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>
        </div>
      </div>
    </template>
    <el-empty v-else description="请选择影像" />
  </div>
</template>

<style scoped>
.preview-panel {
  position: relative;
  display: grid;
  place-items: center;
  height: 100%;
  min-height: 0;
  padding: 16px;
  background: var(--surface-alt);
}

.preview-image {
  width: 100%;
  height: 100%;
  max-height: calc(100vh - 410px);
}

.preview-stage {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.image-loading {
  position: absolute;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--text-secondary);
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 4px;
}

.preview-bar {
  position: absolute;
  right: 16px;
  bottom: 16px;
  left: 16px;
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: hsl(var(--card) / 95%);
  border: 1px solid var(--divider);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgb(0 0 0 / 6%), 0 1px 3px rgb(0 0 0 / 4%);
}

@supports (backdrop-filter: blur(8px)) {
  .preview-bar {
    backdrop-filter: blur(8px);
  }
}

.preview-info {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 13px;
  color: var(--text-primary);
}

.preview-info strong {
  margin-right: 2px;
  color: var(--text-primary);
}

.image-position {
  padding-left: 8px;
  color: var(--text-secondary);
  border-left: 1px solid var(--divider);
}

.preview-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.type-editor {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 13px;
  color: var(--text-secondary);
}

@media (width <= 720px) {
  .preview-bar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
