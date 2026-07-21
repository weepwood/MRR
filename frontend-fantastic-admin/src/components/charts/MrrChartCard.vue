<script setup lang="ts">
import { AnimatePresence, motion } from 'motion-v'
import { motionDurations, motionEasings, motionSprings } from '@/motion/presets'

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
  <el-card shadow="never" class="mrr-chart-card">
    <template #header>
      <div class="mrr-chart-card__header">
        <div class="mrr-chart-card__heading">
          <strong>{{ title }}</strong>
          <span v-if="description">{{ description }}</span>
        </div>
        <div v-if="$slots.actions" class="mrr-chart-card__actions">
          <slot name="actions" />
        </div>
      </div>
    </template>

    <div v-if="$slots.summary" class="mrr-chart-card__summary">
      <slot name="summary" />
    </div>

    <div v-loading="loading" class="mrr-chart-card__body">
      <AnimatePresence mode="wait" :initial="false">
        <motion.div
          v-if="empty && !loading"
          key="empty"
          class="mrr-chart-card__empty"
          :initial="{ opacity: 0, y: 4 }"
          :animate="{ opacity: 1, y: 0 }"
          :exit="{ opacity: 0, y: -2 }"
          :transition="{ duration: motionDurations.standard, ease: motionEasings.emphasized }"
        >
          <motion.i
            class="i-ant-design:bar-chart-outlined"
            aria-hidden="true"
            :initial="{ scale: 0.92 }"
            :animate="{ scale: 1 }"
            :transition="motionSprings.interaction"
          />
          <span>{{ emptyDescription }}</span>
        </motion.div>
        <motion.div
          v-else
          key="content"
          class="mrr-chart-card__content"
          :initial="{ opacity: 0 }"
          :animate="{ opacity: 1 }"
          :exit="{ opacity: 0 }"
          :transition="{ duration: motionDurations.fast }"
        >
          <slot />
        </motion.div>
      </AnimatePresence>
    </div>
  </el-card>
</template>

<style scoped>
.mrr-chart-card {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--app-radius, 12px);
}

.mrr-chart-card :deep(.el-card__header) {
  padding: 16px 20px;
  background: color-mix(in srgb, var(--el-fill-color-light) 46%, transparent);
  border-bottom-color: var(--el-border-color-lighter);
}

.mrr-chart-card :deep(.el-card__body) {
  padding: 20px;
}

.mrr-chart-card__header {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}

.mrr-chart-card__heading {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.mrr-chart-card__heading strong {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.mrr-chart-card__heading span {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.mrr-chart-card__actions {
  flex: none;
}

.mrr-chart-card__summary {
  margin-bottom: 14px;
}

.mrr-chart-card__body {
  min-height: 120px;
}

.mrr-chart-card__content {
  min-width: 0;
}

.mrr-chart-card__empty {
  display: grid;
  gap: 8px;
  place-content: center;
  justify-items: center;
  min-height: 220px;
  color: var(--el-text-color-placeholder);
}

.mrr-chart-card__empty i {
  font-size: 30px;
  transform-origin: center;
}

.mrr-chart-card__empty span {
  font-size: 13px;
}
</style>
