import { describe, expect, it } from 'vitest'
import {
  isExternalArchiveAccessMode,
  resolveArchiveAccessMode,
  shouldShowArchiveSearchCard,
} from '../access-mode'

describe('archive access mode', () => {
  it('shows the search card only for internal access', () => {
    expect(shouldShowArchiveSearchCard('internal')).toBe(true)
    expect(shouldShowArchiveSearchCard('external-ticket')).toBe(false)
    expect(shouldShowArchiveSearchCard('archive-legacy')).toBe(false)
  })

  it('prefers the explicit external Ticket mode', () => {
    expect(resolveArchiveAccessMode('external-ticket', 'archive-legacy')).toBe('external-ticket')
    expect(isExternalArchiveAccessMode('external-ticket')).toBe(true)
  })

  it('uses the runtime legacy mode for unauthenticated archive access', () => {
    expect(resolveArchiveAccessMode('internal', 'archive-legacy')).toBe('archive-legacy')
    expect(resolveArchiveAccessMode('internal', '')).toBe('internal')
  })
})
