import { springStep } from './model.mjs'

// Research controller: one pending frame; wall-clock analytic integration even after a long frame.
// Separate target/state from the visual clamp; dispose is terminal.
export function createSpringController({ now, request, cancel, draw, settled = () => {}, reduced = () => false }) {
  let state = { x: 0, v: 0 }, target = 0, last = now(), frame = null, disposed = false
  const snapshot = () => ({ ...state, target, pending: frame !== null })
  const stop = () => { if (frame !== null) cancel(frame); frame = null }
  const advance = (time) => {
    const next = Math.max(last, time)
    state = springStep(state, target, (next - last) / 1000)
    last = next
  }
  const finish = () => { stop(); state = { x: target, v: 0 }; last = now(); draw(snapshot()); settled(snapshot()) }
  const tick = time => {
    frame = null
    if (disposed) return
    advance(time)
    if (Math.abs(state.x-target)<.001 && Math.abs(state.v)<.01) finish()
    else { frame=request(tick); draw(snapshot()) }
  }
  return {
    snapshot,
    retarget(value) {
      if (disposed) return
      advance(now())
      target = value
      if (reduced()) { finish(); return }
      if (frame === null) frame = request(tick)
      draw(snapshot())
    },
    settle() { if (!disposed) finish() },
    dispose() { stop(); disposed = true },
  }
}

// Native FLIP: read the current painted rect BEFORE cancellation, then invert from new layout.
// The animated element must be dedicated to this transform; zoom belongs on its child.
export function createFlipController(element, { reduced = () => false, duration = 220 } = {}) {
  let active = null
  function cancel() {
    if (active) { const animation=active; active=null; animation.cancel() }
  }
  function transition(change) {
    const first = element.getBoundingClientRect()
    cancel()
    change()
    const last = element.getBoundingClientRect()
    if (reduced() || typeof element.animate !== 'function' || !last.width || !last.height) return
    const transform = `translate(${first.left-last.left}px, ${first.top-last.top}px) scale(${first.width/last.width}, ${first.height/last.height})`
    const animation = element.animate([{ transform, transformOrigin:'0 0' }, { transform:'none', transformOrigin:'0 0' }], { duration, easing:'cubic-bezier(0.22, 1, 0.36, 1)', fill:'both' })
    active=animation
    animation.onfinish=()=>{ if(active===animation) { active=null; animation.cancel() } }
    animation.oncancel=()=>{ if(active===animation) active=null }
  }
  return { transition, cancel, pending:()=>active!==null }
}
