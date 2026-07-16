export interface MedicalRecordTypeOption {
  value: number
  label: string
}

/**
 * 全局病案类型字典。
 *
 * 所有筛选项、图片类型修改、统计展示和 Mock 数据应复用此定义，
 * 避免不同页面出现名称或可选类型不一致。
 */
export const MEDICAL_RECORD_TYPES: MedicalRecordTypeOption[] = [
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
  { value: 11, label: '11-新生儿' },
  { value: 12, label: '12-出院记录' },
  { value: 13, label: '13-大病史' },
  { value: 14, label: '14-其它' },
  { value: 15, label: '15-分娩记录' },
]

export const MEDICAL_RECORD_TYPE_CODES = MEDICAL_RECORD_TYPES.map(item => item.value)

export const MEDICAL_RECORD_TYPE_MAP = new Map<number, string>(
  MEDICAL_RECORD_TYPES.map(item => [item.value, item.label] as const),
)

export function getMedicalRecordTypeLabel(
  type?: number | string | null,
  emptyLabel = '未分类',
): string {
  if (type === null || type === undefined || type === '') {
    return emptyLabel
  }

  const numericType = Number(type)
  return MEDICAL_RECORD_TYPE_MAP.get(numericType) || `类型 ${type}`
}
