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
    <template #title-suffix>
      <slot name="title-suffix" />
    </template>

    <template v-if="hasActions" #actions>
      <el-tag
        v-if="props.count !== undefined"
        size="small"
        type="info"
        effect="plain"
        class="mrr-data-table-panel__count"
      >
        {{ props.countLabel || '共' }} {{ countText }} 条
      </el-tag>
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
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.mrr-data-table-panel__filters {
  padding: 14px 16px 0;
}

.mrr-data-table-panel__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-3);
  align-items: center;
  justify-content: space-between;
  padding: var(--mrr-space-3) var(--mrr-space-4);
  border-bottom: 1px solid var(--mrr-shell-divider);
}

.mrr-data-table-panel__content {
  min-width: 0;
  overflow: hidden;
}

.mrr-data-table-panel__pagination {
  display: flex;
  justify-content: flex-end;
  padding: 13px 16px;
  background: var(--mrr-card);
  background: color-mix(in srgb, var(--mrr-muted) 18%, var(--mrr-card));
  border-top: 1px solid var(--mrr-shell-divider);
}

.mrr-data-table-panel__pagination :deep(.el-pagination) {
  justify-content: flex-end;
}

.mrr-data-table-panel__content :deep(.el-table) {
  --el-table-border-color: var(--mrr-border);

  width: 100%;
}

@media (max-width: 760px) {
  .mrr-data-table-panel__filters {
    padding: var(--mrr-space-3) var(--mrr-space-3) 0;
  }

  .mrr-data-table-panel__pagination {
    justify-content: flex-start;
    padding-inline: var(--mrr-space-3);
  }
}
</style>
