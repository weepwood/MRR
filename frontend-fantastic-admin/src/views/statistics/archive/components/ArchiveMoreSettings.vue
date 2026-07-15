<script setup lang="ts">
import type { ArchivePreviewMode } from '@/utils/system-settings'
import { MoreFilled } from '@element-plus/icons-vue'

const props = defineProps<{
  previewMode: ArchivePreviewMode
  thumbnailSize: number
  autoFit: boolean
  hasLocalPreferences: boolean
}>()

const emit = defineEmits<{
  'update:previewMode': [mode: ArchivePreviewMode]
  'update:thumbnailSize': [size: number]
  'update:autoFit': [enabled: boolean]
  'reset': []
}>()

function updateAutoFit(value: unknown) {
  emit('update:autoFit', value === true)
}

function updateThumbnailSize(value: number | number[]) {
  emit('update:thumbnailSize', Array.isArray(value) ? value[0] ?? props.thumbnailSize : value)
}
</script>

<template>
  <div class="archive-more-settings-root">
    <el-popover placement="bottom-end" :width="272" trigger="click" popper-class="archive-more-settings-popper">
      <template #reference>
        <el-button circle :icon="MoreFilled" aria-label="更多显示设置" title="更多显示设置" />
      </template>

      <section class="archive-more-settings" aria-label="本地显示设置">
        <div class="settings-heading">
          <strong>显示设置</strong>
          <span>仅当前浏览器</span>
        </div>

        <div class="settings-row">
          <span>预览方式</span>
          <el-segmented
            :model-value="props.previewMode"
            size="small"
            :options="[
              { label: '单页', value: 'single' },
              { label: '滚动', value: 'scroll' },
            ]"
            @update:model-value="emit('update:previewMode', $event as ArchivePreviewMode)"
          />
        </div>

        <div class="settings-row">
          <span>自动适应</span>
          <el-switch
            :model-value="props.autoFit"
            aria-label="自动适应预览区域"
            @update:model-value="updateAutoFit($event)"
          />
        </div>

        <div class="settings-slider-row">
          <div class="settings-row">
            <span>缩略图宽度</span>
            <span class="settings-value">{{ props.thumbnailSize }} px</span>
          </div>
          <el-slider
            :model-value="props.thumbnailSize"
            :min="160"
            :max="320"
            :step="10"
            aria-label="缩略图宽度"
            @update:model-value="updateThumbnailSize($event)"
          />
        </div>

        <el-button text size="small" :disabled="!props.hasLocalPreferences" @click="emit('reset')">
          恢复系统默认
        </el-button>
      </section>
    </el-popover>
  </div>
</template>

<style scoped>
:global(.archive-more-settings-popper) {
  z-index: 2020 !important;
}

.archive-more-settings {
  display: grid;
  gap: 14px;
}

.settings-heading,
.settings-row {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.settings-heading strong {
  font-size: 14px;
  color: var(--text-primary);
}

.settings-heading span,
.settings-value {
  font-size: 12px;
  color: var(--text-secondary);
}

.settings-slider-row {
  display: grid;
  gap: 2px;
}

.settings-slider-row :deep(.el-slider) {
  margin: 0 8px;
}

.archive-more-settings > .el-button {
  justify-self: start;
  padding: 0;
}
</style>
