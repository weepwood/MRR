<script setup lang="ts">
defineOptions({ name: 'MrrSelectionBar' })

const props = withDefaults(defineProps<{
  count: number
  total?: number
  label?: string
}>(), {
  label: '已选择',
})

const emit = defineEmits<{
  clear: []
}>()

const summary = computed(() => {
  const selected = Number(props.count || 0).toLocaleString('zh-CN')
  if (props.total === undefined) {
    return `${props.label} ${selected} 条`
  }
  return `${props.label} ${selected} / ${Number(props.total || 0).toLocaleString('zh-CN')} 条`
})
</script>

<template>
  <div v-if="props.count > 0" class="mrr-selection-bar">
    <div class="mrr-selection-bar__summary" role="status" aria-live="polite">
      <span class="mrr-selection-bar__indicator" aria-hidden="true" />
      <strong>{{ summary }}</strong>
      <slot name="description" />
    </div>
    <div class="mrr-selection-bar__actions">
      <slot />
      <el-button text @click="emit('clear')">
        清除选择
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.mrr-selection-bar {
  display: flex;
  gap: var(--mrr-space-3);
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  padding: 10px var(--mrr-space-4);
  color: var(--mrr-card-foreground);
  background: color-mix(in srgb, var(--mrr-primary) 7%, var(--mrr-card));
  border-top: 1px solid color-mix(in srgb, var(--mrr-primary) 16%, var(--mrr-border));
  border-bottom: 1px solid color-mix(in srgb, var(--mrr-primary) 16%, var(--mrr-border));
}

.mrr-selection-bar__summary,
.mrr-selection-bar__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
}

.mrr-selection-bar__summary {
  min-width: 0;
  font-size: 13px;
}

.mrr-selection-bar__summary :deep(*) {
  color: var(--mrr-muted-foreground);
}

.mrr-selection-bar__indicator {
  width: 8px;
  height: 8px;
  background: var(--mrr-primary);
  border-radius: 50%;
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--mrr-primary) 12%, transparent);
}

.mrr-selection-bar__actions {
  flex: 0 0 auto;
  justify-content: flex-end;
}

.mrr-selection-bar__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

@media (width <= 760px) {
  .mrr-selection-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .mrr-selection-bar__actions {
    justify-content: flex-start;
  }
}
</style>
