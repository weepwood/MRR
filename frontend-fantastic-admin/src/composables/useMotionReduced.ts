/**
 * useMotionReduced —— 统一的「是否减少动画」判断（ADR-0007 局部增强层）
 *
 * 默认尊重系统 prefers-reduced-motion（无障碍优先）。
 *
 * 演示 / 预览场景：嵌入式 WebView、远程预览、RDP 等环境往往把
 * prefers-reduced-motion 默认设为 reduce，导致动画被静默降级、看不到效果。
 * 提供两种「强制播放」入口，便于验证优化成果，而不破坏默认无障碍行为：
 *   1. URL 带 ?motion=force
 *   2. localStorage 置 mrr-force-motion=1（控制台执行
 *      localStorage.setItem('mrr-force-motion','1') 后刷新）
 */
export function useMotionReduced(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false
  }
  try {
    const params = new URLSearchParams(window.location.search)
    if (params.get('motion') === 'force') return false
    if (localStorage.getItem('mrr-force-motion') === '1') return false
  } catch {
    /* 访问 localStorage / URLSearchParams 失败时不强制，回退到系统设置 */
  }
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}
