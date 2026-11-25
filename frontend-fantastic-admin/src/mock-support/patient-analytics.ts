import type { createDemoRecords } from './records'
import type { PatientAnalyticsSummary, PatientMultiRecordGroup } from '@/api/modules/patients'
import type { PaginatedResult } from '@/api/types'
import dayjs from 'dayjs'
import { demoClock } from './clock'
import { demoError, demoOk } from './response'

type Patient = ReturnType<typeof createDemoRecords>['patients'][number]
interface DemoResult<T> { code: number, message: string, data?: T, timestamp: string }

const distinct = (values: string[]) => [...new Set(values.filter(Boolean))]
const dateOf = (row: Patient) => row.admissiontime.slice(0, 10)
const compareText = (a: string, b: string) => a.localeCompare(b, 'zh-CN')
const missingId = (row: Patient) => !row.idCard.trim()

function pageResult<T>(rows: T[], rawPage: unknown, rawSize: unknown): DemoResult<PaginatedResult<T>> {
  const page = Number(rawPage ?? 1)
  const requested = Number(rawSize ?? 20)
  if (!Number.isSafeInteger(page) || page < 1 || !Number.isSafeInteger(requested) || requested < 1) {
    return demoError(400, '分页参数无效')
  }
  const size = Math.min(requested, 200)
  return demoOk({ list: rows.slice((page - 1) * size, page * size), total: rows.length, page, size, totalPages: Math.ceil(rows.length / size) })
}

function groupRows(patients: readonly Patient[], suspected: boolean): PatientMultiRecordGroup[] {
  const groups = new Map<string, Patient[]>()
  for (const patient of patients) {
    const key = suspected ? missingId(patient) ? patient.name.trim().toLowerCase() : '' : patient.idCard.trim()
    if (!key || !patient.bah.trim()) {
      continue
    }
    const rows = groups.get(key) ?? []
    rows.push(patient)
    groups.set(key, rows)
  }
  const result: PatientMultiRecordGroup[] = []
  for (const [key, rows] of groups) {
    const archiveNumbers = distinct(rows.map(row => row.bah.trim())).sort()
    if (archiveNumbers.length < 2) {
      continue
    }
    const dates = rows.map(dateOf).filter(Boolean).sort()
    result.push({
      matchType: suspected ? 'NAME_ONLY' : 'IDCARD',
      confidence: suspected ? 'LOW' : 'HIGH',
      patientName: distinct(rows.map(row => row.name.trim())).sort(compareText).join(' / ') || '未填写',
      maskedIdCard: suspected ? undefined : key.length <= 7 ? '***' : key.slice(0, 3) + '*'.repeat(key.length - 7) + key.slice(-4),
      recordCount: rows.length,
      archiveCount: archiveNumbers.length,
      archiveNumbers,
      firstAdmissionDate: dates[0],
      lastAdmissionDate: dates[dates.length - 1],
    })
  }
  return result.sort((a, b) => b.archiveCount - a.archiveCount || compareText(a.patientName, b.patientName))
}

export function createDemoPatientAnalytics(patients: readonly Patient[], clock = demoClock) {
  return {
    summary(rawYear: unknown): DemoResult<PatientAnalyticsSummary> {
      const today = clock.date()
      const currentYear = Number(today.slice(0, 4))
      const year = Number(rawYear ?? currentYear)
      if (!Number.isInteger(year) || year < 1900 || year > currentYear) {
        return demoError(400, '统计年份无效')
      }
      const rows = patients.filter(row => row.bah.trim() && dateOf(row).startsWith(`${year}-`))
      const dateGroups = new Map<string, Set<string>>()
      const departmentGroups = new Map<string, Set<string>>()
      for (const row of rows) {
        for (const [map, key] of [[dateGroups, dateOf(row)], [departmentGroups, row.department.trim() || '未填写']] as const) {
          const group = map.get(key) ?? new Set<string>()
          group.add(row.bah.trim())
          map.set(key, group)
        }
      }
      const last = year === currentYear ? today : `${year}-12-31`
      const dateCounts: PatientAnalyticsSummary['dateCounts'] = []
      for (let cursor = dayjs(`${year}-01-01`); cursor.format('YYYY-MM-DD') <= last; cursor = cursor.add(1, 'day')) {
        const date = cursor.format('YYYY-MM-DD')
        dateCounts.push({ date, count: dateGroups.get(date)?.size ?? 0 })
      }
      const departmentCounts = [...departmentGroups].map(([department, records]) => ({ department, count: records.size })).sort((a, b) => b.count - a.count || compareText(a.department, b.department)).slice(0, 30)
      return demoOk({
        year,
        totalRecords: patients.length,
        totalArchives: distinct(patients.map(row => row.bah.trim())).length,
        yearArchives: distinct(rows.map(row => row.bah.trim())).length,
        missingIdCardRecords: patients.filter(missingId).length,
        confirmedMultiRecordGroups: groupRows(patients, false).length,
        suspectedMultiRecordGroups: groupRows(patients, true).length,
        dateCounts,
        departmentCounts,
      })
    },
    missing(page: unknown, size: unknown) {
      return pageResult(patients.filter(missingId).sort((a, b) => dateOf(b).localeCompare(dateOf(a)) || b.id - a.id), page, size)
    },
    groups(page: unknown, size: unknown, includeSuspected: boolean) {
      return pageResult([...groupRows(patients, false), ...(includeSuspected ? groupRows(patients, true) : [])], page, size)
    },
    departments() {
      return distinct(patients.map(row => row.department.trim())).sort()
    },
  }
}
