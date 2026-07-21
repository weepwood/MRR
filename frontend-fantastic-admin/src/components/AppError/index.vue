<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'

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
  <div class="app-error" role="alert">
    <div class="app-error-icon i-ant-design:warning-twotone" />
    <p class="app-error-text">{{ message }}</p>
    <el-button type="primary" :icon="Refresh" @click="handleRetry">
      {{ retryText }}
    </el-button>
  </div>
</template>

<style scoped>
.app-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 16px;
  text-align: center;
  animation: app-error-enter 180ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

.app-error-icon {
  margin-bottom: 16px;
  font-size: 48px;
  color: var(--el-color-warning, #e6a23c);
  transform-origin: center;
  animation: app-error-icon-enter 180ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

.app-error-text {
  margin: 0 0 20px;
  font-size: 14px;
  color: var(--el-text-color-regular, #606266);
}

@keyframes app-error-enter {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes app-error-icon-enter {
  from { transform: scale(0.92); }
  to { transform: scale(1); }
}

@media (prefers-reduced-motion: reduce) {
  .app-error,
  .app-error-icon {
    animation: none;
  }
}
</style>
