<script setup lang="ts">
import type {
  ArchiveFitMode,
  ArchiveLayoutMode,
  ArchiveScrollbarMode,
  ArchiveTypeDisplayMode,
  ArchiveWallDensity,
} from '../composables/useArchiveLocalPreferences'
import type { ViewMode } from '../types'
import type { ArchivePreviewMode } from '@/utils/system-settings'
import { MoreFilled } from '@element-plus/icons-vue'

const props = defineProps<{
  viewMode: ViewMode
  previewMode: ArchivePreviewMode
  typeDisplayMode: ArchiveTypeDisplayMode
  thumbnailSize: number
  fitMode: ArchiveFitMode
  previewScale: number
  scrollbarMode: ArchiveScrollbarMode
  departmentColorsEnabled: boolean
  layoutMode: ArchiveLayoutMode
  wallCardWidth: number
  wallDensity: ArchiveWallDensity
  wallShowMeta: boolean
  hasLocalPreferences: boolean
}>()

const emit = defineEmits<{
  'update:viewMode': [mode: ViewMode]
  'update:previewMode': [mode: ArchivePreviewMode]
  'update:typeDisplayMode': [mode: ArchiveTypeDisplayMode]
  'update:thumbnailSize': [size: number]
  'update:fitMode': [mode: ArchiveFitMode]
  'update:previewScale': [scale: number]
  'update:scrollbarMode': [mode: ArchiveScrollbarMode]
  'update:departmentColorsEnabled': [enabled: boolean]
  'update:layoutMode': [mode: ArchiveLayoutMode]
  'update:wallCardWidth': [size: number]
  'update:wallDensity': [density: ArchiveWallDensity]
  'update:wallShowMeta': [enabled: boolean]
  'reset': []
}>()

function updateThumbnailSize(value: number | number[]) {
  emit('update:thumbnailSize', Array.isArray(value) ? value[0] ?? props.thumbnailSize : value)
}

function updateWallCardWidth(value: number | number[]) {
  emit('update:wallCardWidth', Array.isArray(value) ? value[0] ?? props.wallCardWidth : value)
}

function updateDepartmentColorsEnabled(value: unknown) {
  emit('update:departmentColorsEnabled', value === true)
}

function updateWallShowMeta(value: unknown) {
  emit('update:wallShowMeta', value === true)
}
</script>

<template>
  <div class="archive-more-settings-root">
    <el-popover placement="bottom-end" :width="292" trigger="click" popper-class="archive-more-settings-popper">
      <template #reference>
        <el-button circle :icon="MoreFilled" aria-label="更多显示设置" title="更多显示设置" />
      </template>

      <section class="archive-more-settings" aria-label="本地显示设置">
        <div class="settings-heading">
          <strong>显示设置</strong>
          <span>仅保存到当前浏览器</span>
        </div>

        <div class="settings-row settings-row--stacked">
          <span>工作区布局</span>
          <el-segmented
            :model-value="props.layoutMode"
            size="small"
            :options="[
              { label: '标准浏览', value: 'standard' },
              { label: '全景平铺', value: 'wall' },
            ]"
            @update:model-value="emit('update:layoutMode', $event as ArchiveLayoutMode)"
          />
        </div>

        <template v-if="props.layoutMode === 'standard'">
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

          <template v-if="props.previewMode === 'scroll'">
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

            <div class="settings-slider-row">
              <div class="settings-row">
                <span>缩放大小</span>
                <span class="settings-value">{{ props.previewScale }}%</span>
              </div>
              <el-slider
                :model-value="props.previewScale"
                :min="50"
                :max="150"
                :step="5"
                tooltip-class="archive-preview-scale-tooltip"
                aria-label="滚动预览缩放大小"
                @update:model-value="emit('update:previewScale', Array.isArray($event) ? $event[0] ?? props.previewScale : $event)"
              />
            </div>
          </template>

          <div class="settings-slider-row">
            <div class="settings-row">
              <span>缩略图宽度</span>
              <span class="settings-value">{{ props.thumbnailSize }} px</span>
            </div>
            <el-slider
              :model-value="props.thumbnailSize"
              :min="160"
              :max="480"
              :step="10"
              tooltip-class="archive-thumbnail-slider-tooltip"
              aria-label="缩略图宽度"
              @update:model-value="updateThumbnailSize($event)"
            />
          </div>
        </template>

        <template v-else>
          <div class="settings-row settings-row--stacked">
            <span>平铺密度</span>
            <el-segmented
              :model-value="props.wallDensity"
              size="small"
              :options="[
                { label: '紧凑', value: 'compact' },
                { label: '舒适', value: 'comfortable' },
                { label: '宽松', value: 'spacious' },
              ]"
              @update:model-value="emit('update:wallDensity', $event as ArchiveWallDensity)"
            />
          </div>

          <div class="settings-slider-row">
            <div class="settings-row">
              <span>图片宽度</span>
              <span class="settings-value">{{ props.wallCardWidth }} px</span>
            </div>
            <el-slider
              :model-value="props.wallCardWidth"
              :min="160"
              :max="420"
              :step="10"
              tooltip-class="archive-wall-width-tooltip"
              aria-label="平铺图片宽度"
              @update:model-value="updateWallCardWidth($event)"
            />
          </div>

          <div class="settings-row">
            <span>图片信息</span>
            <el-switch
              :model-value="props.wallShowMeta"
              aria-label="显示平铺图片页码和分类"
              @update:model-value="updateWallShowMeta($event)"
            />
          </div>
        </template>

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
          <span>科室颜色</span>
          <el-switch
            :model-value="props.departmentColorsEnabled"
            aria-label="按科室显示病案背景色"
            @update:model-value="updateDepartmentColorsEnabled($event)"
          />
        </div>

        <div class="settings-row settings-row--stacked">
          <span>滚动条</span>
          <el-segmented
            :model-value="props.scrollbarMode"
            size="small"
            :options="[
              { label: '隐藏', value: 'hidden' },
              { label: '半隐藏', value: 'semi-hidden' },
              { label: '不隐藏', value: 'visible' },
            ]"
            @update:model-value="emit('update:scrollbarMode', $event as ArchiveScrollbarMode)"
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

:global(.archive-thumbnail-slider-tooltip),
:global(.archive-preview-scale-tooltip),
:global(.archive-wall-width-tooltip) {
  z-index: 2021 !important;
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

.settings-row--stacked {
  display: grid;
  gap: 8px;
  justify-content: stretch;
}

.settings-row--stacked :deep(.el-segmented) {
  width: 100%;
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
  width: calc(100% - 16px);
  margin: 0 auto;
}

.archive-more-settings > .el-button {
  justify-self: start;
  padding: 0;
}
</style>
