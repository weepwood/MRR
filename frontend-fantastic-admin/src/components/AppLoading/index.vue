<script setup lang="ts">
import { motion } from 'motion-v'
import { motionDurations } from '@/motion/presets'

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
  <motion.div
    class="app-loading"
    :initial="{ opacity: 0 }"
    :animate="{ opacity: 1 }"
    :transition="{ duration: motionDurations.fast }"
    aria-busy="true"
    aria-label="内容加载中"
  >
    <template v-if="type === 'table'">
      <div v-for="i in rows" :key="i" class="skeleton-row">
        <div v-for="j in cols" :key="j" class="skeleton-cell" />
      </div>
    </template>
    <template v-else-if="type === 'card'">
      <div class="skeleton-cards">
        <div v-for="i in cols" :key="i" class="skeleton-card">
          <div class="skeleton-card-img" />
          <div class="skeleton-card-line skeleton-card-line--short" />
          <div class="skeleton-card-line" />
        </div>
      </div>
    </template>
    <template v-else-if="type === 'stats'">
      <div class="skeleton-stats">
        <div v-for="i in cols" :key="i" class="skeleton-stat">
          <div class="skeleton-stat-label" />
          <div class="skeleton-stat-value" />
        </div>
      </div>
    </template>
  </motion.div>
</template>

<style scoped>
.app-loading {
  width: 100%;
}

.skeleton-row {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.skeleton-cell {
  flex: 1;
  height: 16px;
  background: linear-gradient(90deg, var(--el-fill-color-light, #f0f2f5) 25%, var(--el-fill-color, #e4e7ed) 50%, var(--el-fill-color-light, #f0f2f5) 75%);
  background-size: 200% 100%;
  border-radius: 4px;
  animation: shimmer 1.5s infinite;
}

.skeleton-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.skeleton-card {
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 8px;
}

.skeleton-card-img {
  height: 120px;
  margin-bottom: 12px;
  background: linear-gradient(90deg, var(--el-fill-color-light, #f0f2f5) 25%, var(--el-fill-color, #e4e7ed) 50%, var(--el-fill-color-light, #f0f2f5) 75%);
  background-size: 200% 100%;
  border-radius: 4px;
  animation: shimmer 1.5s infinite;
}

.skeleton-card-line {
  height: 14px;
  margin-bottom: 8px;
  background: linear-gradient(90deg, var(--el-fill-color-light, #f0f2f5) 25%, var(--el-fill-color, #e4e7ed) 50%, var(--el-fill-color-light, #f0f2f5) 75%);
  background-size: 200% 100%;
  border-radius: 4px;
  animation: shimmer 1.5s infinite;
}

.skeleton-card-line--short {
  width: 60%;
}

.skeleton-stats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}

.skeleton-stat {
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 8px;
}

.skeleton-stat-label {
  width: 50%;
  height: 12px;
  margin-bottom: 8px;
  background: linear-gradient(90deg, var(--el-fill-color-light, #f0f2f5) 25%, var(--el-fill-color, #e4e7ed) 50%, var(--el-fill-color-light, #f0f2f5) 75%);
  background-size: 200% 100%;
  border-radius: 4px;
  animation: shimmer 1.5s infinite;
}

.skeleton-stat-value {
  width: 70%;
  height: 24px;
  background: linear-gradient(90deg, var(--el-fill-color-light, #f0f2f5) 25%, var(--el-fill-color, #e4e7ed) 50%, var(--el-fill-color-light, #f0f2f5) 75%);
  background-size: 200% 100%;
  border-radius: 4px;
  animation: shimmer 1.5s infinite;
}

@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-cell,
  .skeleton-card-img,
  .skeleton-card-line,
  .skeleton-stat-label,
  .skeleton-stat-value {
    background-position: 0 0;
    animation: none;
  }
}
</style>
