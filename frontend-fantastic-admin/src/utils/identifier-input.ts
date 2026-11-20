import { MEDICAL_RECORD_CODE_LENGTH } from './medical-record-code'

/**
 * 编号类输入框的即时反馈。
 *
 * 病案号、上架号都是 8 位，但录入时常省略前导零（00123456 记成 123456）；
 * 身份证号 15 位、18 位都合法。用户在敲键盘时最需要的不是「格式错误」，
 * 而是「我现在几位」。所以这里只负责数位数、判长短、给一句怎么补的建议，
 * 不改写用户输入的值。
 */

/** 15 位旧号或 18 位新号；18 位末位可为 X（小写亦可）。 */
export const ID_CARD_PATTERN = /^\d{15}(?:\d{2}[\dX])?$/i
export const ID_CARD_LEGACY_LENGTH = 15
export const ID_CARD_FULL_LENGTH = 18

/** neutral：位数不足以判断；ok：命中标准位数；warn：需要用户确认。 */
export type IdentifierTone = 'neutral' | 'ok' | 'warn'

export interface IdentifierInputHint {
  /** 剔除空白后的字符数，直接用于徽标展示。 */
  length: number
  tone: IdentifierTone
  /** 一句给人看的补正建议；空串表示不必打扰。 */
  message: string
}

/**
 * 录入侧归一化：粘贴时常混入空格、全角空格或连字符，编号里不该有这些。
 * 只剔除空白类字符，不动其余内容，避免把用户正在敲的东西改掉。
 */
export function normalizeMedicalRecordCodeInput(value: unknown): string {
  return String(value ?? '').replace(/\s+/g, '')
}

/** 身份证号同理，另把末位小写 x 统一成大写 X，便于和证件核对。 */
export function normalizeIdCardInput(value: unknown): string {
  return String(value ?? '')
    .replace(/\s+/g, '')
    .replace(/x$/i, 'X')
}

/**
 * 病案号/上架号：8 位。不足 8 位不报错——后端按数值匹配，少输前导零照样能查到，
 * 所以这里只说明「补齐后等价于哪个号」，让用户自己核对是不是漏了开头两位。
 */
export function describeMedicalRecordCodeInput(value: unknown): IdentifierInputHint {
  const text = normalizeMedicalRecordCodeInput(value)
  if (!text) {
    return { length: 0, tone: 'neutral', message: '' }
  }

  // 含非数字时不做位数判断：历史库里确实存在非纯数字编号，误判比不提示更糟。
  if (!/^\d+$/.test(text)) {
    return { length: text.length, tone: 'neutral', message: '' }
  }

  if (text.length < MEDICAL_RECORD_CODE_LENGTH) {
    // 补齐后的值直接给出来：少了前导零的人一看就知道实际会查哪个号；
    // 再按 4+4 断开，方便和纸质档案逐位对数
    const padded = text.padStart(MEDICAL_RECORD_CODE_LENGTH, '0')
    const grouped = `${padded.slice(0, 4)} ${padded.slice(4)}`
    return {
      length: text.length,
      tone: 'warn',
      message: `不足 ${MEDICAL_RECORD_CODE_LENGTH} 位，查询时按 ${grouped} 匹配`,
    }
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
