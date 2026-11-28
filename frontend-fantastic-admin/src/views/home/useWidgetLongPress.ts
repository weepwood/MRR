import { onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue'

interface Point { x: number, y: number }
interface Slot { id: string, left: number, top: number, right: number, bottom: number }
interface Gesture extends Point { id: string, pointer: number, touch: boolean, active: boolean, element: HTMLElement }
const interactive = 'button, input, textarea, select, a, [role="button"], [role="link"], [contenteditable="true"], .el-popper, .dashboard-widget__content'

export function useWidgetLongPress(options: {
  start: (id: string) => boolean
  move: (id: string) => boolean
  drop: (id: string) => void
  end: () => void
}) {
  let gesture: Gesture | undefined
  let timer: ReturnType<typeof setTimeout> | undefined
  let suppressUntil = 0
  let frame = 0
  let pendingPoint: Point | undefined
  let lastPoint: Point | undefined
  let acceptedSlot: Slot | undefined
  const point = ref<Point | null>(null)
  const active = ref(false)

  // FLIP transforms describe the visual position, not the destination grid slot.
  // Hit-test the destination geometry so moving siblings cannot steal the pointer.
  function slotAt(x: number, y: number): Slot | undefined {
    const grid = gesture?.element.parentElement
    if (!grid || !grid.classList.contains('dashboard-grid')) { return }
    const hit = document.elementFromPoint(x, y)
    if (!hit || !grid.contains(hit)) { return }
    for (const card of Array.from(grid.querySelectorAll<HTMLElement>(':scope > [data-widget-id]'))) {
      const rect = card.getBoundingClientRect()
      const transform = getComputedStyle(card).transform
      const matrix = transform === 'none' ? undefined : new DOMMatrixReadOnly(transform)
      const left = rect.left - (matrix?.m41 ?? 0)
      const top = rect.top - (matrix?.m42 ?? 0)
      const slot = { id: card.dataset.widgetId!, left, top, right: left + rect.width, bottom: top + rect.height }
      if (contains(slot, x, y)) { return slot }
    }
  }
  function contains(slot: Slot, x: number, y: number, margin = 0) {
    return x >= slot.left - margin && x <= slot.right + margin && y >= slot.top - margin && y <= slot.bottom + margin
  }
  function updatePreview() {
    frame = 0
    const next = pendingPoint
    pendingPoint = undefined
    if (!next || !gesture?.active) { return }
    point.value = next
    if (lastPoint && lastPoint.x === next.x && lastPoint.y === next.y) { return }
    lastPoint = next
    // Hold the accepted slot until the pointer exits it. Layout movement alone
    // must never trigger a second swap or a stationary-pointer oscillation.
    if (acceptedSlot && contains(acceptedSlot, next.x, next.y, 4)) { return }
    acceptedSlot = undefined
    const slot = slotAt(next.x, next.y)
    if (options.move(slot?.id ?? '') && slot) { acceptedSlot = slot }
  }
  function cancel() {
    if (timer !== undefined) { clearTimeout(timer) }
    timer = undefined
    if (frame) { cancelAnimationFrame(frame) }
    frame = 0
    pendingPoint = undefined
    lastPoint = undefined
    acceptedSlot = undefined
    if (gesture?.active) {
      suppressUntil = performance.now() + 600
      options.end()
    }
    gesture = undefined
    point.value = null
    active.value = false
  }
  function begin(event: Event, id: string, x: number, y: number, pointer: number, touch: boolean) {
    if (gesture) { cancel(); return }
    suppressUntil = 0
    const target = event.target
    if (!(target instanceof Element)) { return }
    const control = target.closest(interactive)
    if (control && control !== event.currentTarget) { return }
    const element = event.currentTarget
    if (!(element instanceof HTMLElement)) { return }
    gesture = { id, x, y, pointer, touch, active: false, element }
    timer = setTimeout(() => {
      if (!gesture || !gesture.element.isConnected || !options.start(id)) { cancel(); return }
      gesture.active = true
      active.value = true
      point.value = { x, y }
      lastPoint = { x, y }
    }, 350)
  }
  function move(x: number, y: number, event: Event) {
    if (!gesture) { return }
    if (!gesture.active) {
      if (Math.hypot(x - gesture.x, y - gesture.y) > 9) { cancel() }
      return
    }
    if (event.cancelable) { event.preventDefault() }
    pendingPoint = { x, y }
    if (!frame) { frame = requestAnimationFrame(updatePreview) }
  }
  function finish(x: number, y: number) {
    if (gesture?.active) {
      if (frame) { cancelAnimationFrame(frame) }
      pendingPoint = { x, y }
      updatePreview()
      // Release commits the existing preview, never performs a second reorder.
      // A release outside the grid or on an incompatible group cancels.
      const slot = slotAt(x, y)
      options.drop(slot?.id ?? (acceptedSlot && contains(acceptedSlot, x, y) && gesture.element.parentElement?.contains(document.elementFromPoint(x, y)) ? acceptedSlot.id : ''))
    }
    cancel()
  }
  function pointerDown(event: PointerEvent, id: string) {
    if (event.pointerType === 'touch' || event.button !== 0 || !event.isPrimary) { return }
    begin(event, id, event.clientX, event.clientY, event.pointerId, false)
  }
  function touchStart(event: TouchEvent, id: string) {
    if (event.touches.length !== 1) { cancel(); return }
    const touch = event.changedTouches[0]
    begin(event, id, touch.clientX, touch.clientY, touch.identifier, true)
  }
  function pointerMove(event: PointerEvent) {
    if (gesture && !gesture.touch && event.pointerId === gesture.pointer) { move(event.clientX, event.clientY, event) }
  }
  function pointerUp(event: PointerEvent) {
    if (gesture && !gesture.touch && event.pointerId === gesture.pointer) { finish(event.clientX, event.clientY) }
  }
  function touchMove(event: TouchEvent) {
    if (!gesture?.touch) { return }
    if (event.touches.length !== 1) { cancel(); return }
    const touch = Array.from(event.changedTouches).find(item => item.identifier === gesture?.pointer)
    if (touch) { move(touch.clientX, touch.clientY, event) }
  }
  function touchEnd(event: TouchEvent) {
    if (!gesture?.touch) { return }
    const touch = Array.from(event.changedTouches).find(item => item.identifier === gesture?.pointer)
    if (touch) {
      if (gesture.active && event.cancelable) { event.preventDefault() }
      finish(touch.clientX, touch.clientY)
    }
  }
  function click(event: MouseEvent) {
    if (performance.now() < suppressUntil && event.detail !== 0) {
      event.preventDefault()
      event.stopImmediatePropagation()
      suppressUntil = 0
    }
  }
  function contextMenu(event: MouseEvent) {
    if (gesture?.active) { event.preventDefault() }
  }
  function key(event: KeyboardEvent) {
    if (event.key === 'Escape' && gesture) { cancel() }
  }
  function resetClick() {
    if (!gesture) { suppressUntil = 0 }
  }
  function pointerCancel(event: PointerEvent) {
    if (!gesture?.touch && event.pointerId === gesture?.pointer) { cancel() }
  }
  onMounted(() => {
    window.addEventListener('pointerdown', resetClick, true)
    window.addEventListener('pointermove', pointerMove, { passive: false })
    window.addEventListener('pointerup', pointerUp)
    window.addEventListener('pointercancel', pointerCancel)
    window.addEventListener('touchmove', touchMove, { passive: false })
    window.addEventListener('touchend', touchEnd, { passive: false })
    window.addEventListener('touchcancel', cancel)
    window.addEventListener('click', click, true)
    window.addEventListener('contextmenu', contextMenu, true)
    window.addEventListener('keydown', key)
    window.addEventListener('blur', cancel)
    document.addEventListener('visibilitychange', cancel)
  })
  onDeactivated(cancel)
  onBeforeUnmount(() => {
    cancel()
    window.removeEventListener('pointerdown', resetClick, true)
    window.removeEventListener('pointermove', pointerMove)
    window.removeEventListener('pointerup', pointerUp)
    window.removeEventListener('pointercancel', pointerCancel)
    window.removeEventListener('touchmove', touchMove)
    window.removeEventListener('touchend', touchEnd)
    window.removeEventListener('touchcancel', cancel)
    window.removeEventListener('click', click, true)
    window.removeEventListener('contextmenu', contextMenu, true)
    window.removeEventListener('keydown', key)
    window.removeEventListener('blur', cancel)
    document.removeEventListener('visibilitychange', cancel)
  })
  return { pointerDown, touchStart, cancel, active, point }
}
