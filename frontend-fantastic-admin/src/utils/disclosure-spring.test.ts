import { describe, expect, it } from 'vitest'
import { advanceDisclosureSpring } from './disclosure-spring'

describe('disclosure spring', () => {
  it('preserves position and velocity at retargeting', () => {
    const state = advanceDisclosureSpring({ x: 0, v: 0 }, 160, 0.075)
    expect(state.x).toBeGreaterThan(0)
    expect(state.v).toBeGreaterThan(0)
    expect(advanceDisclosureSpring(state, 0, 0)).toEqual(state)
    expect(advanceDisclosureSpring(state, 0, 0.02).v).toBeLessThan(state.v)
  })
  it('uses elapsed time consistently even after long frames', () => {
    const a = advanceDisclosureSpring({ x: 0, v: 0 }, 160, 0.24)
    const b = advanceDisclosureSpring(advanceDisclosureSpring({ x: 0, v: 0 }, 160, 0.12), 160, 0.12)
    expect(a.x).toBeCloseTo(b.x, 10)
    expect(a.v).toBeCloseTo(b.v, 10)
    const c = advanceDisclosureSpring(a, 160, 2)
    expect(c.x).toBeCloseTo(160, 8)
    expect(c.v).toBeCloseTo(0, 8)
  })
})
