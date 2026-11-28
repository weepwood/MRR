import type { DashboardSnapshot, DashboardSource } from './dashboard-data'
import { nextTick, onActivated, onDeactivated, onMounted, onScopeDispose, reactive, watch } from 'vue'
import { loadDashboardSource } from './dashboard-data'

export interface SourceState { status: 'idle' | 'loading' | 'ready' | 'error' | 'stale', snapshot?: DashboardSnapshot, at?: number }
const keys = ['statistics', 'health'] as const
const ttl: Record<DashboardSource, number> = { statistics: 10 * 60_000, health: 60_000 }

export function useDashboardData(session: () => number, sources: () => DashboardSource[]) {
  const states = reactive<Record<DashboardSource, SourceState>>({ statistics: { status: 'idle' }, health: { status: 'idle' } })
  const pending = new Map<DashboardSource, { controller: AbortController, request: Promise<void> }>()
  let active = false
  let timer: ReturnType<typeof setInterval> | undefined
  const allowed = (source: DashboardSource) => active && !document.hidden && sources().includes(source)
  function expire() {
    for (const source of keys) {
      const state = states[source]
      if (state.status === 'ready' && state.at !== undefined && Date.now() - state.at >= ttl[source]) { state.status = 'stale' }
    }
  }
  function cancel(source: DashboardSource, clear = false) {
    const task = pending.get(source)
    pending.delete(source)
    task?.controller.abort()
    if (clear) { states[source] = { status: 'idle' } }
    else if (states[source].status === 'loading') { states[source].status = states[source].snapshot ? 'stale' : 'idle' }
  }
  function refresh(source: DashboardSource) {
    if (!allowed(source)) { return Promise.resolve() }
    const existing = pending.get(source)
    if (existing) { return existing.request }
    expire()
    const state = states[source]
    if (state.status === 'ready') { return Promise.resolve() }
    const controller = new AbortController()
    const valid = () => pending.get(source)?.controller === controller && allowed(source) && !controller.signal.aborted
    state.status = 'loading'
    const request = loadDashboardSource(source, controller.signal).then((snapshot) => {
      if (valid()) { states[source] = { status: 'ready', snapshot, at: Date.now() } }
    }).catch(() => {
      if (valid()) { states[source] = { ...states[source], status: 'error' } }
    }).finally(() => {
      if (pending.get(source)?.controller === controller) {
        pending.delete(source)
        if (states[source].status === 'loading') { states[source].status = states[source].snapshot ? 'stale' : 'idle' }
      }
    })
    pending.set(source, { controller, request })
    return request
  }
  function sync() {
    expire()
    if (document.hidden) { keys.forEach(source => cancel(source)); return }
    // One bounded health probe per active cache lifetime; no network polling.
    if (allowed('health') && states.health.status === 'idle') { void refresh('health') }
  }
  function start() {
    active = true
    if (timer === undefined) { timer = setInterval(expire, 15_000) }
    sync()
  }
  function stop() {
    active = false
    if (timer !== undefined) { clearInterval(timer) }
    timer = undefined
    keys.forEach(source => cancel(source))
  }
  watch(session, () => { keys.forEach(source => cancel(source, true)); void nextTick(sync) }, { flush: 'sync' })
  watch(() => sources().join(','), () => {
    keys.filter(source => !sources().includes(source)).forEach(source => cancel(source, true))
    void nextTick(sync)
  }, { flush: 'sync' })
  onMounted(() => { document.addEventListener('visibilitychange', sync); start() })
  onActivated(start)
  onDeactivated(stop)
  onScopeDispose(() => { stop(); document.removeEventListener('visibilitychange', sync) })
  return { states, refresh }
}
