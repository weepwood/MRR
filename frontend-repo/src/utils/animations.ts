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
