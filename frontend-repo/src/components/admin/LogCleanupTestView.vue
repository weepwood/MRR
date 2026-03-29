<template>
  <el-card class="pmr-panel log-cleanup-test-view" shadow="never">
    <template #header>
      <div class="pmr-panel-header">
        <div>
          <h3 class="pmr-panel-title">日志清理测试</h3>
          <p class="pmr-panel-subtitle">
            先导出过期日志，再执行手动清理。自动清理默认关闭，仅保留手动测试入口。
          </p>
        </div>
        <span class="pmr-badge">Retention</span>
      </div>
    </template>

    <div class="cleanup-actions">
      <el-button type="primary" :loading="runningCleanup" @click="runExportAndCleanup">
        导出并清理日志
      </el-button>
      <el-button @click="resetResult">清空结果</el-button>
    </div>

    <el-alert
      v-if="cleanupResult"
      :type="alertType"
      :title="cleanupTitle"
      :closable="false"
      show-icon
      class="cleanup-alert"
    />

    <el-descriptions v-if="cleanupResult" :column="2" border class="cleanup-descriptions">
      <el-descriptions-item label="自动清理">{{ cleanupResult.enabled ? '开启' : '关闭' }}</el-descriptions-item>
      <el-descriptions-item label="是否跳过">{{ cleanupResult.skipped ? '是' : '否' }}</el-descriptions-item>
      <el-descriptions-item label="是否成功">{{ cleanupResult.success ? '是' : '否' }}</el-descriptions-item>
      <el-descriptions-item label="保留天数">{{ cleanupResult.retentionDays }}</el-descriptions-item>
      <el-descriptions-item label="批次大小">{{ cleanupResult.batchSize }}</el-descriptions-item>
      <el-descriptions-item label="最大批次">{{ cleanupResult.maxBatchesPerRun }}</el-descriptions-item>
      <el-descriptions-item label="本次删除">{{ cleanupResult.deleted }}</el-descriptions-item>
      <el-descriptions-item label="剩余过期">{{ cleanupResult.remainingOlderThanCutoff }}</el-descriptions-item>
      <el-descriptions-item label="执行时间">{{ formatDateTime(cleanupResult.executedAt) }}</el-descriptions-item>
      <el-descriptions-item label="清理阈值">{{ formatDateTime(cleanupResult.cutoff) }}</el-descriptions-item>
      <el-descriptions-item label="执行批次">{{ cleanupResult.batches }}</el-descriptions-item>
      <el-descriptions-item label="提示信息" :span="2">
        <span class="cleanup-message">{{ cleanupResult.message || '-' }}</span>
      </el-descriptions-item>
    </el-descriptions>

    <el-empty v-else description="点击按钮后会先下载导出文件，再执行手动清理" />
  </el-card>
</template>

<script setup>
import { computed, ref } from 'vue'
import { exportLogRetentionLogs, runLogRetentionCleanup } from '@/services/api'

const runningCleanup = ref(false)
const cleanupResult = ref(null)

const cleanupTitle = computed(() => {
  if (!cleanupResult.value) return ''
  if (cleanupResult.value.success && cleanupResult.value.skipped) {
    return '清理已跳过'
  }
  if (cleanupResult.value.success) {
    return '清理执行成功'
  }
  return '清理执行失败'
})

const alertType = computed(() => {
  if (!cleanupResult.value) return 'info'
  if (cleanupResult.value.success && cleanupResult.value.deleted > 0) return 'success'
  if (cleanupResult.value.success) return 'warning'
  return 'error'
})

const formatDateTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
}

const getDownloadFileName = (headers) => {
  const disposition = headers?.['content-disposition'] || headers?.['Content-Disposition'] || ''
  const match = disposition.match(/filename\*?=(?:UTF-8''|")?([^";]+)/i)
  if (match?.[1]) {
    return decodeURIComponent(match[1].replace(/"/g, ''))
  }
  return `access-log-retention-${new Date().toISOString().replace(/[:.]/g, '-')}.csv`
}

const downloadBlob = (blob, fileName) => {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

const exportLogsFirst = async () => {
  const response = await exportLogRetentionLogs()
  const blob = response?.data
  if (!(blob instanceof Blob)) {
    throw new Error('日志导出失败')
  }
  const fileName = getDownloadFileName(response.headers)
  downloadBlob(blob, fileName)
  return {
    fileName,
    total: Number.parseInt(response.headers?.['x-export-total'] || response.headers?.['X-Export-Total'] || '0', 10) || 0,
    cutoff: response.headers?.['x-export-cutoff'] || response.headers?.['X-Export-Cutoff'] || ''
  }
}

const runExportAndCleanup = async () => {
  runningCleanup.value = true
  try {
    const exportInfo = await exportLogsFirst()
    ElMessage.success(exportInfo.total > 0 ? `已导出 ${exportInfo.total} 条日志` : '已导出日志文件')

    const response = await runLogRetentionCleanup(exportInfo.cutoff ? { cutoff: exportInfo.cutoff } : {})
    const result = response?.data
    if (!result || result.code !== 200) {
      throw new Error(result?.message || '日志清理测试失败')
    }

    cleanupResult.value = result.data || null
    if (cleanupResult.value?.success) {
      ElMessage.success(cleanupResult.value.message || '日志清理测试已完成')
    } else {
      ElMessage.warning(cleanupResult.value?.message || '日志清理测试未成功')
    }
  } catch (error) {
    cleanupResult.value = null
    ElMessage.error(error?.message || '日志清理测试失败')
  } finally {
    runningCleanup.value = false
  }
}

const resetResult = () => {
  cleanupResult.value = null
}
</script>

<style scoped>
.cleanup-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.cleanup-alert {
  margin-bottom: 16px;
}

.cleanup-descriptions {
  margin-bottom: 8px;
}

.cleanup-message {
  word-break: break-word;
}
</style>
