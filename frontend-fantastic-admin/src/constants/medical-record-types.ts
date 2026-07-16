export interface MedicalRecordTypeOption {
  value: number
  label: string
}

export const MEDICAL_RECORD_TYPE_OPTIONS: readonly MedicalRecordTypeOption[] = Object.freeze([
  { value: 1, label: '01-病案首页' },
  { value: 2, label: '02-病程录' },
  { value: 3, label: '03-手术记录' },
  { value: 4, label: '04-术后病程录' },
  { value: 5, label: '05-护理记录' },
  { value: 6, label: '06-会诊单' },
  { value: 7, label: '07-特殊检查' },
  { value: 8, label: '08-检验单' },
  { value: 9, label: '09-医嘱' },
  { value: 10, label: '10-体温单' },
  { value: 12, label: '12-出院记录' },
  { value: 13, label: '13-大病史' },
  { value: 14, label: '14-其它' },
])

export const MEDICAL_RECORD_TYPE_CODES: readonly number[] = Object.freeze(
  MEDICAL_RECORD_TYPE_OPTIONS.map(item => item.value),
)

export const MEDICAL_RECORD_TYPE_LABEL_MAP: Readonly<Record<number, string>> = Object.freeze(
  Object.fromEntries(MEDICAL_RECORD_TYPE_OPTIONS.map(item => [item.value, item.label])) as Record<number, string>,
)

export function getMedicalRecordTypeLabel(type?: number | string | null, fallback = '未分类'): string {
  if (type === null || type === undefined || type === '') {
    return fallback
  }
  const numericType = Number(type)
  return MEDICAL_RECORD_TYPE_LABEL_MAP[numericType] || `类型 ${type}`
}
