import type { Router } from 'vue-router'
import { animate } from 'motion-v'
import { entranceDelay, motionDurations, motionEasings, motionSprings } from './presets'

type Cleanup = () => void

interface SettingsNavState {
  indicator: HTMLElement
  resizeObserver?: ResizeObserver
  resizeHandler?: () => void
}

const enteredElements = new WeakSet<Element>()
const boundQuickActions = new WeakSet<Element>()
const settingsNavStates = new WeakMap<HTMLElement, SettingsNavState>()

function prefersReducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function animateHomeCards(root: ParentNode) {
  const cards = Array.from(root.querySelectorAll<HTMLElement>('.home-page .stat-card'))
  cards.forEach((card, index) => {
    if (enteredElements.has(card)) {
      return
    }
    enteredElements.add(card)
    if (prefersReducedMotion()) {
      return
    }
    card.style.willChange = 'transform, opacity'
    void animate(
      card,
      { opacity: [0, 1], y: [8, 0] },
      {
        duration: motionDurations.standard,
        delay: entranceDelay(index),
        ease: motionEasings.emphasized,
      },
    )
    window.setTimeout(() => card.style.removeProperty('will-change'), 450)
  })
}

function animateTimelineGroups(root: ParentNode) {
  const groups = Array.from(root.querySelectorAll<HTMLElement>('.timeline-group-children'))
  groups.forEach((group) => {
    if (enteredElements.has(group)) {
      return
    }
    enteredElements.add(group)
    if (prefersReducedMotion()) {
      return
    }
    void animate(
      group,
      { opacity: [0, 1], y: [-4, 0] },
      { duration: motionDurations.standard, ease: motionEasings.emphasized },
    )
    const entries = Array.from(group.querySelectorAll<HTMLElement>('.timeline-entry'))
    entries.forEach((entry, index) => {
      void animate(
        entry,
        { opacity: [0, 1], x: [-4, 0] },
        {
          duration: motionDurations.fast,
          delay: entranceDelay(index, 0.025, 0.12),
          ease: motionEasings.emphasized,
        },
      )
    })
  })
}

function bindQuickAction(action: HTMLElement, cleanups: Set<Cleanup>) {
  if (boundQuickActions.has(action)) {
    return
  }
  boundQuickActions.add(action)
  const icon = action.querySelector<HTMLElement>('i')
  if (!icon) {
    return
  }

  const moveIcon = (scale: number, y: number) => {
    if (prefersReducedMotion()) {
      return
    }
    void animate(icon, { scale, y }, motionSprings.interaction)
  }
  const enter = () => moveIcon(1.08, -1)
  const leave = () => moveIcon(1, 0)
  const press = () => moveIcon(0.94, 0)
  const release = () => moveIcon(1.08, -1)

  action.addEventListener('pointerenter', enter)
  action.addEventListener('pointerleave', leave)
  action.addEventListener('pointerdown', press)
  action.addEventListener('pointerup', release)
  action.addEventListener('pointercancel', leave)
  action.addEventListener('focusin', enter)
  action.addEventListener('focusout', leave)

  cleanups.add(() => {
    action.removeEventListener('pointerenter', enter)
    action.removeEventListener('pointerleave', leave)
    action.removeEventListener('pointerdown', press)
    action.removeEventListener('pointerup', release)
    action.removeEventListener('pointercancel', leave)
    action.removeEventListener('focusin', enter)
    action.removeEventListener('focusout', leave)
  })
}

function bindQuickActions(root: ParentNode, cleanups: Set<Cleanup>) {
  root.querySelectorAll<HTMLElement>('.home-page .quick-item').forEach(action => bindQuickAction(action, cleanups))
}

function positionSettingsIndicator(nav: HTMLElement, state: SettingsNavState, immediate = false) {
  const active = nav.querySelector<HTMLElement>('.settings-nav-item.active')
  if (!active) {
    state.indicator.hidden = true
    return
  }

  state.indicator.hidden = false
  state.indicator.classList.toggle('danger', active.classList.contains('danger'))
  const navRect = nav.getBoundingClientRect()
  const activeRect = active.getBoundingClientRect()
  const x = activeRect.left - navRect.left + nav.scrollLeft
  const y = activeRect.top - navRect.top + nav.scrollTop
  const width = activeRect.width
  const height = activeRect.height

  if (immediate || prefersReducedMotion()) {
    state.indicator.style.width = `${width}px`
    state.indicator.style.height = `${height}px`
    state.indicator.style.transform = `translate3d(${x}px, ${y}px, 0)`
    return
  }

  void animate(
    state.indicator,
    { x, y, width, height },
    motionSprings.layout,
  )
}

function bindSettingsNav(nav: HTMLElement, cleanups: Set<Cleanup>) {
  let state = settingsNavStates.get(nav)
  if (!state) {
    const indicator = document.createElement('span')
    indicator.className = 'mrr-motion-settings-indicator'
    indicator.setAttribute('aria-hidden', 'true')
    nav.prepend(indicator)
    state = { indicator }
    settingsNavStates.set(nav, state)
    positionSettingsIndicator(nav, state, true)

    if (typeof ResizeObserver !== 'undefined') {
      state.resizeObserver = new ResizeObserver(() => positionSettingsIndicator(nav, state!))
      state.resizeObserver.observe(nav)
    }
    else {
      state.resizeHandler = () => positionSettingsIndicator(nav, state!)
      window.addEventListener('resize', state.resizeHandler, { passive: true })
    }

    cleanups.add(() => {
      state?.resizeObserver?.disconnect()
      if (state?.resizeHandler) {
        window.removeEventListener('resize', state.resizeHandler)
      }
      state?.indicator.remove()
    })
  }
  positionSettingsIndicator(nav, state)
}

function bindSettingsNavigation(root: ParentNode, cleanups: Set<Cleanup>) {
  root.querySelectorAll<HTMLElement>('.settings-nav').forEach(nav => bindSettingsNav(nav, cleanups))
}

export function installMotionEnhancements(router: Router) {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return () => undefined
  }

  const root = document.querySelector<HTMLElement>('#app')
  if (!root) {
    return () => undefined
  }

  document.documentElement.classList.add('mrr-motion-enabled')
  const cleanups = new Set<Cleanup>()
  let scanFrame: number | undefined

  const scan = () => {
    scanFrame = undefined
    animateHomeCards(root)
    bindQuickActions(root, cleanups)
    animateTimelineGroups(root)
    bindSettingsNavigation(root, cleanups)
  }
  const scheduleScan = () => {
    if (scanFrame !== undefined) {
      return
    }
    scanFrame = window.requestAnimationFrame(scan)
  }

  const observer = new MutationObserver(scheduleScan)
  observer.observe(root, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ['class'],
  })
  cleanups.add(() => observer.disconnect())
  cleanups.add(router.afterEach(scheduleScan))

  const reducedMotionMedia = window.matchMedia('(prefers-reduced-motion: reduce)')
  const handleMotionPreference = scheduleScan
  reducedMotionMedia.addEventListener('change', handleMotionPreference)
  cleanups.add(() => reducedMotionMedia.removeEventListener('change', handleMotionPreference))

  scheduleScan()

  return () => {
    if (scanFrame !== undefined) {
      window.cancelAnimationFrame(scanFrame)
    }
    cleanups.forEach(cleanup => cleanup())
    cleanups.clear()
    document.documentElement.classList.remove('mrr-motion-enabled')
  }
}
