<script setup lang="ts">
import type { ArchiveExportJob } from '@/api/modules/archive-export'
import { computed } from 'vue'

const props = defineProps<{
  job: ArchiveExportJob
  cancelling?: boolean
  downloading?: boolean
}>()

const emit = defineEmits<{
  cancel: []
  download: []
  dismiss: []
}>()

const progress = computed(() => {
  if (!props.job.plannedCount) return 0
  return Math.min(100, Math.round(props.job.processedCount * 100 / props.job.plannedCount))
})

const statusText = computed(() => ({
  PENDING: '等待处理',
  PROCESSING: '正在生成',
  SUCCESS: '生成完成',
  FAILED: '生成失败',
  CANCELLED: '已取消',
  EXPIRED: '文件已过期',
}[props.job.status]))

const alertType = computed(() => {
  if (props.job.status === 'SUCCESS') return 'success'
  if (props.job.status === 'FAILED' || props.job.status === 'EXPIRED') return 'error'
  if (props.job.status === 'CANCELLED') return 'warning'
  return 'info'
})

const canCancel = computed(() => ['PENDING', 'PROCESSING'].includes(props.job.status))
const canDownload = computed(() => props.job.status === 'SUCCESS')

function formatBytes(value: number) {
  if (!Number.isFinite(value) || value <= 0) return '未知'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let amount = value
  let index = 0
  while (amount >= 1024 && index < units.length - 1) {
    amount /= 1024
    index++
  }
  return `${amount >= 10 || index === 0 ? amount.toFixed(0) : amount.toFixed(1)} ${units[index]}`
}
</script>

<template>
  <el-alert
    class="archive-export-job"
    :type="alertType"
    :closable="!canCancel"
    show-icon
    @close="emit('dismiss')"
  >
    <template #title>
      <div class="job-title-row">
        <strong>{{ job.format }} 导出 · {{ statusText }}</strong>
        <span>{{ job.processedCount }}/{{ job.plannedCount }} 张</span>
      </div>
    </template>

    <div class="job-content">
      <el-progress
        v-if="job.status === 'PENDING' || job.status === 'PROCESSING'"
        :percentage="progress"
        :stroke-width="8"
      />
      <div class="job-meta">
        <span>预计 {{ formatBytes(job.estimatedBytes) }}</span>
        <span v-if="job.outputBytes">实际 {{ formatBytes(job.outputBytes) }}</span>
        <span v-if="job.sourceSummary">来源 {{ job.sourceSummary }}</span>
      </div>
      <p v-if="job.errorMessage" class="job-error">
        {{ job.errorMessage }}
      </p>
      <div class="job-actions">
        <el-button
          v-if="canCancel"
          size="small"
          :loading="cancelling"
          @click="emit('cancel')"
        >
          取消任务
        </el-button>
        <el-button
          v-if="canDownload"
          type="primary"
          size="small"
          :loading="downloading"
          @click="emit('download')"
        >
          下载文件
        </el-button>
        <el-button
          v-if="!canCancel"
          text
          size="small"
          @click="emit('dismiss')"
        >
          关闭
        </el-button>
      </div>
    </div>
  </el-alert>
</template>

<style scoped>
.archive-export-job {
  margin-top: 10px;
}

.job-title-row,
.job-meta,
.job-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.job-title-row {
  justify-content: space-between;
  width: 100%;
}

.job-content {
  display: grid;
  gap: 8px;
  margin-top: 8px;
}

.job-meta {
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--text-secondary);
}

.job-error {
  margin: 0;
  color: var(--el-color-danger);
}

.job-actions {
  justify-content: flex-end;
}
</style>
