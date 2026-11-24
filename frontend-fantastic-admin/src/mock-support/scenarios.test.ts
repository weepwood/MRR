import { describe, expect, it } from 'vitest'
import { createDemoRecords } from './records'
import { readScenario, SCENARIO_KEY, writeScenario } from './scenarios'

function storage() {
  const values = new Map<string, string>()
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => { values.set(key, value) },
    removeItem: (key: string) => { values.delete(key) },
  }
}

describe('demo scenario selection', () => {
  it('persists only a known preset and resets only its own key', () => {
    const store = storage()
    store.setItem('unrelated', 'keep')
    expect(readScenario(store)).toBe('standard')
    expect(writeScenario(store, 'migration-backlog')).toBe(true)
    expect(readScenario(store)).toBe('migration-backlog')
    expect(store.getItem(SCENARIO_KEY)).toBe('migration-backlog')
    expect(writeScenario(store, 'invalid')).toBe(false)
    expect(readScenario(store)).toBe('migration-backlog')
    expect(writeScenario(store, 'standard')).toBe(true)
    expect(store.getItem(SCENARIO_KEY)).toBeNull()
    expect(store.getItem('unrelated')).toBe('keep')
    store.setItem(SCENARIO_KEY, '{"unexpected":true}')
    expect(readScenario(store)).toBe('standard')
  })

  it('handles inaccessible storage without a successful write claim', () => {
    const blocked = {
      getItem() { throw new Error('blocked') },
      setItem() { throw new Error('blocked') },
      removeItem() { throw new Error('blocked') },
    }
    expect(readScenario(blocked)).toBe('standard')
    expect(writeScenario(blocked, 'migration-backlog')).toBe(false)
    expect(writeScenario(blocked, 'standard')).toBe(false)
  })

  it('generates coherent independent presets without changing record identities', () => {
    const standard = createDemoRecords(undefined, 'standard')
    const backlog = createDemoRecords(undefined, 'migration-backlog')
    const review = createDemoRecords(undefined, 'ocr-review')
    expect(backlog.scans.map(row => row.id)).toEqual(standard.scans.map(row => row.id))
    expect(backlog.scans.filter(row => row.ossUrl)).toHaveLength(0)
    expect(backlog.scans.filter(row => row.migrationStatus === 'failed').length).toBeGreaterThan(0)
    expect(backlog.scans.every(row => !row.migratedAt)).toBe(true)
    expect(review.scans.filter(row => row.btype === 0 && row.uploadFlag).length)
      .toBeGreaterThan(standard.scans.filter(row => row.btype === 0 && row.uploadFlag).length)
    expect(review.scans.map(row => row.sjh)).toEqual(standard.scans.map(row => row.sjh))
    expect(standard.scans.some(row => row.ossUrl)).toBe(true)
  })
})
