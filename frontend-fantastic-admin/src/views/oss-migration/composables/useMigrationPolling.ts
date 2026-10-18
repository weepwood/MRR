import type { Ref } from 'vue'
import type { MigrationJob } from '@/api/modules/oss'
import { onScopeDispose } from 'vue'
import { getMigrationJob } from '@/api/modules/oss'

const TERMINAL_STATUSES = new Set(['cancelled', 'interrupted', 'completed', 'completed_with_errors', 'failed'])

/** Own only this page's timers; API, permissions and backend task state stay unchanged. */
export function useMigrationPolling(
  currentJob: Ref<MigrationJob | undefined>,
  refreshAll: () => Promise<void>,
  refreshOverview: () => Promise<void>,
) {
  let jobTimer: ReturnType<typeof setInterval> | undefined
  let overviewTimer: ReturnType<typeof setInterval> | undefined
  let jobBusy = false
  let overviewBusy = false
  let generation = 0
  let disposed = false

  function stopPolling() {
    generation++
    clearInterval(jobTimer)
    jobTimer = undefined
  }

  function startPolling() {
    if (disposed || jobTimer !== undefined || currentJob.value?.id == null) {
      return
    }
    const started = ++generation
    jobTimer = setInterval(async () => {
      const jobId = currentJob.value?.id
      if (jobId == null) {
        stopPolling()
        return
      }
      if (jobBusy) {
        return
      }
      jobBusy = true
      try {
        const response = await getMigrationJob(jobId)
        if (disposed || generation !== started || currentJob.value?.id !== jobId) {
          return
        }
        if (response.data) {
          currentJob.value = response.data
        }
        if (TERMINAL_STATUSES.has(currentJob.value?.status ?? '')) {
          stopPolling()
          await refreshAll()
        }
      }
      catch {
        if (generation === started) {
          stopPolling()
        }
      }
      finally {
        jobBusy = false
      }
    }, 2_000)
  }

  function stopOverviewRefresh() {
    clearInterval(overviewTimer)
    overviewTimer = undefined
  }

  function startOverviewRefresh() {
    stopOverviewRefresh()
    if (disposed) {
      return
    }
    overviewTimer = setInterval(async () => {
      if (overviewBusy) {
        return
      }
      overviewBusy = true
      try {
        await refreshOverview()
      }
      catch {
        // The page owns notifications; allow the next scheduled refresh.
      }
      finally {
        overviewBusy = false
      }
    }, 15_000)
  }

  onScopeDispose(() => {
    disposed = true
    stopPolling()
    stopOverviewRefresh()
  })

  return { startPolling, stopPolling, startOverviewRefresh, stopOverviewRefresh }
}
