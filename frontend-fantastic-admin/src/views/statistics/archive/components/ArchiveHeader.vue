<script setup lang="ts">
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'

defineOptions({ name: 'ArchiveHeader' })

const props = withDefaults(defineProps<{
  loading?: boolean
}>(), {
  loading: false,
})

const emit = defineEmits<{
  back: []
  refresh: []
}>()
</script>

<template>
  <header class="archive-header">
    <div class="archive-heading">
      <div class="archive-title">
        <h2>影像档案袋</h2>
        <p class="archive-subtitle">
          病案影像检索、预览与归档
        </p>
      </div>
      <div class="heading-actions">
        <el-button text size="small" :icon="ArrowLeft" @click="emit('back')">
          返回
        </el-button>
        <el-button text size="small" :icon="Refresh" :loading="props.loading" @click="emit('refresh')">
          刷新
        </el-button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.archive-header {
  display: grid;
  gap: 10px;
  padding: 2px 2px 12px;
  border-bottom: 1px solid var(--divider);
}

.archive-heading,
.heading-actions {
  display: flex;
  align-items: center;
}

.archive-heading {
  gap: 12px;
  justify-content: space-between;
  padding: 2px 2px 12px;
  border-bottom: 1px solid var(--divider);
}

.heading-actions {
  flex: none;
  gap: 2px;
}

.archive-title h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.5px;
}

.archive-subtitle {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--text-secondary);
}

@media (width <= 720px) {
  .archive-header {
    padding-bottom: 8px;
  }
}
</style>
