<script setup lang="ts">
defineOptions({ name: 'MrrFilterBar' })

const props = withDefaults(defineProps<{
  compact?: boolean
}>(), {
  compact: false,
})

const slots = useSlots()
</script>

<template>
  <div class="mrr-filter-bar" :class="{ 'mrr-filter-bar--compact': props.compact }">
    <div class="mrr-filter-bar__fields">
      <slot />
    </div>
    <div v-if="slots.actions" class="mrr-filter-bar__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.mrr-filter-bar {
  display: flex;
  gap: var(--mrr-space-3);
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  padding: 12px 16px;
  background: color-mix(in srgb, var(--surface-muted) 72%, var(--surface));
  border: 1px solid var(--divider);
  border-radius: var(--mrr-radius-lg);
}

.mrr-filter-bar--compact {
  padding: 10px 12px;
}

.mrr-filter-bar__fields {
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.mrr-filter-bar__actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
}

.mrr-filter-bar__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

@media (width <= 760px) {
  .mrr-filter-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .mrr-filter-bar__fields,
  .mrr-filter-bar__actions {
    width: 100%;
  }

  .mrr-filter-bar__actions :deep(.el-button) {
    flex: 1 1 120px;
  }
}
</style>
