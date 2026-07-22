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
  padding: 11px 12px;
  color: var(--mrr-card-foreground);
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-lg);
  box-shadow: var(--mrr-shadow-xs);
  transition: border-color 140ms ease, box-shadow 140ms ease;
}

.mrr-filter-bar:focus-within {
  border-color: color-mix(in srgb, var(--mrr-ring) 46%, var(--mrr-border));
  box-shadow: var(--mrr-shadow-xs), 0 0 0 3px color-mix(in srgb, var(--mrr-ring) 10%, transparent);
}

.mrr-filter-bar--compact {
  padding: 8px 9px;
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
  border-left: 1px solid var(--mrr-shell-divider);
}

.mrr-filter-bar__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.mrr-filter-bar :deep(.el-input__wrapper),
.mrr-filter-bar :deep(.el-select__wrapper) {
  height: var(--mrr-control-height);
  background: color-mix(in srgb, var(--mrr-muted) 18%, var(--mrr-card));
}

.mrr-filter-bar :deep(.el-input--small .el-input__wrapper),
.mrr-filter-bar :deep(.el-select--small .el-select__wrapper) {
  height: var(--mrr-control-height-compact);
}

.mrr-filter-bar :deep(.el-input--large .el-input__wrapper),
.mrr-filter-bar :deep(.el-select--large .el-select__wrapper) {
  height: var(--mrr-control-height-large);
}

.mrr-filter-bar :deep(.el-input__wrapper.is-focus),
.mrr-filter-bar :deep(.el-select__wrapper.is-focused) {
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
    border-top: 1px solid var(--mrr-shell-divider);
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
