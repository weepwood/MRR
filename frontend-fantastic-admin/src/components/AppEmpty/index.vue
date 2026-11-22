<script setup lang="ts">
defineOptions({ name: 'AppEmpty' })

withDefaults(defineProps<{
  description?: string
  icon?: string
  actionText?: string
  actionIcon?: string
  /** compact：用于弹层、下拉、小面板等受限空间，缩小留白与图标，不改变语义。 */
  size?: 'default' | 'compact'
}>(), {
  description: '暂无数据',
  icon: 'i-lucide:inbox',
  size: 'default',
})

const emit = defineEmits<{
  action: []
}>()

function handleAction() {
  emit('action')
}
</script>

<template>
  <!-- 空态是查询结果的一部分，需要被读屏播报；AppError 用 role="alert"，空态用 status 更轻。 -->
  <div class="app-empty mrr-content-enter" :class="{ 'is-compact': size === 'compact' }" role="status" aria-live="polite">
    <div class="app-empty-icon mrr-status-pop" :class="icon" aria-hidden="true" />
    <p class="app-empty-text">
      {{ description }}
    </p>
    <el-button v-if="actionText" type="primary" :size="size === 'compact' ? 'small' : undefined" :icon="actionIcon" @click="handleAction">
      {{ actionText }}
    </el-button>
  </div>
</template>

<style scoped>
.app-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;

  /* 空态/错误态常出现在表格面板内部，64px 上下留白会把分页条推出首屏；与骨架屏高度更接近。 */
  padding: 48px var(--mrr-space-4);
  text-align: center;
}

.app-empty-icon {
  margin-bottom: 16px;
  font-size: 48px;
  color: var(--el-text-color-placeholder);
  transform-origin: center;
}

.app-empty-text {
  margin: 0 0 20px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.app-empty.is-compact {
  padding: var(--mrr-space-5) var(--mrr-space-3);
}

.app-empty.is-compact .app-empty-icon {
  margin-bottom: var(--mrr-space-2);
  font-size: 32px;
}

.app-empty.is-compact .app-empty-text {
  margin-bottom: var(--mrr-space-3);
  font-size: 13px;
}

.app-empty.is-compact .app-empty-text:last-child {
  margin-bottom: 0;
}
</style>
