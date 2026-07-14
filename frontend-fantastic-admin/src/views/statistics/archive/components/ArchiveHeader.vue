<script setup lang="ts">
import { ArrowLeft, Document, Download, Printer, Refresh } from '@element-plus/icons-vue'

defineOptions({ name: 'ArchiveHeader' })

const props = withDefaults(defineProps<{
  loading?: boolean
  downloading?: boolean
  printing?: boolean
  exportingPdf?: boolean
  showActions?: boolean
  selectedCount?: number
}>(), {
  loading: false,
  downloading: false,
  printing: false,
  exportingPdf: false,
  showActions: false,
  selectedCount: 0,
})

const emit = defineEmits<{
  back: []
  refresh: []
  download: []
  print: []
  exportPdf: []
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
    <div v-if="props.showActions" class="archive-actions">
      <el-button class="download-action" :icon="Download" :loading="props.downloading" @click="emit('download')">
        下载档案袋
      </el-button>
      <el-button :icon="Printer" :loading="props.printing" :disabled="!props.selectedCount" @click="emit('print')">
        打印选中<template v-if="props.selectedCount">
          ({{ props.selectedCount }})
        </template>
      </el-button>
      <el-button type="primary" :icon="Document" :loading="props.exportingPdf" :disabled="!props.selectedCount" @click="emit('exportPdf')">
        导出 PDF<template v-if="props.selectedCount">
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

.archive-heading,
.heading-actions {
  display: flex;
  align-items: center;
}

.archive-heading {
  gap: 12px;
  justify-content: space-between;
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

.archive-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.archive-actions .download-action {
  grid-column: 1 / -1;
}

.archive-actions :deep(.el-button) {
  min-width: 0;
  margin: 0;
}

@media (width <= 720px) {
  .archive-header {
    padding-bottom: 8px;
  }
}
</style>
