<script setup lang="ts">
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'

defineOptions({ name: 'MrrChartCard' })

withDefaults(defineProps<{
  title: string
  description?: string
  loading?: boolean
  empty?: boolean
  emptyDescription?: string
}>(), {
  description: '',
  loading: false,
  empty: false,
  emptyDescription: '暂无图表数据',
})
</script>

<template>
  <MrrSectionCard :title="title" :description="description" class="mrr-chart-card">
    <template v-if="$slots.actions" #actions>
      <slot name="actions" />
    </template>

    <div v-if="$slots.summary" class="mrr-chart-card__summary">
      <slot name="summary" />
    </div>

    <div v-loading="loading" :aria-busy="loading" class="mrr-chart-card__body">
      <div v-if="empty && !loading" class="mrr-chart-card__empty">
        <i class="i-lucide:chart-no-axes-column-increasing" aria-hidden="true" />
        <span>{{ emptyDescription }}</span>
      </div>
      <slot v-else />
    </div>
  </MrrSectionCard>
</template>

<style scoped>
.mrr-chart-card__summary {
  margin-bottom: 14px;
}

.mrr-chart-card__body {
  min-height: 120px;
}

.mrr-chart-card__empty {
  display: grid;
  gap: 8px;
  place-content: center;
  justify-items: center;
  min-height: 220px;
  color: var(--mrr-muted-foreground);
}

.mrr-chart-card__empty i {
  font-size: 30px;
}

.mrr-chart-card__empty span {
  font-size: 13px;
}
</style>
