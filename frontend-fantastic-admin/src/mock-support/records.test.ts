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

  it('preserves identifiers across dates and isolates separately generated datasets', () => {
    const first = createDemoRecords(createDemoClock(new Date(2026, 10, 24, 12)))
    const second = createDemoRecords(createDemoClock(new Date(2027, 0, 1, 12)))
    expect(first.scans.map(scan => [scan.id, scan.bah, scan.sjh])).toEqual(second.scans.map(scan => [scan.id, scan.bah, scan.sjh]))
    expect(first.scans[0].uploadDate).not.toBe(second.scans[0].uploadDate)
    first.scans.pop()
    expect(second.scans).toHaveLength(64)
  })
})
