<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'
import { motion } from 'motion-v'
import { motionDurations, motionEasings, motionSprings } from '@/motion/presets'

defineOptions({ name: 'AppError' })

withDefaults(defineProps<{
  message?: string
  retryText?: string
}>(), {
  message: '加载失败',
  retryText: '重试',
})

const emit = defineEmits<{
  retry: []
}>()

function handleRetry() {
  emit('retry')
}
</script>

<template>
  <motion.div
    class="app-error"
    role="alert"
    :initial="{ opacity: 0, y: -4 }"
    :animate="{ opacity: 1, y: 0 }"
    :transition="{ duration: motionDurations.standard, ease: motionEasings.emphasized }"
  >
    <motion.div
      class="app-error-icon i-ant-design:warning-twotone"
      :initial="{ scale: 0.92 }"
      :animate="{ scale: 1 }"
      :transition="motionSprings.interaction"
    />
    <p class="app-error-text">{{ message }}</p>
    <el-button type="primary" :icon="Refresh" @click="handleRetry">
      {{ retryText }}
    </el-button>
  </motion.div>
</template>

<style scoped>
.app-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 16px;
  text-align: center;
}

.app-error-icon {
  margin-bottom: 16px;
  font-size: 48px;
  color: var(--el-color-warning, #e6a23c);
  transform-origin: center;
}

.app-error-text {
  margin: 0 0 20px;
  font-size: 14px;
  color: var(--el-text-color-regular, #606266);
}
</style>
