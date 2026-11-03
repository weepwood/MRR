import type { Page } from '@playwright/test'

export async function sampleAuthModeSwitch(page: Page, reverseInFlight = false) {
  return page.evaluate(async (reverseInFlight) => {
    const shell = document.querySelector<HTMLElement>('.login-shell')!
    const switcher = document.querySelector<HTMLElement>('.auth-mode-switch')!
    const tabs = switcher.querySelectorAll<HTMLButtonElement>('button')
    const nextFrame = () => new Promise(requestAnimationFrame)
    const translateX = (style: CSSStyleDeclaration) => new DOMMatrixReadOnly(style.transform).m41
    const geometry = () => {
      const box = shell.getBoundingClientRect()
      return { top: box.top, height: box.height, bottom: box.bottom, documentHeight: document.documentElement.scrollHeight }
    }
    await Promise.all(shell.getAnimations().map(animation => animation.finished))

    async function sample(mode: 'login' | 'register') {
      const previous = new Set(document.getAnimations())
      const before = geometry()
      tabs[mode === 'register' ? 1 : 0].click()
      const paused = new Set<Animation>()
      const transitions: (CSSTransition | undefined)[] = []
      const findTransform = (element?: Element | null, subtree = false) => element?.getAnimations({ subtree })
        .find((animation): animation is CSSTransition => animation instanceof CSSTransition
          && animation.transitionProperty === 'transform' && !previous.has(animation))

      try {
        const deadline = performance.now() + 1500
        let incoming: HTMLElement | null = null
        let outgoing: HTMLElement | null = null
        while (performance.now() < deadline) {
          await nextFrame()
          incoming = document.querySelector<HTMLElement>(`.${mode}-form`)
          outgoing = document.querySelector<HTMLElement>(mode === 'register' ? '.login-form' : '.register-form')
          transitions[0] ??= findTransform(incoming)
          transitions[1] ??= findTransform(outgoing)
          transitions[2] ??= findTransform(switcher, true)
          for (const animation of transitions) {
            if (animation && !paused.has(animation)) {
              animation.pause()
              paused.add(animation)
            }
          }
          if (transitions[0] && transitions[1] && transitions[2]) {
            break
          }
        }
        const animations = [...paused]
        if (animations.length !== 3 || !incoming || !outgoing) {
          throw new Error(`Expected three transform transitions while switching to ${mode}; found ${animations.length}`)
        }
        await Promise.all(animations.map(animation => animation.ready))

        const incomingAction = incoming.querySelector('button[type="submit"]')!
        const outgoingAction = outgoing.querySelector('button[type="submit"]')!
        const frames = [0, 0.2, 0.35].map((progress) => {
          // Seek native CSS transitions so a delayed frame cannot skip the evidence.
          for (const animation of animations) {
            animation.currentTime = Number(animation.effect!.getComputedTiming().duration) * progress
          }
          const enter = getComputedStyle(incoming)
          const leave = getComputedStyle(outgoing)
          const indicator = getComputedStyle(switcher, '::before')
          return {
            ...geometry(),
            actionTopDelta: incomingAction.getBoundingClientRect().top - outgoingAction.getBoundingClientRect().top,
            indicatorX: translateX(indicator),
            enterX: translateX(enter),
            leaveX: translateX(leave),
            properties: [enter.transitionProperty, leave.transitionProperty],
            timings: [enter.transitionTimingFunction, leave.transitionTimingFunction, indicator.transitionTimingFunction],
            durations: [enter.transitionDuration, leave.transitionDuration, indicator.transitionDuration],
            pageOverflows: document.documentElement.scrollWidth > innerWidth,
            inlineSizing: [shell.style.height, shell.style.minHeight, shell.style.transition],
          }
        })
        return { before, frames, animations }
      }
      finally {
        for (const animation of paused) {
          animation.play()
        }
      }
    }

    const forward = await sample('register')
    if (!reverseInFlight) {
      await Promise.all(forward.animations.map(animation => animation.finished))
      await nextFrame()
    }
    const registered = geometry()
    const backward = await sample('login')
    return { forward: forward.frames, backward: backward.frames, initial: forward.before, registered }
  }, reverseInFlight)
}
