<script setup lang="ts">
import type { ArchiveFitMode, ArchiveTypeDisplayMode } from '../composables/useArchiveLocalPreferences'
import type { ViewMode } from '../types'
import type { ArchivePreviewMode } from '@/utils/system-settings'
import { MoreFilled } from '@element-plus/icons-vue'

const props = defineProps<{
  viewMode: ViewMode
  previewMode: ArchivePreviewMode
  typeDisplayMode: ArchiveTypeDisplayMode
  thumbnailSize: number
  fitMode: ArchiveFitMode
  hideScrollbars: boolean
  departmentColorsEnabled: boolean
  hasLocalPreferences: boolean
}>()

const emit = defineEmits<{
  'update:viewMode': [mode: ViewMode]
  'update:previewMode': [mode: ArchivePreviewMode]
  'update:typeDisplayMode': [mode: ArchiveTypeDisplayMode]
  'update:thumbnailSize': [size: number]
  'update:fitMode': [mode: ArchiveFitMode]
  'update:hideScrollbars': [enabled: boolean]
  'update:departmentColorsEnabled': [enabled: boolean]
  'reset': []
}>()

function updateThumbnailSize(value: number | number[]) {
  emit('update:thumbnailSize', Array.isArray(value) ? value[0] ?? props.thumbnailSize : value)
}

function updateHideScrollbars(value: unknown) {
  emit('update:hideScrollbars', value === true)
}

function updateDepartmentColorsEnabled(value: unknown) {
  emit('update:departmentColorsEnabled', value === true)
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
          <span>仅保存到当前浏览器</span>
        </div>

        <div class="settings-row">
          <span>缩略图展示</span>
          <el-segmented
            :model-value="props.viewMode"
            size="small"
            :options="[
              { label: '缩略图', value: 'thumb' },
              { label: '列表', value: 'list' },
            ]"
            @update:model-value="emit('update:viewMode', $event as ViewMode)"
          />
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
          <span>分类展示</span>
          <el-segmented
            :model-value="props.typeDisplayMode"
            size="small"
            :options="[
              { label: '双栏', value: 'double-column' },
              { label: '单栏', value: 'single-column' },
            ]"
            @update:model-value="emit('update:typeDisplayMode', $event as ArchiveTypeDisplayMode)"
          />
        </div>

        <div class="settings-row">
          <span>适应方向</span>
          <el-segmented
            :model-value="props.fitMode"
            size="small"
            :options="[
              { label: '高度', value: 'height' },
              { label: '宽度', value: 'width' },
            ]"
            @update:model-value="emit('update:fitMode', $event as ArchiveFitMode)"
          />
        </div>

        <div class="settings-row">
          <span>科室颜色</span>
          <el-switch
            :model-value="props.departmentColorsEnabled"
            aria-label="按科室显示病案背景色"
            @update:model-value="updateDepartmentColorsEnabled($event)"
          />
        </div>

        <div class="settings-row">
          <span>隐藏滚动条</span>
          <el-switch
            :model-value="props.hideScrollbars"
            aria-label="隐藏档案袋滚动条"
            @update:model-value="updateHideScrollbars($event)"
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
