/**
 * Preserve Home/End navigation on the migrated Element Plus tablist.
 * Native arrow navigation remains owned by Element Plus.
 */
export function handleSegmentedTabKey(event: KeyboardEvent) {
  if (event.key !== 'Home' && event.key !== 'End') { return }
  if (!(event.target instanceof HTMLElement) || event.target.getAttribute('role') !== 'tab') { return }
  const tablist = event.target.closest('[role="tablist"]')
  if (!tablist) { return }
  const tabs = Array.from(tablist.querySelectorAll<HTMLElement>('[role="tab"]:not(.is-disabled):not([aria-disabled="true"])'))
  const target = event.key === 'Home' ? tabs[0] : tabs[tabs.length - 1]
  if (!target) { return }
  event.preventDefault()
  event.stopPropagation()
  target.focus({ preventScroll: true })
  target.click()
}
