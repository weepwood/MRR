import type { Ref } from 'vue'
import type { MigrationJob } from '@/api/modules/oss'
import { useDocumentVisibility } from '@vueuse/core'
import { onScopeDispose, ref, watch } from 'vue'
import { getMigrationJob } from '@/api/modules/oss'

const TERMINAL_STATUSES = new Set(['cancelled', 'interrupted', 'completed', 'completed_with_errors', 'failed'])

/** Own only this page's timers; API, permissions and backend task state stay unchanged. */
export function useMigrationPolling(
  currentJob: Ref<MigrationJob | undefined>,
  refreshAll: () => Promise<void>,
  refreshOverview: () => Promise<void>,
  active: Ref<boolean> = ref(true),
) {
  const visibility = useDocumentVisibility()
  let jobTimer: ReturnType<typeof setInterval> | undefined
  let overviewTimer: ReturnType<typeof setInterval> | undefined
  let jobBusy = false
  let overviewBusy = false
  let generation = 0
  let disposed = false
  let jobRequested = false
  let overviewRequested = false

  function canPoll() {
    return !disposed && active.value && visibility.value === 'visible'
  }

  function clearJobTimer() {
    generation++
    clearInterval(jobTimer)
    jobTimer = undefined
  }

  function stopPolling() {
    jobRequested = false
    clearJobTimer()
  }

  async function pollJob(started: number) {
    const jobId = currentJob.value?.id
    if (jobId == null) {
      stopPolling()
      return
    }
    if (!canPoll() || jobBusy || !jobRequested) {
      return
    }
    jobBusy = true
    try {
      const response = await getMigrationJob(jobId)
      if (!canPoll() || generation !== started || currentJob.value?.id !== jobId) {
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
      if (canPoll() && generation === started && currentJob.value?.id === jobId) {
        stopPolling()
      }
    }
    finally {
      jobBusy = false
    }
  }

  function startPolling() {
    if (disposed || currentJob.value?.id == null) {
      return
    }
    jobRequested = true
    if (!canPoll() || jobTimer !== undefined) {
      return
    }
    const started = ++generation
    jobTimer = setInterval(() => void pollJob(started), 2_000)
  }

  function clearOverviewTimer() {
    clearInterval(overviewTimer)
    overviewTimer = undefined
  }

  function stopOverviewRefresh() {
    overviewRequested = false
    clearOverviewTimer()
  }

  async function pollOverview() {
    if (!canPoll() || overviewBusy || !overviewRequested) {
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
  }

  function startOverviewRefresh() {
    if (disposed) {
      return
    }
    overviewRequested = true
    clearOverviewTimer()
    if (canPoll()) {
      overviewTimer = setInterval(() => void pollOverview(), 15_000)
    }
  }

  watch(() => active.value && visibility.value === 'visible', (visible) => {
    if (!visible) {
      // Suspend timers without discarding the user's polling choices.
      clearJobTimer()
      clearOverviewTimer()
      return
    }
    if (jobRequested) {
      startPolling()
      void pollJob(generation)
    }
    if (overviewRequested) {
      startOverviewRefresh()
      void pollOverview()
    }
  }, { flush: 'sync' })

  onScopeDispose(() => {
    disposed = true
    stopPolling()
    stopOverviewRefresh()
  })

  return { startPolling, stopPolling, startOverviewRefresh, stopOverviewRefresh }
}
