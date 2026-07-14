<script setup lang="ts">
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'

defineOptions({ name: 'MrrDataTablePanel' })

const props = defineProps<{
  title: string
  description?: string
  icon?: string
  count?: number
  countLabel?: string
}>()

const slots = useSlots()
const hasActions = computed(() => props.count !== undefined || Boolean(slots.actions))
const countText = computed(() => Number(props.count ?? 0).toLocaleString('zh-CN'))
</script>

<template>
  <MrrSectionCard
    :title="props.title"
    :description="props.description"
    :icon="props.icon"
    body-padding="none"
  >
    <template v-if="hasActions" #actions>
      <span v-if="props.count !== undefined" class="mrr-data-table-panel__count">
        {{ props.countLabel || '共' }} {{ countText }} 条
      </span>
      <slot name="actions" />
    </template>

    <div v-if="slots.filters" class="mrr-data-table-panel__filters">
      <slot name="filters" />
    </div>

    <div v-if="slots.toolbar" class="mrr-data-table-panel__toolbar">
      <slot name="toolbar" />
    </div>

    <div class="mrr-data-table-panel__content">
      <slot />
    </div>

    <div v-if="slots.pagination" class="mrr-data-table-panel__pagination">
      <slot name="pagination" />
    </div>
  </MrrSectionCard>
</template>

<style scoped>
.mrr-data-table-panel__count {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 500;
  color: var(--mrr-secondary-foreground);
  background: var(--mrr-secondary);
  border: 1px solid transparent;
  border-radius: var(--mrr-radius-sm);
}

.mrr-data-table-panel__filters {
  padding: var(--mrr-space-3) var(--mrr-space-4) 0;
}

.mrr-data-table-panel__toolbar {
  display: flex;
  gap: var(--mrr-space-3);
  align-items: center;
  justify-content: space-between;
  padding: var(--mrr-space-3) var(--mrr-space-4);
  border-bottom: 1px solid var(--mrr-border);
}

.mrr-data-table-panel__content {
  min-width: 0;
  overflow: hidden;
}

.mrr-data-table-panel__pagination {
  display: flex;
  justify-content: flex-end;
  padding: var(--mrr-space-3) var(--mrr-space-4);
  background: color-mix(in srgb, var(--mrr-muted) 28%, var(--mrr-card));
  border-top: 1px solid var(--mrr-border);
}

.mrr-data-table-panel__content :deep(.el-table) {
  --el-table-border-color: var(--mrr-border);

  width: 100%;
}

@media (width <= 760px) {
  .mrr-data-table-panel__filters {
    padding: var(--mrr-space-3) var(--mrr-space-3) 0;
  }

  .mrr-data-table-panel__pagination {
    justify-content: flex-start;
    padding-inline: var(--mrr-space-3);
    overflow-x: auto;
  }
}
</style>
