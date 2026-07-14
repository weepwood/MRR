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
  padding: 13px 14px;
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--surface) 74%, var(--surface-muted)) 0%,
    color-mix(in srgb, var(--surface-muted) 72%, var(--surface)) 100%
  );
  border: 1px solid color-mix(in srgb, var(--mrr-control-border) 82%, var(--divider));
  border-radius: 14px;
  box-shadow: 0 1px 2px rgb(15 23 42 / 3%), inset 0 1px 0 rgb(255 255 255 / 55%);
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.mrr-filter-bar:focus-within {
  border-color: color-mix(in srgb, var(--color-primary) 22%, var(--divider));
  box-shadow: 0 4px 18px rgb(15 23 42 / 5%), inset 0 1px 0 rgb(255 255 255 / 62%);
}

.mrr-filter-bar--compact {
  padding: 10px 12px;
  border-radius: var(--mrr-radius-card);
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
  padding-left: var(--mrr-space-3);
  border-left: 1px solid var(--divider);
}

.mrr-filter-bar__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.mrr-filter-bar :deep(.el-input__wrapper),
.mrr-filter-bar :deep(.el-select__wrapper) {
  background: var(--surface);
}

@media (width <= 760px) {
  .mrr-filter-bar {
    gap: var(--mrr-space-3);
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
    border-top: 1px solid var(--divider);
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
