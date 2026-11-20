/**
 * 全屏查看器关闭时的焦点选择守卫。
 *
 * ElImageViewer 内部用 focus-trap，释放陷阱时会把焦点还原到「打开前」聚焦的卡片，
 * 卡片的 @focus 处理器会随之把预览切回旧图（例：P1 聚焦后键盘切到 P5，
 * 全屏关闭即回跳 P1）。还原时机在查看器卸载流程内（早于 nextTick），
 * 无法在关闭后抢先纠正，只能在源头让卡片 @focus 在还原窗口内不触发切换。
 *
 * 只拦 @focus 驱动的切换：点击（@click）与页面键盘导航（navigate → selectImage）
 * 不经过这里，守卫窗口内仍然可用。
 */

let suppressedUntil = 0

export function suppressFocusSelect(durationMs = 600): void {
  suppressedUntil = Date.now() + durationMs
}

export function isFocusSelectSuppressed(): boolean {
  return Date.now() < suppressedUntil
}
