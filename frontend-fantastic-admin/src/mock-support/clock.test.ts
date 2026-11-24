import { describe, expect, it } from 'vitest'
import { createDemoClock } from './clock'

describe('demo calendar', () => {
  it('rolls across year and leap-day boundaries without duplicate buckets', () => {
    const clock = createDemoClock(new Date(2024, 2, 1, 12, 30))
    expect(clock.date(0)).toBe('2024-03-01')
    expect(clock.date(1)).toBe('2024-02-29')
    expect(clock.date(2)).toBe('2024-02-28')
    expect(new Set(Array.from({ length: 30 }, (_, index) => clock.date(index))).size).toBe(30)
    expect(clock.dateTime(1, 2)).toBe('2024-02-29 10:30:00')
  })

  it('uses the same captured clock for timestamps and related rows', () => {
    const reference = new Date(2027, 0, 1, 0, 5)
    const clock = createDemoClock(reference)
    expect(clock.date(1)).toBe('2026-12-31')
    expect(clock.dateTime(0, 0, 10)).toBe('2026-12-31 23:55:00')
    expect(Date.parse(clock.iso(2, 3))).toBeLessThan(reference.valueOf())
  })
})
