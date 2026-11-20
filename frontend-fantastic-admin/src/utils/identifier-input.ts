import { MEDICAL_RECORD_CODE_LENGTH, normalizeMedicalRecordCode } from './medical-record-code'

/**
 * 编号类输入框的即时反馈。
 *
 * 病案号与上架号保留原始位数，短号和带前导零的编号可以精确匹配不同病案。
 * 身份证号允许 15 位或 18 位。位数提示只供核对。
 */

/** 15 位旧号或 18 位新号；18 位末位可为 X（小写亦可）。 */
export const ID_CARD_PATTERN = /^\d{15}(?:\d{2}[\dX])?$/i
export const ID_CARD_LEGACY_LENGTH = 15
export const ID_CARD_FULL_LENGTH = 18

/** neutral：位数不足以判断；ok：命中标准位数；warn：需要用户确认。 */
export type IdentifierTone = 'neutral' | 'ok' | 'warn'

export interface IdentifierInputHint {
  /** 归一化后的字符数，用于徽标展示。 */
  length: number
  tone: IdentifierTone
  /** 一句给人看的补正建议；空串表示不必打扰。 */
  message: string
}

/** 身份证号去除空白，并把末位小写 x 统一为大写。 */
export function normalizeIdCardInput(value: unknown): string {
  return String(value ?? '')
    .replace(/\s+/g, '')
    .replace(/x$/i, 'X')
}

/** 病案号和上架号的位数提示，沿用查询使用的原始编号。 */
export function describeMedicalRecordCodeInput(value: unknown): IdentifierInputHint {
  const text = normalizeMedicalRecordCode(value)
  if (!/^\d+$/.test(text) || text.length < MEDICAL_RECORD_CODE_LENGTH) {
    return { length: text.length, tone: 'neutral', message: '' }
  }

  if (text.length > MEDICAL_RECORD_CODE_LENGTH) {
    return {
      length: text.length,
      tone: 'warn',
      message: `超过 ${MEDICAL_RECORD_CODE_LENGTH} 位，请核对号码`,
    }
  }

  return { length: text.length, tone: 'ok', message: '' }
}

/** 身份证号：15 位与 18 位都合法，中间的长度都算「还没输完」。 */
export function describeIdCardInput(value: unknown): IdentifierInputHint {
  const text = normalizeIdCardInput(value)
  if (!text) {
    return { length: 0, tone: 'neutral', message: '' }
  }

  // X 只允许出现在末位，其余位置必须是数字
  if (!/^\d*X?$/i.test(text)) {
    return { length: text.length, tone: 'warn', message: '只能输入数字，末位可为 X' }
  }

  const { length } = text
  if (length === ID_CARD_LEGACY_LENGTH) {
    // 15 位旧证号在 18 格里会空出后 3 格，这里明说「可以直接查」，免得用户以为还差几位
    return { length, tone: 'ok', message: '15 位旧证号，可直接查询' }
  }
  if (length === ID_CARD_FULL_LENGTH) {
    return { length, tone: 'ok', message: '' }
  }

  if (length < ID_CARD_LEGACY_LENGTH) {
    return {
      length,
      tone: 'warn',
      message: `还差 ${ID_CARD_LEGACY_LENGTH - length} 位（需 ${ID_CARD_LEGACY_LENGTH} 或 ${ID_CARD_FULL_LENGTH} 位）`,
    }
  }

  return {
    length,
    tone: 'warn',
    message: `应为 ${ID_CARD_LEGACY_LENGTH} 位或 ${ID_CARD_FULL_LENGTH} 位，当前 ${length} 位`,
  }
}
