export const MEDICAL_RECORD_CODE_LENGTH = 8
export const ARCHIVE_BAH_UNIQUE_LIMIT = 10_000_000

const MEDICAL_RECORD_CODE_KEYS = new Set(['bah', 'sjh'])
const ARCHIVE_BAH_UNIQUE_LIMIT_TEXT = String(ARCHIVE_BAH_UNIQUE_LIMIT)

export type ArchiveLookupMode = 'bah' | 'sjh'

export interface ArchiveLookupResolution {
  mode: ArchiveLookupMode | null
  bah: string
  sjh: string
  requestBah?: string
  requestSjh?: string
  validationMessage: string
}

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
 * 病案号从 10000000 开始不再保证唯一，查询时必须改用唯一上架号。
 */
export function requiresSjhForBah(value: unknown): boolean {
  const searchTerm = toMedicalRecordCodeSearchTerm(value)
  if (!/^\d+$/.test(searchTerm)) {
    return false
  }
  return searchTerm.length > ARCHIVE_BAH_UNIQUE_LIMIT_TEXT.length
    || (searchTerm.length === ARCHIVE_BAH_UNIQUE_LIMIT_TEXT.length
      && searchTerm >= ARCHIVE_BAH_UNIQUE_LIMIT_TEXT)
}

/**
 * 统一解析影像档案查询键。
 *
 * - 病案号小于 10000000：仅以病案号请求后端，即使同时存在上架号。
 * - 病案号大于等于 10000000：仅以上架号请求后端，病案号只保留用于展示和路由恢复。
 * - 仅有上架号：以上架号查询。
 */
export function resolveArchiveLookup(bah: unknown, sjh: unknown): ArchiveLookupResolution {
  const normalizedBah = normalizeMedicalRecordCode(bah)
  const normalizedSjh = normalizeMedicalRecordCode(sjh)

  if (!normalizedBah && !normalizedSjh) {
    return {
      mode: null,
      bah: '',
      sjh: '',
      validationMessage: '请输入病案号或上架号',
    }
  }

  if (normalizedBah && requiresSjhForBah(normalizedBah)) {
    if (!normalizedSjh) {
      return {
        mode: null,
        bah: normalizedBah,
        sjh: '',
        validationMessage: `病案号大于等于 ${ARCHIVE_BAH_UNIQUE_LIMIT} 时必须输入上架号`,
      }
    }
    return {
      mode: 'sjh',
      bah: normalizedBah,
      sjh: normalizedSjh,
      requestSjh: normalizedSjh,
      validationMessage: '',
    }
  }

  if (normalizedBah) {
    return {
      mode: 'bah',
      bah: normalizedBah,
      sjh: normalizedSjh,
      requestBah: normalizedBah,
      validationMessage: '',
    }
  }

  return {
    mode: 'sjh',
    bah: '',
    sjh: normalizedSjh,
    requestSjh: normalizedSjh,
    validationMessage: '',
  }
}

/**
 * 校验影像档案查询条件。上架号本身唯一，因此允许只使用上架号查询。
 */
export function getArchiveLookupValidationMessage(bah: unknown, sjh: unknown): string {
  return resolveArchiveLookup(bah, sjh).validationMessage
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
