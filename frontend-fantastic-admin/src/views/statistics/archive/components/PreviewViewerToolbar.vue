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
  <el-icon class="viewer-action" title="缩小" @click="props.actions('zoomOut')">
    <ZoomOut />
  </el-icon>
  <el-icon class="viewer-action" title="放大" @click="props.actions('zoomIn')">
    <ZoomIn />
  </el-icon>
  <i class="el-image-viewer__actions__divider" />
  <el-icon class="viewer-action" :title="fitMode ? '按原始尺寸查看' : '适应窗口'" @click="toggleFitMode">
    <component :is="fitMode ? FullScreen : ScaleToOriginal" />
  </el-icon>
  <i class="el-image-viewer__actions__divider" />
  <el-icon class="viewer-action" title="向左旋转" @click="props.actions('anticlockwise')">
    <RefreshLeft />
  </el-icon>
  <el-icon class="viewer-action" title="向右旋转" @click="props.actions('clockwise')">
    <RefreshRight />
  </el-icon>
  <span class="viewer-progress">{{ props.activeIndex + 1 }} / {{ props.total }}</span>
</template>

<style scoped>
.viewer-action {
  cursor: pointer;
  transition: color var(--mrr-motion-feedback) var(--mrr-ease-out);
}

.viewer-action:hover {
  color: var(--mrr-primary);
}

.viewer-progress {
  padding-left: 16px;
  margin-left: -8px;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  color: var(--text-secondary);
  white-space: nowrap;
  user-select: none;
  border-left: 1px solid var(--divider);
}
</style>
