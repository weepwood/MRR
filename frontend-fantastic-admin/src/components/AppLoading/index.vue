<script setup lang="ts">
defineOptions({ name: 'AppLoading' })

withDefaults(defineProps<{
  type?: 'table' | 'card' | 'stats'
  rows?: number
  cols?: number
}>(), {
  type: 'table',
  rows: 5,
  cols: 4,
})
</script>

<template>
  <div
    class="app-loading mrr-fade-enter"
    aria-busy="true"
    aria-label="内容加载中"
  >
    <template v-if="type === 'table'">
      <!-- 表头单独一行、颜色更浅，骨架的轮廓与真实表格一致，结果到达时不跳版。 -->
      <div class="skeleton-head">
        <div v-for="j in cols" :key="j" class="skeleton-cell" />
      </div>
      <div v-for="i in rows" :key="i" class="skeleton-row" :style="{ '--skeleton-index': i - 1 }">
        <div v-for="j in cols" :key="j" class="skeleton-cell" />
      </div>
    </template>
    <template v-else-if="type === 'card'">
      <div class="skeleton-cards">
        <div v-for="i in cols" :key="i" class="skeleton-card" :style="{ '--skeleton-index': i - 1 }">
          <div class="skeleton-card-img" />
          <div class="skeleton-card-line skeleton-card-line--short" />
          <div class="skeleton-card-line" />
        </div>
      </div>
    </template>
    <template v-else-if="type === 'stats'">
      <div class="skeleton-stats">
        <div v-for="i in cols" :key="i" class="skeleton-stat" :style="{ '--skeleton-index': i - 1 }">
          <div class="skeleton-stat-label" />
          <div class="skeleton-stat-value" />
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
/*
 * 骨架只用令牌：间距 --mrr-space-*、圆角跟随全局圆角滑块（--mrr-radius-sm），
 * 扫光底色沿用 Element Plus 中性填充（gr8r 调色板已把它们桥接到 MRR 令牌）。
 * 行 / 卡依次淡入（stagger，最多 6 档），关闭动效时由 motion.css 统一清零。
 */
.app-loading {
  --skeleton-base: var(--el-fill-color-light);
  --skeleton-highlight: var(--el-fill-color);
  --skeleton-shimmer: linear-gradient(90deg, var(--skeleton-base) 25%, var(--skeleton-highlight) 50%, var(--skeleton-base) 75%);

  width: 100%;
}

.skeleton-cell,
.skeleton-card-img,
.skeleton-card-line,
.skeleton-stat-label,
.skeleton-stat-value {
  background: var(--skeleton-shimmer);
  background-size: 200% 100%;
  border-radius: var(--mrr-radius-sm);
  animation: shimmer var(--mrr-motion-loop-soft) linear infinite;
}

.skeleton-head,
.skeleton-row {
  display: flex;
  gap: var(--mrr-space-3);
  align-items: center;
}

.skeleton-head {
  height: var(--mrr-table-header-height);
  padding-inline: var(--mrr-space-3);
  background: var(--el-fill-color-lighter);
  border-radius: var(--mrr-radius-md) var(--mrr-radius-md) 0 0;
}

.skeleton-head .skeleton-cell {
  height: 10px;
  opacity: 0.7;
}

.skeleton-row {
  height: var(--mrr-table-row-height);
  padding-inline: var(--mrr-space-3);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.skeleton-row,
.skeleton-card,
.skeleton-stat {
  animation: mrr-content-enter var(--mrr-motion-move) var(--mrr-ease-out) backwards;
  animation-delay: calc(var(--mrr-motion-stagger) * min(var(--skeleton-index, 0), 6));
}

.skeleton-cell {
  flex: 1 1 0;
  height: 12px;
}

/* 列宽与行长轻微错落，读起来像文字而不是一张网格。 */
.skeleton-head .skeleton-cell:first-child,
.skeleton-row .skeleton-cell:first-child {
  flex-grow: 1.4;
}

.skeleton-row:nth-child(3n) .skeleton-cell:last-child {
  flex-grow: 0.55;
}

.skeleton-row:nth-child(3n + 1) .skeleton-cell:nth-child(2) {
  flex-grow: 0.75;
}

.skeleton-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--mrr-space-4);
}

.skeleton-card {
  padding: var(--mrr-space-4);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--mrr-radius-card);
}

.skeleton-card-img {
  height: 120px;
  margin-bottom: var(--mrr-space-3);
  border-radius: var(--mrr-radius-md);
}

.skeleton-card-line {
  height: 14px;
  margin-bottom: var(--mrr-space-2);
}

.skeleton-card-line--short {
  width: 60%;
}

.skeleton-stats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: var(--mrr-space-4);
}

.skeleton-stat {
  padding: var(--mrr-space-4);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--mrr-radius-card);
}

.skeleton-stat-label {
  width: 50%;
  height: 12px;
  margin-bottom: var(--mrr-space-2);
}

.skeleton-stat-value {
  width: 70%;
  height: 24px;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
