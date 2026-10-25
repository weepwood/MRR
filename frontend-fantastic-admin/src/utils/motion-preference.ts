/**
 * AppConfigPanel 中的动态效果偏好；缺省即开启，独立于操作系统动画偏好。
 */
export function isMotionEnabled(): boolean {
  return typeof document === 'undefined' || document.documentElement.dataset.motion !== 'off'
}
