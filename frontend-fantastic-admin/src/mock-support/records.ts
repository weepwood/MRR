import type { DemoScenario } from './scenarios'
import { getMedicalRecordTypeLabel, MEDICAL_RECORD_TYPE_CODES } from '@/constants/medical-record-types'
import { demoClock } from './clock'
import { pageSvg } from './image'
import { pagesScenarioStorage, readScenario } from './scenarios'

export interface MockScanRecord {
  id: number
  archiveId?: number
  brxh: string
  bah: string
  sjh: string
  filename: string
  btype: number
  pages: number
  openerNo: string
  uploadDate: string
  uploadFlag: number
  folder: string
  ossUrl?: string
  fileSize: number
  checksumMd5: string
  migrationStatus: string
  migratedAt?: string
  migrationAttempts?: number
  migrationErrorCode?: string
  migrationNextRetryAt?: string | null
  migrationUpdatedAt?: string
}

const departments = ['心内科', '神经内科', '普外科', '骨科', '呼吸内科', '消化内科']
const operators = ['scanner-01', 'scanner-02', 'scanner-03', 'scanner-04']
const names = ['演示患者甲', '演示患者乙', '演示患者丙', '演示患者丁', '演示患者戊', '演示患者己']

// Keep the first 12 archives in the recent window; distribute the rest over
// more than a year so date filters and year comparisons have real sample data.
function archiveAge(index: number) {
  return index < 12 ? index : 12 + (index - 12) * 20
}

// Keep all identifiers synthetic and deterministic. Each archive belongs to
// one patient record; both scans use that record's BAH and BRXH.
export function createDemoRecords(clock = demoClock, scenario: DemoScenario = 'standard') {
  const patients = Array.from({ length: 32 }, (_, index) => ({
    id: index + 1,
    brxh: String(320001 + index * 2),
    idCard: `00000020000101${String(index + 101).padStart(4, '0')}`,
    bah: String(26070000 + index).padStart(8, '0'),
    name: names[index % names.length],
    admissiontime: clock.dateTime(archiveAge(index) + 3, 2),
    department: departments[index % departments.length],
  }))

  const scans: MockScanRecord[] = patients.flatMap((patient, patientIndex) =>
    Array.from({ length: 2 }, (_, offset) => {
      const index = patientIndex * 2 + offset
      const id = index + 1
      const reviewSample = scenario === 'ocr-review' && id % 3 === 0 && id % 16 !== 0
      const btype = reviewSample || [3, 4, 11, 12].includes(id) ? 0 : MEDICAL_RECORD_TYPE_CODES[index % MEDICAL_RECORD_TYPE_CODES.length]
      const age = archiveAge(patientIndex)
      const uploadDate = clock.dateTime(age, 1, offset * 15)
      const monthFolder = uploadDate.slice(0, 7).replace('-', '/')
      const uploaded = id % 16 !== 0
      const waiting = id % 8 === 0 && uploaded
      const failed = id % 13 === 0 && uploaded && !waiting
      const retry = id % 7 === 0 && uploaded && !waiting && !failed
      const migrated = scenario !== 'migration-backlog' && uploaded && !waiting && !failed && !retry && index % 4 !== 0
      return {
        id,
        archiveId: patient.id,
        brxh: patient.brxh,
        bah: patient.bah,
        sjh: waiting ? `INVALID-${id}` : String(90000000 + id),
        filename: `${patient.bah}_${btype}_${id}.jpg`,
        btype,
        pages: 8 + (index * 7) % 83,
        openerNo: operators[index % operators.length],
        uploadDate,
        uploadFlag: uploaded ? 1 : 0,
        folder: `/mock/archive/${monthFolder}/${patient.bah}`,
        ossUrl: migrated ? pageSvg(patient.bah, id, btype === 0 ? '未分类' : getMedicalRecordTypeLabel(btype)) : undefined,
        fileSize: 512_000 + index * 31_337,
        checksumMd5: `mock-md5-${String(id).padStart(4, '0')}`,
        migrationStatus: migrated ? (index % 5 === 0 ? 'verified' : 'migrated') : waiting ? 'waiting_sjh' : failed ? 'failed' : retry ? 'retry_wait' : 'not_migrated',
        migrationAttempts: failed || retry ? 1 : 0,
        migrationErrorCode: waiting ? 'MISSING_SJH' : failed ? 'SOURCE_NOT_FOUND' : retry ? 'SOURCE_TIMEOUT' : undefined,
        migrationNextRetryAt: retry ? clock.iso(0, id % 2 === 0 ? -2 : 1) : null,
        migratedAt: migrated ? clock.dateTime(age, 0, offset * 15) : undefined,
      }
    }),
  )

  return { patients, scans }
}

// One mutable dataset per demo module instance; production never imports it.
export const demoRecords = createDemoRecords(demoClock, readScenario(pagesScenarioStorage()))
