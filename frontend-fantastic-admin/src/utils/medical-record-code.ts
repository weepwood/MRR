export const MEDICAL_RECORD_CODE_LENGTH = 8

const MEDICAL_RECORD_CODE_KEYS = new Set(['bah', 'sjh'])

/**
 * 将纯数字病案号或上架号统一格式化为 8 位，不足位数时在左侧补零。
 * 异常的非数字值不会被静默截断或改写，只清理首尾空白。
 */
export function normalizeMedicalRecordCode(value: unknown): string {
  const text = String(value ?? '').trim()
  if (!text || text.toUpperCase() === 'NULL') {
    return ''
  }
  if (/^\d+$/.test(text) && text.length < MEDICAL_RECORD_CODE_LENGTH) {
    return text.padStart(MEDICAL_RECORD_CODE_LENGTH, '0')
  }
  return text
}

/**
 * 用于界面展示；空值统一显示为占位符。
 */
export function formatMedicalRecordCode(value: unknown, fallback = '-'): string {
  return normalizeMedicalRecordCode(value) || fallback
}

/**
 * 生成无前导零搜索词，使 123 与 00000123 具有相同搜索语义。
 */
export function toMedicalRecordCodeSearchTerm(value: unknown): string {
  const text = String(value ?? '').trim()
  if (!text || !/^\d+$/.test(text)) {
    return text
  }
  const withoutLeadingZeros = text.replace(/^0+/, '')
  return withoutLeadingZeros || '0'
}

/**
 * 递归规范化接口请求或响应对象中名为 bah / sjh（不区分大小写）的字段。
 */
export function normalizeMedicalRecordCodeFields<T>(value: T): T {
  if (Array.isArray(value)) {
    return value.map(item => normalizeMedicalRecordCodeFields(item)) as T
  }
  if (!isPlainRecord(value)) {
    return value
  }

  const normalized: Record<string, unknown> = {}
  for (const [key, fieldValue] of Object.entries(value)) {
    if (MEDICAL_RECORD_CODE_KEYS.has(key.toLowerCase()) && isCodeScalar(fieldValue)) {
      normalized[key] = normalizeMedicalRecordCode(fieldValue)
    }
    else {
      normalized[key] = normalizeMedicalRecordCodeFields(fieldValue)
    }
  }
  return normalized as T
}

function isCodeScalar(value: unknown): value is string | number {
  return typeof value === 'string' || typeof value === 'number'
}

function isPlainRecord(value: unknown): value is Record<string, unknown> {
  if (!value || typeof value !== 'object') {
    return false
  }
  const prototype = Object.getPrototypeOf(value)
  return prototype === Object.prototype || prototype === null
}
