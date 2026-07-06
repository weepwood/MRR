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
      <el-button :icon="ArrowLeft" @click="emit('back')">
        返回明细
      </el-button>
      <el-button :icon="Refresh" :loading="props.loading" @click="emit('refresh')">
        刷新
      </el-button>
      <el-button type="primary" :icon="Download" :loading="props.downloading" @click="emit('download')">
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
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: flex-end;
  justify-content: space-between;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--divider);
}

.archive-title h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.5px;
}

.archive-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.archive-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (width <= 720px) {
  .archive-header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
