<script setup lang="ts">
import { motion } from 'motion-v'
import { motionDurations, motionEasings, motionSprings } from '@/motion/presets'

defineOptions({ name: 'AppEmpty' })

withDefaults(defineProps<{
  description?: string
  icon?: string
  actionText?: string
  actionIcon?: string
}>(), {
  description: '暂无数据',
  icon: 'i-ant-design:inbox-outlined',
})

const emit = defineEmits<{
  action: []
}>()

function handleAction() {
  emit('action')
}
</script>

<template>
  <motion.div
    class="app-empty"
    :initial="{ opacity: 0, y: 4 }"
    :animate="{ opacity: 1, y: 0 }"
    :transition="{ duration: motionDurations.standard, ease: motionEasings.emphasized }"
  >
    <motion.div
      class="app-empty-icon"
      :class="icon"
      :initial="{ scale: 0.92 }"
      :animate="{ scale: 1 }"
      :transition="motionSprings.interaction"
    />
    <p class="app-empty-text">{{ description }}</p>
    <el-button v-if="actionText" type="primary" :icon="actionIcon" @click="handleAction">
      {{ actionText }}
    </el-button>
  </motion.div>
</template>

<style scoped>
.app-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 16px;
  text-align: center;
}

.app-empty-icon {
  margin-bottom: 16px;
  font-size: 48px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  transform-origin: center;
}

.app-empty-text {
  margin: 0 0 20px;
  font-size: 14px;
  color: var(--el-text-color-secondary, #909399);
}
</style>
