<script setup lang="ts">
import { ArrowLeft, Download, Printer, Refresh } from '@element-plus/icons-vue'

defineOptions({ name: 'ArchiveHeader' })

const props = withDefaults(defineProps<{
  loading?: boolean
  downloading?: boolean
  printing?: boolean
  selectedCount?: number
}>(), {
  loading: false,
  downloading: false,
  printing: false,
  selectedCount: 0,
})

const emit = defineEmits<{
  back: []
  refresh: []
  download: []
  print: []
}>()
</script>

<template>
  <header class="archive-header">
    <div class="archive-title">
      <h2>影像档案袋</h2>
      <p class="archive-subtitle">
        病案影像检索、预览与归档
      </p>
    </div>
    <div class="archive-actions">
      <el-button text :icon="ArrowLeft" @click="emit('back')">
        返回明细
      </el-button>
      <el-button text :icon="Refresh" :loading="props.loading" @click="emit('refresh')">
        刷新
      </el-button>
      <el-button :icon="Download" :loading="props.downloading" @click="emit('download')">
        下载档案袋
      </el-button>
      <el-button type="primary" :icon="Printer" :loading="props.printing" :disabled="!props.selectedCount" @click="emit('print')">
        打印选中<template v-if="props.selectedCount">
          ({{ props.selectedCount }})
        </template>
      </el-button>
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

.archive-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
}

.archive-actions :deep(.el-button) {
  width: 100%;
  margin: 0;
}

@media (width <= 720px) {
  .archive-header {
    padding-bottom: 8px;
  }
}
</style>
