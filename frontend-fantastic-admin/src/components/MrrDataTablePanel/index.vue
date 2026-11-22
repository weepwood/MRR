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

/* 所有分区共用 --mrr-card-inset，卡头标题、筛选字段、表格外沿、分页落在同一条竖线上。 */
.mrr-data-table-panel__filters {
  padding: var(--mrr-space-4) var(--mrr-card-inset) 0;
}

.mrr-data-table-panel__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-3);
  align-items: center;
  justify-content: space-between;
  padding: var(--mrr-space-3) var(--mrr-card-inset);
  border-bottom: 1px solid var(--mrr-shell-divider);
}

.mrr-data-table-panel__content {
  min-width: 0;

  /* 表格自带圆角边框（元素自身 border 绘制），与卡片外框之间留出呼吸间隙。 */
  padding: var(--mrr-space-3) var(--mrr-card-inset) var(--mrr-card-inset);
  overflow: hidden;
}

.mrr-data-table-panel__pagination {
  display: flex;
  justify-content: flex-end;
  padding: var(--mrr-space-3) var(--mrr-card-inset);
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
    padding: var(--mrr-space-3) var(--mrr-card-inset-compact) 0;
  }

  .mrr-data-table-panel__toolbar {
    padding-right: var(--mrr-card-inset-compact);
    padding-left: var(--mrr-card-inset-compact);
  }

  .mrr-data-table-panel__content {
    padding: var(--mrr-card-inset-compact);
  }

  .mrr-data-table-panel__pagination {
    justify-content: flex-start;
    padding-inline: var(--mrr-card-inset-compact);
  }
}

/* Chrome 86 回退：分页条与表体之间的浅色分界靠底色承载，缺了会与最后一行粘在一起。 */
@supports not (color: color-mix(in srgb, #000 50%, #fff)) {
  .mrr-data-table-panel__pagination {
    background: var(--mrr-card);
  }
}
</style>
