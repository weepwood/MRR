import { describe, expect, it } from 'vitest'
import { createDemoClock } from './clock'
import { createDemoPatientAnalytics } from './patient-analytics'
import { createDemoRecords } from './records'

describe('演示患者数据质量', () => {
  const clock = createDemoClock(new Date(2026, 10, 24, 12))

  it('共享合成患者样本，按年、日期和科室核对病案计数', () => {
    const { patients } = createDemoRecords(clock)
    const analytics = createDemoPatientAnalytics(patients, clock)
    const summary = analytics.summary(2026).data!
    expect(summary.totalRecords).toBe(patients.length)
    expect(summary.totalArchives).toBe(32)
    expect(summary.dateCounts).toHaveLength(328)
    expect(summary.dateCounts.reduce((n, row) => n + row.count, 0)).toBe(summary.yearArchives)
    expect(summary.departmentCounts.reduce((n, row) => n + row.count, 0)).toBe(summary.yearArchives)
    expect(analytics.summary(2027).code).toBe(400)
    expect(analytics.summary(2025).data!.dateCounts).toHaveLength(365)
    expect(analytics.departments()).toEqual([...new Set(patients.map(row => row.department))].sort())
  })

  it('缺失证件和多病案分组来自同一数据集，区分确认与疑似并脱敏', () => {
    const { patients } = createDemoRecords(clock)
    const analytics = createDemoPatientAnalytics(patients, clock)
    const summary = analytics.summary(2026).data!
    const missing = analytics.missing(1, 1).data!
    expect(missing.total).toBe(summary.missingIdCardRecords)
    expect(missing.total).toBe(2)
    expect(missing.list).toHaveLength(1)
    expect(missing.list.every(row => !row.idCard)).toBe(true)
    const all = analytics.groups(1, 20, true).data!
    const confirmed = analytics.groups(1, 20, false).data!
    expect(all.total).toBe(summary.confirmedMultiRecordGroups + summary.suspectedMultiRecordGroups)
    expect(confirmed.total).toBe(1)
    expect(all.total).toBe(2)
    expect(confirmed.list[0].maskedIdCard).toMatch(/^000\*+\d{4}$/)
    expect(all.list[1].matchType).toBe('NAME_ONLY')
    expect(all.list[1].maskedIdCard).toBeUndefined()
    expect(all.list.every(group => group.archiveCount === group.archiveNumbers.length)).toBe(true)
  })

  it('数据变化立即反映到统计，不维护第二套计数', () => {
    const { patients } = createDemoRecords(clock)
    const analytics = createDemoPatientAnalytics(patients, clock)
    const missing = analytics.missing(1, 20).data!.total
    patients[0].idCard = ''
    expect(analytics.summary(2026).data!.missingIdCardRecords).toBe(missing + 1)
    expect(analytics.missing(2, 2).data!.list).toHaveLength(1)
    expect(analytics.groups(0, 20, true).code).toBe(400)
  })
})
