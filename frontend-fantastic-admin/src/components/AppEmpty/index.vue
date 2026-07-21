<script setup lang="ts">
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
  <div class="app-empty mrr-content-enter">
    <div class="app-empty-icon mrr-status-pop" :class="icon" />
    <p class="app-empty-text">{{ description }}</p>
    <el-button v-if="actionText" type="primary" :icon="actionIcon" @click="handleAction">
      {{ actionText }}
    </el-button>
  </div>
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
