import { MEDICAL_RECORD_TYPE_CODES } from '@/constants/medical-record-types'
import { demoClock } from './clock'

export interface MockScanRecord {
  id: number
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
}

const departments = ['心内科', '神经内科', '普外科', '骨科', '呼吸内科', '消化内科']
const operators = ['scanner-01', 'scanner-02', 'scanner-03', 'scanner-04']
const names = ['演示患者甲', '演示患者乙', '演示患者丙', '演示患者丁', '演示患者戊', '演示患者己']

// Keep all identifiers synthetic and deterministic. Each archive belongs to
// one patient record; both scans use that record's BAH and BRXH.
export function createDemoRecords(clock = demoClock) {
  const patients = Array.from({ length: 32 }, (_, index) => ({
    id: index + 1,
    brxh: String(320001 + index * 2),
    idCard: `00000020000101${String(index + 101).padStart(4, '0')}`,
    bah: String(26070000 + index).padStart(8, '0'),
    name: names[index % names.length],
    admissiontime: clock.dateTime(index + 3, 2),
    department: departments[index % departments.length],
  }))

  const scans: MockScanRecord[] = patients.flatMap((patient, patientIndex) =>
    Array.from({ length: 2 }, (_, offset) => {
      const index = patientIndex * 2 + offset
      const id = index + 1
      const btype = MEDICAL_RECORD_TYPE_CODES[index % MEDICAL_RECORD_TYPE_CODES.length]
      const uploadDate = clock.dateTime(patientIndex, 1, offset * 15)
      const monthFolder = uploadDate.slice(0, 7).replace('-', '/')
      const migrated = index % 4 !== 0
      return {
        id,
        brxh: patient.brxh,
        bah: patient.bah,
        sjh: String(90000000 + id),
        filename: `${patient.bah}_${btype}_${id}.jpg`,
        btype,
        pages: 8 + (index * 7) % 83,
        openerNo: operators[index % operators.length],
        uploadDate,
        uploadFlag: migrated ? 1 : 0,
        folder: `/mock/archive/${monthFolder}/${patient.bah}`,
        ossUrl: migrated ? `https://example.invalid/mock/${id}.jpg` : undefined,
        fileSize: 512_000 + index * 31_337,
        checksumMd5: `mock-md5-${String(id).padStart(4, '0')}`,
        migrationStatus: migrated ? (index % 5 === 0 ? 'verified' : 'migrated') : 'not_migrated',
        migratedAt: migrated ? clock.dateTime(patientIndex, 0, offset * 15) : undefined,
      }
    }),
  )

  return { patients, scans }
}

// One mutable dataset per demo module instance; production never imports it.
export const demoRecords = createDemoRecords()
