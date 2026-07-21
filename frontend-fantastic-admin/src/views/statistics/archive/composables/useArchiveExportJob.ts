import type {
  ArchiveExportJob,
  CreateArchiveExportJobRequest,
} from '@/api/modules/archive-export'
import { ElMessage } from 'element-plus'
import { onScopeDispose, ref } from 'vue'
import {
  cancelArchiveExportJob,
  createArchiveExportJob,
  downloadArchiveExportJob,
  getArchiveExportJob,
} from '@/api/modules/archive-export'

function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function createIdempotencyKey(request: CreateArchiveExportJobRequest): string {
  const random = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `${request.format.toLowerCase()}:${random}`
}

export function useArchiveExportJob() {
  const job = ref<ArchiveExportJob | null>(null)
  const creating = ref(false)
  const cancelling = ref(false)
  const downloading = ref(false)
  let pollTimer: ReturnType<typeof setTimeout> | undefined

  function stopPolling() {
    if (pollTimer) {
      clearTimeout(pollTimer)
      pollTimer = undefined
    }
  }

  function isTerminal(status: ArchiveExportJob['status']) {
    return ['SUCCESS', 'FAILED', 'CANCELLED', 'EXPIRED'].includes(status)
  }

  async function poll() {
    const id = job.value?.id
    if (!id) return
    try {
      const response = await getArchiveExportJob(id)
      if (response.data) job.value = response.data
      if (!job.value || isTerminal(job.value.status)) {
        stopPolling()
        if (job.value?.status === 'SUCCESS') {
          ElMessage.success('病案导出文件已生成，可开始下载')
        }
        else if (job.value?.status === 'FAILED') {
          ElMessage.error(job.value.errorMessage || '病案导出任务失败')
        }
        return
      }
      pollTimer = setTimeout(() => void poll(), 1500)
    }
    catch (error: unknown) {
      stopPolling()
      ElMessage.error((error as { message?: string })?.message || '导出任务状态查询失败')
    }
  }

  async function start(request: CreateArchiveExportJobRequest) {
    stopPolling()
    creating.value = true
    try {
      const response = await createArchiveExportJob({
        ...request,
        idempotencyKey: request.idempotencyKey || createIdempotencyKey(request),
      })
      if (!response.data) throw new Error('服务器未返回导出任务')
      job.value = response.data
      ElMessage.info('病案较大，已转为后台生成任务')
      if (!isTerminal(job.value.status)) {
        pollTimer = setTimeout(() => void poll(), 500)
      }
      return job.value
    }
    finally {
      creating.value = false
    }
  }

  async function cancel() {
    const id = job.value?.id
    if (!id || isTerminal(job.value.status)) return
    cancelling.value = true
    try {
      const response = await cancelArchiveExportJob(id)
      if (response.data) job.value = response.data
      stopPolling()
      ElMessage.info('已提交取消请求')
    }
    finally {
      cancelling.value = false
    }
  }

  async function download() {
    const current = job.value
    if (!current || current.status !== 'SUCCESS') return
    downloading.value = true
    try {
      const blob = await downloadArchiveExportJob(current.id)
      saveBlob(blob, current.fileName || `archive-export-${current.id}.${current.format.toLowerCase()}`)
    }
    finally {
      downloading.value = false
    }
  }

  function dismiss() {
    stopPolling()
    job.value = null
  }

  onScopeDispose(stopPolling)

  return {
    job,
    creating,
    cancelling,
    downloading,
    start,
    cancel,
    download,
    dismiss,
  }
}
