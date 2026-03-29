import gsap from 'gsap'

/**
 * Simplified Animation Library (GSAP powered)
 * Streamlined motion patterns for better performance.
 */

/**
 * Standard staggered entrance for lists or grids.
 */
export const animateStaggeredEntrance = (elements: string | Element[], delay = 0.2) => {
  return gsap.from(elements, {
    y: 10,
    opacity: 0,
    duration: 0.5,
    stagger: 0.05,
    delay,
    ease: 'power2.out',
    clearProps: 'all'
  })
}

/**
 * Grow animation for SVG Rect bars.
 */
export const animateSvgBars = (elements: string | Element[]) => {
  const bars = typeof elements === 'string' ? document.querySelectorAll(elements) : elements
  
  bars.forEach((bar) => {
    const targetHeight = Number(bar.getAttribute('height'))
    const targetY = Number(bar.getAttribute('y'))
    const baselineY = targetY + targetHeight

    // Start from zero height at the baseline
    gsap.fromTo(bar, 
      { attr: { height: 0, y: baselineY } },
      { 
        attr: { height: targetHeight, y: targetY },
        duration: 0.8,
        ease: 'power2.out'
      }
    )
  })
}

/**
 * Draw animation for SVG lines and paths.
 */
export const animateSvgPath = (element: SVGPathElement | SVGPolylineElement, duration = 1.5) => {
  if (!element) return
  
  const length = element instanceof SVGPathElement ? element.getTotalLength() : 1000 // Fallback for polyline if needed
  
  gsap.fromTo(element, 
    { strokeDasharray: length, strokeDashoffset: length },
    { strokeDashoffset: 0, duration, ease: 'power2.inOut' }
  )
}

/**
 * Simple Sidebar Reveal.
 */
export const revealSidebar = (element: string | Element) => {
  return gsap.from(element, {
    x: -256,
    opacity: 0,
    duration: 0.6,
    ease: 'power2.out',
    clearProps: 'all'
  })
}

/**
 * Simple Hero Reveal.
 */
export const revealHero = (element: string | Element) => {
  return gsap.from(element, {
    y: 20,
    opacity: 0,
    duration: 0.5,
    ease: 'power2.out',
    clearProps: 'all'
  })
}

/**
 * Simple Staggered Grid Entrance.
 */
export const revealStaggeredGrid = (elements: string | Element[], delay = 0.2) => {
  return gsap.from(elements, {
    y: 15,
    opacity: 0,
    duration: 0.4,
    stagger: 0.08,
    ease: 'power2.out',
    delay,
    clearProps: 'all'
  })
}

/**
 * Subtle Magnetic Hover Interaction.
 */
export const applyMagneticEffect = (element: Element, strength = 0.02) => {
  const onMouseMove = (e: MouseEvent) => {
    const rect = element.getBoundingClientRect()
    const centerX = rect.left + rect.width / 2
    const centerY = rect.top + rect.height / 2
    const deltaX = (e.clientX - centerX) * strength
    const deltaY = (e.clientY - centerY) * strength

    gsap.to(element, {
      x: deltaX,
      y: deltaY,
      duration: 0.3,
      ease: 'power2.out'
    })
  }

  const onMouseLeave = () => {
    gsap.to(element, {
      x: 0,
      y: 0,
      duration: 0.3,
      ease: 'power2.out'
    })
  }

  element.addEventListener('mousemove', onMouseMove as any)
  element.addEventListener('mouseleave', onMouseLeave as any)

  return () => {
    element.removeEventListener('mousemove', onMouseMove as any)
    element.removeEventListener('mouseleave', onMouseLeave as any)
  }
}

/**
 * Simple Page Transition.
 */
export const animatePageTransition = (element: string | Element) => {
  return gsap.fromTo(element, 
    { opacity: 0 },
    { opacity: 1, duration: 0.3, ease: 'power1.out', clearProps: 'all' }
  )
}

/**
 * Counter animation for numbers.
 */
export const animateCounter = (element: HTMLElement, endValue: number, duration = 1.0) => {
  const obj = { value: 0 }
  return gsap.to(obj, {
    value: endValue,
    duration,
    ease: 'power2.out',
    onUpdate: () => {
      element.innerText = Math.floor(obj.value).toLocaleString('zh-CN')
    }
  })
}