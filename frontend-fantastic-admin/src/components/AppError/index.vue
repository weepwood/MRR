<script setup lang="ts">
import { RefreshCw as Refresh } from 'lucide-vue-next'

defineOptions({ name: 'AppError' })

withDefaults(defineProps<{
  message?: string
  retryText?: string
  /** compact：用于弹层、图片错误插槽等受限空间，缩小留白与图标，不改变语义。 */
  size?: 'default' | 'compact'
}>(), {
  message: '加载失败',
  retryText: '重试',
  size: 'default',
})

const emit = defineEmits<{
  retry: []
}>()

function handleRetry() {
  emit('retry')
}
</script>

<template>
  <div class="app-error mrr-content-enter" :class="{ 'is-compact': size === 'compact' }" role="alert">
    <div class="app-error-icon i-lucide:triangle-alert mrr-status-pop" aria-hidden="true" />
    <p class="app-error-text">
      {{ message }}
    </p>
    <el-button type="primary" :size="size === 'compact' ? 'small' : undefined" :icon="Refresh" @click="handleRetry">
      {{ retryText }}
    </el-button>
  </div>
</template>

<style scoped>
.app-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;

  /* 空态/错误态常出现在表格面板内部，64px 上下留白会把分页条推出首屏；与骨架屏高度更接近。 */
  min-width: 0;
  padding: var(--mrr-space-8) var(--mrr-space-4);
  text-align: center;
}

.app-error-icon {
  margin-bottom: var(--mrr-space-3);
  font-size: 36px;
  color: var(--el-color-warning);
  transform-origin: center;
}

.app-error-text {
  max-width: 100%;
  margin: 0 0 var(--mrr-space-4);
  font-size: var(--mrr-font-size-sm);
  line-height: 1.6;
  color: var(--el-text-color-regular);
  overflow-wrap: anywhere;
}

.app-error.is-compact {
  padding: var(--mrr-space-5) var(--mrr-space-3);
}

.app-error.is-compact .app-error-icon {
  margin-bottom: var(--mrr-space-2);
  font-size: 26px;
}

.app-error.is-compact .app-error-text {
  margin-bottom: var(--mrr-space-3);
  font-size: var(--mrr-font-size-sm);
}

.app-error.is-compact .app-error-text:last-child {
  margin-bottom: 0;
}
</style>
