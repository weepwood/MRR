export interface DisclosureSpringState {
  x: number
  v: number
}

/** Critical damping; advance over actual elapsed time rather than truncating a long frame. */
export function advanceDisclosureSpring(state: DisclosureSpringState, target: number, dt: number): DisclosureSpringState {
  const omega = 24
  const y = state.x - target
  const b = state.v + omega * y
  const decay = Math.exp(-omega * dt)
  return { x: target + (y + b * dt) * decay, v: (state.v - omega * b * dt) * decay }
}
