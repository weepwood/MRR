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
  padding: 12px;
  color: var(--mrr-card-foreground);
  background: color-mix(in srgb, var(--mrr-muted) 46%, var(--mrr-card));
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-lg);
  box-shadow: none;
  transition: border-color 120ms ease, box-shadow 120ms ease;
}

.mrr-filter-bar:focus-within {
  border-color: color-mix(in srgb, var(--mrr-ring) 52%, var(--mrr-border));
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--mrr-ring) 12%, transparent);
}

.mrr-filter-bar--compact {
  padding: 9px;
  border-radius: var(--mrr-radius-md);
}

.mrr-filter-bar__fields {
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  min-width: 0;
}

.mrr-filter-bar__actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  padding-left: var(--mrr-space-3);
  border-left: 1px solid var(--mrr-border);
}

.mrr-filter-bar__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.mrr-filter-bar :deep(.el-input__wrapper),
.mrr-filter-bar :deep(.el-select__wrapper) {
  background: var(--mrr-card);
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

  .mrr-filter-bar__actions {
    padding-top: var(--mrr-space-3);
    padding-left: 0;
    border-top: 1px solid var(--mrr-border);
    border-left: 0;
  }

  .mrr-filter-bar__actions :deep(.el-button) {
    flex: 1 1 120px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .mrr-filter-bar {
    transition: none;
  }
}
</style>
