import { afterEach, describe, expect, it } from 'vitest'
import {
  archiveAccessMode,
  canRenderArchiveRoute,
  isExternalArchiveAccessMode,
  resolveArchiveAccessMode,
  setArchiveAccessMode,
  shouldShowArchiveSearchCard,
} from '../access-mode'

describe('archive access mode', () => {
  afterEach(() => {
    setArchiveAccessMode('internal')
  })

  it('shows the search card only for internal access', () => {
    expect(shouldShowArchiveSearchCard('internal')).toBe(true)
    expect(shouldShowArchiveSearchCard('external-ticket')).toBe(false)
    expect(shouldShowArchiveSearchCard('archive-legacy')).toBe(false)
  })

  it('recognizes external Ticket and legacy runtime modes', () => {
    expect(resolveArchiveAccessMode('external-ticket', 'archive-legacy')).toBe('external-ticket')
    expect(resolveArchiveAccessMode('internal', 'external-ticket')).toBe('external-ticket')
    expect(resolveArchiveAccessMode('internal', 'archive-legacy')).toBe('archive-legacy')
    expect(resolveArchiveAccessMode('internal', '')).toBe('internal')
    expect(isExternalArchiveAccessMode('external-ticket')).toBe(true)
  })

  it('allows the root permission gate to render both verified external modes', () => {
    expect(canRenderArchiveRoute('archive', 'external-ticket')).toBe(true)
    expect(canRenderArchiveRoute('archive', 'archive-legacy')).toBe(true)
    expect(canRenderArchiveRoute('archive', 'internal')).toBe(false)
    expect(canRenderArchiveRoute('settings', 'external-ticket')).toBe(false)
  })

  it('updates the reactive mode and DOM marker together', () => {
    setArchiveAccessMode('external-ticket')
    expect(archiveAccessMode.value).toBe('external-ticket')
    expect(document.documentElement.dataset.mrrAccessMode).toBe('external-ticket')

    setArchiveAccessMode('internal')
    expect(archiveAccessMode.value).toBe('internal')
    expect(document.documentElement.dataset.mrrAccessMode).toBeUndefined()
  })
})
