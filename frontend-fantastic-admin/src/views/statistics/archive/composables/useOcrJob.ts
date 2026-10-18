import type { OcrJob } from '@/api/modules/ocr'
import { onScopeDispose, ref } from 'vue'
import { getOcrJob } from '@/api/modules/ocr'

const terminal = new Set(['SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELLED', 'INTERRUPTED'])

export function useOcrJob() {
  const job = ref<OcrJob | null>(null)
  const error = ref('')
  let generation = 0
  let currentId = ''
  let timer: ReturnType<typeof setTimeout> | undefined

  function clearTimer() {
    if (timer !== undefined) { clearTimeout(timer) }
    timer = undefined
  }

  async function poll(version: number) {
    if (!currentId || document.hidden || version !== generation) { return }
    try {
      const result = await getOcrJob(currentId)
      if (version !== generation) { return }
      job.value = result.data ?? null
      error.value = ''
    }
    catch {
      if (version === generation) { error.value = '任务进度读取失败，可稍后刷新' }
    }
    if (version === generation && !document.hidden && !terminal.has(job.value?.status || '')) {
      timer = setTimeout(() => void poll(version), 3000)
    }
  }

  function stop() {
    generation++
    currentId = ''
    clearTimer()
  }

  function follow(id: string) {
    stop()
    job.value = null
    currentId = id
    void poll(generation)
  }

  function visibilityChanged() {
    clearTimer()
    generation++
    if (!document.hidden && currentId && !terminal.has(job.value?.status || '')) { void poll(generation) }
  }

  document.addEventListener('visibilitychange', visibilityChanged)
  onScopeDispose(() => {
    stop()
    document.removeEventListener('visibilitychange', visibilityChanged)
  })
  return { job, error, follow, stop }
}
