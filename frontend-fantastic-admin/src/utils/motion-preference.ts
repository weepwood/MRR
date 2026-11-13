/**
 * 应用开关和系统减少动态效果偏好共同决定是否播放空间动效。
 */
export function isMotionEnabled(): boolean {
  const appEnabled = typeof document === 'undefined' || document.documentElement.dataset.motion !== 'off'
  const reduced = typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
  return appEnabled && !reduced
}
