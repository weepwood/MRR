import { describe, expect, it } from 'vitest'
import { createDemoClock } from './clock'
import { createDemoRecords } from './records'

describe('shared synthetic records', () => {
  it('keeps one patient identity per archive and unique scan identifiers', () => {
    const { patients, scans } = createDemoRecords(createDemoClock(new Date(2027, 0, 1, 0, 5)))
    expect(patients).toHaveLength(32)
    expect(scans).toHaveLength(64)
    expect(new Set(patients.map(patient => patient.bah)).size).toBe(32)
    expect(new Set(patients.map(patient => patient.brxh)).size).toBe(32)
    expect(new Set(scans.map(scan => scan.id)).size).toBe(64)
    expect(new Set(scans.map(scan => scan.sjh)).size).toBe(64)
    for (const patient of patients) {
      const related = scans.filter(scan => scan.bah === patient.bah)
      expect(related).toHaveLength(2)
      expect(related.every(scan => scan.brxh === patient.brxh && scan.uploadDate >= patient.admissiontime)).toBe(true)
      expect(patient.idCard).toMatch(/^000000\d{12}$/)
    }
  })

  it('covers recent and historical dates across calendar years and preserves chronological relationships', () => {
    const clock = createDemoClock(new Date(2026, 10, 24, 12))
    const { patients, scans } = createDemoRecords(clock)
    expect(scans.filter(scan => scan.uploadDate.slice(0, 10) >= clock.date(7)).length).toBeGreaterThan(0)
    expect(scans.some(scan => scan.uploadDate.slice(0, 10) < clock.date(365))).toBe(true)
    expect(new Set(scans.map(scan => scan.uploadDate.slice(0, 7))).size).toBeGreaterThanOrEqual(12)
    expect(new Set(scans.map(scan => scan.uploadDate.slice(0, 4))).size).toBeGreaterThanOrEqual(2)
    for (const scan of scans) {
      expect(scan.uploadDate >= patients.find(patient => patient.bah === scan.bah)!.admissiontime).toBe(true)
      if (scan.migratedAt) {
        expect(scan.migratedAt >= scan.uploadDate).toBe(true)
      }
      expect(scan.folder).toContain(scan.uploadDate.slice(0, 7).replace('-', '/'))
    }
  })

  it('includes both due and deferred retries without changing scan identities', () => {
    const reference = new Date(2026, 10, 24, 12)
    const { scans } = createDemoRecords(createDemoClock(reference))
    const retries = scans.filter(scan => scan.migrationStatus === 'retry_wait')
    expect(retries.some(scan => Date.parse(scan.migrationNextRetryAt!) <= reference.getTime())).toBe(true)
    expect(retries.some(scan => Date.parse(scan.migrationNextRetryAt!) > reference.getTime())).toBe(true)
    expect(scans.find(scan => scan.id === 7)?.migrationNextRetryAt).toBe(createDemoClock(reference).iso(0, 1))
  })

  it('preserves identifiers across dates and isolates separately generated datasets', () => {
    const first = createDemoRecords(createDemoClock(new Date(2026, 10, 24, 12)))
    const second = createDemoRecords(createDemoClock(new Date(2027, 0, 1, 12)))
    expect(first.scans.map(scan => [scan.id, scan.bah, scan.sjh])).toEqual(second.scans.map(scan => [scan.id, scan.bah, scan.sjh]))
    expect(first.scans[0].uploadDate).not.toBe(second.scans[0].uploadDate)
    first.scans.pop()
    expect(second.scans).toHaveLength(64)
  })
})
