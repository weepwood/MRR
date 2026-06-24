<script setup lang="ts">
import type { GalleryImage } from '../types'
import { normalizeText, TYPE_OPTIONS } from '../constants'

defineOptions({ name: 'PreviewPanel' })

const props = defineProps<{
  image: GalleryImage | null
  previewList: string[]
  index: number
  isSelected: boolean
  savingType?: boolean
  loading?: boolean
}>()

const emit = defineEmits<{
  toggle: []
  saveType: [type: number]
}>()

const currentType = computed({
  get: () => Number(props.image?.btype || 0),
  set: (value: number) => emit('saveType', value),
})
</script>

<template>
  <div v-loading="props.loading" class="preview-panel">
    <template v-if="props.image">
      <el-image
        class="preview-image"
        :src="props.image.imageUrl"
        fit="contain"
        :preview-src-list="props.previewList"
        :initial-index="props.index"
        :preview-teleported="true"
        :hide-on-click-modal="false"
      />
      <div class="preview-bar">
        <div class="preview-info">
          <strong>P{{ props.image.pages ?? '-' }}</strong>
          <span>{{ normalizeText(props.image.filename) }}</span>
        </div>
        <div class="preview-actions">
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
  max-height: calc(100vh - 380px);
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
  backdrop-filter: blur(8px);
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
