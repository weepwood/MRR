// A visual budget, never a substitute for the router/session readiness contract.
export const STARTUP_VISUAL_BUDGET = 450

function bounded<T>(work: Promise<T>, ms: number): Promise<boolean> {
  return new Promise((resolve) => {
    const timer = window.setTimeout(resolve, ms, false)
    work.then(() => {
      window.clearTimeout(timer)
      resolve(true)
    }, () => {
      window.clearTimeout(timer)
      resolve(false)
    })
  })
}

/** Wait for used fonts, not all font faces or business API requests. */
export async function waitForStartupVisualReady(initialAppearance: Promise<unknown>): Promise<void> {
  const deadline = performance.now() + STARTUP_VISUAL_BUDGET
  const root = document.documentElement
  const remaining = () => Math.max(0, deadline - performance.now())
  // The mounted app is hidden, but style/layout must run to discover its used fonts.
  document.getElementById('app')?.getBoundingClientRect()
  if (document.fonts) {
    await bounded(Promise.all([document.fonts.ready, initialAppearance]), remaining())
  }
  else {
    await bounded(initialAppearance, remaining())
  }
  await nextTick()
  document.getElementById('app')?.getBoundingClientRect()
  if (document.fonts && document.fonts.status === 'loading') {
    const loaded = await bounded(document.fonts.ready, remaining())
    if (!loaded) {
      // Freeze the system fallback for this document; late font arrival must not reflow it.
      root.dataset.mrrStartupFont = 'system'
    }
  }
  await nextTick()
  // The animation frames are bounded too (background tabs can suspend rAF).
  await bounded(new Promise<void>((resolve) => {
    requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
  }), 80)
}
