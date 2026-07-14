import { describe, expect, it } from 'vitest'
import {
  formatArchiveWatermarkTime,
  parseArchiveWatermarkEnabled,
  parseArchiveWatermarkOpacity,
  resolveArchiveWatermarkUserId,
} from '../archive-watermark'

describe('archive watermark helpers', () => {
  it('formats the current time with zero-padded date and minute', () => {
    expect(formatArchiveWatermarkTime(new Date(2026, 6, 14, 9, 5, 7)))
      .toBe('2026-07-14 09:05')
  })

  it('prefers numeric user id and falls back to username or account', () => {
    expect(resolveArchiveWatermarkUserId({ id: 42, username: 'doctor' }, 'operator')).toBe('42')
    expect(resolveArchiveWatermarkUserId({ username: 'doctor' }, 'operator')).toBe('doctor')
    expect(resolveArchiveWatermarkUserId({}, 'operator')).toBe('operator')
    expect(resolveArchiveWatermarkUserId({}, '')).toBe('未登录')
  })

  it('parses server and local boolean values safely', () => {
    expect(parseArchiveWatermarkEnabled(true)).toBe(true)
    expect(parseArchiveWatermarkEnabled('false')).toBe(false)
    expect(parseArchiveWatermarkEnabled('1')).toBe(true)
    expect(parseArchiveWatermarkEnabled(undefined, false)).toBe(false)
  })

  it('clamps configurable opacity to the supported range', () => {
    expect(parseArchiveWatermarkOpacity('18')).toBe(18)
    expect(parseArchiveWatermarkOpacity(2)).toBe(5)
    expect(parseArchiveWatermarkOpacity(90)).toBe(35)
    expect(parseArchiveWatermarkOpacity('invalid', 12)).toBe(12)
  })
})
