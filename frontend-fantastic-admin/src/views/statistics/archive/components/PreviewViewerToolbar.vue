<script setup lang="ts">
import { FullScreen, RefreshLeft, RefreshRight, ScaleToOriginal, ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import { ref } from 'vue'

defineOptions({ name: 'PreviewViewerToolbar' })

const props = defineProps<{
  /** Element 查看器 toolbar 插槽给出的 handleActions */
  actions: (action: 'zoomIn' | 'zoomOut' | 'anticlockwise' | 'clockwise') => void
  /** 插槽给出的 reset，内部就是 toggleMode（适应窗口 / 原始尺寸） */
  reset: () => void
  activeIndex: number
  total: number
}>()

/** 插槽不暴露当前模式，自己跟一份，只用于切换按钮的图标与提示文案。 */
const fitMode = ref(true)

function toggleFitMode() {
  props.reset()
  fitMode.value = !fitMode.value
}
</script>

<template>
  <!--
    替代 Element 默认的底部工具条：默认页码是独立漂浮的节点（bottom: 90px），
    既压在影像上又脱离工具条。这里把页码放进胶囊末尾，与缩放/旋转图标同一行。
    样式挂在 element-plus-overrides.css 的查看器配色段里（.el-image-viewer__actions__divider）。
  -->
  <button type="button" class="viewer-action" title="缩小" aria-label="缩小" @keydown.space.stop @keydown.enter.stop @click="props.actions('zoomOut')">
    <el-icon><ZoomOut /></el-icon>
  </button>
  <button type="button" class="viewer-action" title="放大" aria-label="放大" @keydown.space.stop @keydown.enter.stop @click="props.actions('zoomIn')">
    <el-icon><ZoomIn /></el-icon>
  </button>
  <i class="el-image-viewer__actions__divider" aria-hidden="true" />
  <button type="button" class="viewer-action" :title="fitMode ? '按原始尺寸查看' : '适应窗口'" :aria-label="fitMode ? '按原始尺寸查看' : '适应窗口'" @keydown.space.stop @keydown.enter.stop @click="toggleFitMode">
    <el-icon><component :is="fitMode ? FullScreen : ScaleToOriginal" /></el-icon>
  </button>
  <i class="el-image-viewer__actions__divider" aria-hidden="true" />
  <button type="button" class="viewer-action" title="向左旋转" aria-label="向左旋转" @keydown.space.stop @keydown.enter.stop @click="props.actions('anticlockwise')">
    <el-icon><RefreshLeft /></el-icon>
  </button>
  <button type="button" class="viewer-action" title="向右旋转" aria-label="向右旋转" @keydown.space.stop @keydown.enter.stop @click="props.actions('clockwise')">
    <el-icon><RefreshRight /></el-icon>
  </button>
  <span class="viewer-progress">{{ props.activeIndex + 1 }} / {{ props.total }}</span>
</template>

<style scoped>
.viewer-action {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  font-size: 20px;
  color: inherit;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--mrr-radius-sm);
  transition: color var(--mrr-motion-feedback) var(--mrr-ease-out), background-color var(--mrr-motion-feedback) var(--mrr-ease-out);
}

.viewer-action:hover {
  color: var(--mrr-primary);
  background: var(--mrr-control-bg-hover);
}

.viewer-action:focus-visible {
  outline: 2px solid var(--mrr-ring);
  outline-offset: 1px;
}

.viewer-progress {
  padding-left: var(--mrr-space-2);
  font-size: var(--mrr-font-size-sm);
  font-variant-numeric: tabular-nums;
  color: var(--text-secondary);
  white-space: nowrap;
  user-select: none;
  border-left: 1px solid var(--divider);
}
</style>
