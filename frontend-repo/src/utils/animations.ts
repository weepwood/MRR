import gsap from 'gsap'

/**
 * Statistics Animation Library (GSAP powered)
 * Specialized motion patterns for data visualization.
 */

/**
 * Standard staggered entrance for lists or grids.
 */
export const animateStaggeredEntrance = (elements: string | Element[], delay = 0.2) => {
  return gsap.from(elements, {
    y: 20,
    opacity: 0,
    duration: 0.8,
    stagger: 0.1,
    delay,
    ease: 'power3.out',
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
        duration: 1.2,
        ease: 'elastic.out(1, 0.75)',
        stagger: 0.05
      }
    )
  })
}

/**
 * Draw animation for SVG lines and paths.
 */
export const animateSvgPath = (element: SVGPathElement | SVGPolylineElement, duration = 2) => {
  if (!element) return
  
  const length = element instanceof SVGPathElement ? element.getTotalLength() : 1000 // Fallback for polyline if needed
  
  gsap.fromTo(element, 
    { strokeDasharray: length, strokeDashoffset: length },
    { strokeDashoffset: 0, duration, ease: 'power2.inOut' }
  )
}

/**
 * Elegant Sidebar Reveal - wide-arc glide.
 */
export const revealSidebar = (element: string | Element) => {
  return gsap.from(element, {
    x: -256,
    opacity: 0,
    duration: 1.4,
    ease: 'expo.out'
  })
}

/**
 * Elegant Hero Reveal - soft scale-fade.
 */
export const revealHero = (element: string | Element) => {
  return gsap.from(element, {
    y: 40,
    scale: 0.98,
    opacity: 0,
    duration: 1.2,
    ease: 'power3.out',
    delay: 0.2
  })
}

/**
 * Wave-staggered Grid Entrance.
 */
export const revealStaggeredGrid = (elements: string | Element[], delay = 0.4) => {
  return gsap.from(elements, {
    y: 30,
    opacity: 0,
    duration: 1,
    stagger: {
      amount: 0.4,
      from: 'start',
      grid: 'auto'
    },
    ease: 'power2.out',
    delay
  })
}

/**
 * Ultra-subtle Magnetic Hover Interaction.
 */
export const applyMagneticEffect = (element: Element, strength = 0.05) => {
  const onMouseMove = (e: MouseEvent) => {
    const rect = element.getBoundingClientRect()
    const centerX = rect.left + rect.width / 2
    const centerY = rect.top + rect.height / 2
    const deltaX = (e.clientX - centerX) * strength
    const deltaY = (e.clientY - centerY) * strength

    gsap.to(element, {
      x: deltaX,
      y: deltaY,
      rotateX: -deltaY * 0.5,
      rotateY: deltaX * 0.5,
      duration: 0.6,
      ease: 'power2.out'
    })
  }

  const onMouseLeave = () => {
    gsap.to(element, {
      x: 0,
      y: 0,
      rotateX: 0,
      rotateY: 0,
      duration: 0.8,
      ease: 'elastic.out(1, 0.5)'
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
 * Fluid Page Transition.
 */
export const animatePageTransition = (element: string | Element) => {
  return gsap.fromTo(element, 
    { opacity: 0, x: 10 },
    { opacity: 1, x: 0, duration: 0.6, ease: 'power3.out', clearProps: 'all' }
  )
}

/**
 * Counter animation for numbers.
 */
export const animateCounter = (element: HTMLElement, endValue: number, duration = 1.6) => {
  const obj = { value: 0 }
  return gsap.to(obj, {
    value: endValue,
    duration,
    ease: 'power3.out',
    onUpdate: () => {
      element.innerText = Math.floor(obj.value).toLocaleString('zh-CN')
    }
  })
}
