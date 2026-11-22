<script setup lang="ts">
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'

defineOptions({ name: 'MrrDataTablePanel' })

const props = defineProps<{
  title: string
  description?: string
  icon?: string
  count?: number
  countLabel?: string
  /**
   * 已有结果上的再次查询（刷新、翻页、改条件）。保留表格，200ms 后在表格上方显示进度条并把结果变暗。
   * 首次加载请继续在默认插槽里放 AppLoading 骨架；两者可以同时为真。
   */
  loading?: boolean
  /**
   * 结果标识：每次「用户发起的查询」返回后换一个值（请求代次或结果数组本身），
   * 前 6 行依次淡入，提示「这是新结果」。静默轮询不要换值，否则每次轮询都会闪一遍。
   */
  resultKey?: unknown
}>()

const slots = useSlots()
const hasActions = computed(() => props.count !== undefined || Boolean(slots.actions))
const countText = computed(() => Number(props.count ?? 0).toLocaleString('zh-CN'))

// a / b 轮换：两份同内容关键帧，名字一变就重新播放（见 motion.css 的 mrr-row-enter-*）。
const resultPass = ref<'a' | 'b'>()
watch(() => props.resultKey, (next, previous) => {
  if (next !== previous) {
    resultPass.value = resultPass.value === 'a' ? 'b' : 'a'
  }
})
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

    <div
      class="mrr-data-table-panel__content"
      :class="{ 'is-refreshing': props.loading }"
      :aria-busy="props.loading ? 'true' : undefined"
      :data-result-pass="resultPass"
    >
      <div v-if="props.loading" class="mrr-busy-bar mrr-data-table-panel__progress" aria-hidden="true" />
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
  --busy-inset: var(--mrr-card-inset);

  position: relative;
  min-width: 0;

  /* 表格自带圆角边框（元素自身 border 绘制），与卡片外框之间留出呼吸间隙。 */
  padding: var(--mrr-space-3) var(--mrr-card-inset) var(--mrr-card-inset);
  overflow: hidden;
}

/* 进度条放在表格上方 12px 留白的正中，左右与表格外沿对齐，不压表格的圆角边框。 */
.mrr-data-table-panel__progress {
  position: absolute;
  top: calc(var(--mrr-space-3) / 2 - 1px);
  right: var(--busy-inset);
  left: var(--busy-inset);
  animation:
    mrr-busy-bar var(--mrr-motion-loop-soft) ease-in-out infinite,
    mrr-fade-enter var(--mrr-motion-feedback) var(--mrr-ease-out) var(--mrr-motion-busy-delay) backwards;
}

/*
 * 刷新中：结果变暗并暂停交互（避免对即将被替换的旧行执行操作）。
 * 变暗同样延迟 busy-delay；恢复不延迟，新结果到达立即全亮。
 */
.mrr-data-table-panel__content > :deep(*) {
  transition: opacity var(--mrr-motion-feedback) var(--mrr-ease-out);
}

.mrr-data-table-panel__content.is-refreshing > :deep(:not(.mrr-data-table-panel__progress)) {
  pointer-events: none;
  opacity: 0.55;
  transition-delay: var(--mrr-motion-busy-delay);
}

/* 新结果：前 6 行依次淡入（stagger × 0..5，最长 150ms 延迟 + 180ms = 330ms ≤ 350ms）。 */
.mrr-data-table-panel__content[data-result-pass="a"] :deep(.el-table__body tr) {
  animation: mrr-row-enter-a var(--mrr-motion-move) var(--mrr-ease-out) backwards;
  animation-delay: calc(var(--mrr-motion-stagger) * var(--row-index, 5));
}

.mrr-data-table-panel__content[data-result-pass="b"] :deep(.el-table__body tr) {
  animation: mrr-row-enter-b var(--mrr-motion-move) var(--mrr-ease-out) backwards;
  animation-delay: calc(var(--mrr-motion-stagger) * var(--row-index, 5));
}

.mrr-data-table-panel__content :deep(.el-table__body tr:nth-child(1)) {
  --row-index: 0;
}

.mrr-data-table-panel__content :deep(.el-table__body tr:nth-child(2)) {
  --row-index: 1;
}

.mrr-data-table-panel__content :deep(.el-table__body tr:nth-child(3)) {
  --row-index: 2;
}

.mrr-data-table-panel__content :deep(.el-table__body tr:nth-child(4)) {
  --row-index: 3;
}

.mrr-data-table-panel__content :deep(.el-table__body tr:nth-child(5)) {
  --row-index: 4;
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
    --busy-inset: var(--mrr-card-inset-compact);

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
