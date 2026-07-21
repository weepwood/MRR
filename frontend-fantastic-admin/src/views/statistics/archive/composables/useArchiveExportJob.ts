import type {
  ArchiveExportJob,
  CreateArchiveExportJobRequest,
} from '@/api/modules/archive-export'
import { ElMessage } from 'element-plus'
import { getCurrentScope, onScopeDispose, ref } from 'vue'
import {
  cancelArchiveExportJob,
  createArchiveExportJob,
  getArchiveExportJob,
} from '@/api/modules/archive-export'
import { downloadExportJobWithResume } from '../utils/resumable-export-download'

function createIdempotencyKey(request: CreateArchiveExportJobRequest): string {
  const random = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `${request.format.toLowerCase()}:${random}`
}

export function useArchiveExportJob(_formatHint?: 'ZIP' | 'PDF') {
  const job = ref<ArchiveExportJob | null>(null)
  const creating = ref(false)
  const cancelling = ref(false)
  const downloading = ref(false)
  let pollTimer: ReturnType<typeof setTimeout> | undefined
  let disposed = false
  let generation = 0

  function applyJob(next: ArchiveExportJob) {
    if (job.value?.id === next.id) {
      Object.assign(job.value, next)
    }
    else {
      job.value = next
    }
  }

  function stopPolling() {
    if (pollTimer) {
      clearTimeout(pollTimer)
      pollTimer = undefined
    }
  }

  function isTerminal(status: ArchiveExportJob['status']) {
    return ['SUCCESS', 'FAILED', 'CANCELLED', 'EXPIRED'].includes(status)
  }

  function isCurrent(id: string, token: number) {
    return !disposed && token === generation && job.value?.id === id
  }

  function schedulePoll(id: string, token: number, delay: number) {
    if (!isCurrent(id, token)) return
    stopPolling()
    pollTimer = setTimeout(() => void poll(id, token), delay)
  }

  async function poll(id: string, token: number) {
    if (!isCurrent(id, token)) return
    try {
      const response = await getArchiveExportJob(id)
      // discard/dismiss 或新任务启动后，即使旧请求稍后返回也不得恢复旧按钮状态。
      if (!isCurrent(id, token)) return
      if (response.data) applyJob(response.data)
      if (!job.value || isTerminal(job.value.status)) {
        stopPolling()
        cancelling.value = false
        if (job.value?.status === 'SUCCESS') {
          ElMessage.success('病案导出文件已生成，可在按钮上下载')
        }
        else if (job.value?.status === 'FAILED') {
          ElMessage.error(job.value.errorMessage || '病案导出任务失败')
        }
        else if (job.value?.status === 'CANCELLED') {
          ElMessage.info('病案导出任务已取消')
        }
        return
      }
      schedulePoll(id, token, 1500)
    }
    catch (error: unknown) {
      if (!isCurrent(id, token)) return
      stopPolling()
      cancelling.value = false
      ElMessage.error((error as { message?: string })?.message || '导出任务状态查询失败')
    }
  }

  async function start(request: CreateArchiveExportJobRequest) {
    stopPolling()
    const token = ++generation
    creating.value = true
    cancelling.value = false
    try {
      const response = await createArchiveExportJob({
        ...request,
        idempotencyKey: request.idempotencyKey || createIdempotencyKey(request),
      })
      if (!response.data) throw new Error('服务器未返回导出任务')
      if (disposed || token !== generation) {
        if (!isTerminal(response.data.status)) {
          void cancelArchiveExportJob(response.data.id).catch(() => undefined)
        }
        return null
      }
      applyJob(response.data)
      ElMessage.info('病案较大，已转为后台生成任务，进度将在按钮上显示')
      if (!isTerminal(job.value.status)) {
        schedulePoll(job.value.id, token, 500)
      }
      return job.value
    }
    finally {
      if (token === generation) creating.value = false
    }
  }

  async function cancel() {
    const current = job.value
    if (!current || isTerminal(current.status)) return
    const token = generation
    cancelling.value = true
    try {
      const response = await cancelArchiveExportJob(current.id)
      if (!isCurrent(current.id, token)) return
      if (response.data) applyJob(response.data)
      ElMessage.info(job.value?.status === 'CANCELLED' ? '导出任务已取消' : '正在取消导出任务')
      if (job.value && !isTerminal(job.value.status)) {
        // PROCESSING 任务的取消是协作式的，继续轮询直到后端写入终态。
        schedulePoll(current.id, token, 500)
      }
      else {
        stopPolling()
        cancelling.value = false
      }
    }
    catch (error) {
      if (token === generation) cancelling.value = false
      throw error
    }
  }

  async function discard() {
    const current = job.value
    generation++
    stopPolling()
    job.value = null
    creating.value = false
    cancelling.value = false
    if (!current || isTerminal(current.status)) return
    try {
      await cancelArchiveExportJob(current.id)
    }
    catch {
      // 当前页面已切换病案或选择，失效旧按钮状态不能被取消请求失败阻塞。
    }
  }

  async function download() {
    const current = job.value
    if (!current || current.status !== 'SUCCESS') return
    downloading.value = true
    try {
      const mode = await downloadExportJobWithResume(current)
      ElMessage.success(mode === 'resumable' ? '导出文件已写入磁盘' : '导出文件已开始下载')
    }
    catch (error: unknown) {
      if ((error as { name?: string })?.name === 'AbortError') {
        ElMessage.info('已取消选择下载文件')
        return
      }
      ElMessage.error((error as { message?: string })?.message || '导出文件下载失败')
    }
    finally {
      downloading.value = false
    }
  }

  function dismiss() {
    generation++
    stopPolling()
    job.value = null
    creating.value = false
    cancelling.value = false
  }

  if (getCurrentScope()) {
    onScopeDispose(() => {
      disposed = true
      generation++
      stopPolling()
    })
  }

  return {
    job,
    creating,
    cancelling,
    downloading,
    start,
    cancel,
    discard,
    download,
    dismiss,
  }
}
