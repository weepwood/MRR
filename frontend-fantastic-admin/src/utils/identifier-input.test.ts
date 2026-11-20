import { describe, expect, it } from 'vitest'
import {
  describeIdCardInput,
  describeMedicalRecordCodeInput,
  ID_CARD_FULL_LENGTH,
  ID_CARD_LEGACY_LENGTH,
  ID_CARD_PATTERN,
  normalizeIdCardInput,
} from './identifier-input'

describe('normalizeIdCardInput', () => {
  it('剔除空白并把末位小写 x 转成大写', () => {
    expect(normalizeIdCardInput(' 110101 19900101 123x ')).toBe('11010119900101123X')
    expect(normalizeIdCardInput('11010119900101123X')).toBe('11010119900101123X')
    // 中间的 x 不该被当成校验位
    expect(normalizeIdCardInput('110101x99001011234')).toBe('110101x99001011234')
  })
})

describe('describeMedicalRecordCodeInput', () => {
  it('空值不给任何提示', () => {
    expect(describeMedicalRecordCodeInput('')).toEqual({ length: 0, tone: 'neutral', message: '' })
    expect(describeMedicalRecordCodeInput('   ')).toEqual({ length: 0, tone: 'neutral', message: '' })
  })

  it('8 位判为达标且不打扰', () => {
    expect(describeMedicalRecordCodeInput('00123456')).toEqual({ length: 8, tone: 'ok', message: '' })
  })

  it('合法短编号只显示原始位数', () => {
    expect(describeMedicalRecordCodeInput('　123456　')).toEqual({ length: 6, tone: 'neutral', message: '' })
    expect(describeMedicalRecordCodeInput('1')).toEqual({ length: 1, tone: 'neutral', message: '' })
  })

  it('超过 8 位提示核对而不是静默接受', () => {
    const hint = describeMedicalRecordCodeInput('001234567')
    expect(hint.length).toBe(9)
    expect(hint.tone).toBe('warn')
    expect(hint.message).toContain('超过 8 位')
  })

  it('含非数字时只报位数，不猜对错', () => {
    // 历史库里存在非纯数字编号，误判成错误比不提示更糟
    expect(describeMedicalRecordCodeInput('A123456')).toEqual({ length: 7, tone: 'neutral', message: '' })
  })
})

describe('describeIdCardInput', () => {
  it('15 位与 18 位都是合法长度', () => {
    expect(describeIdCardInput('110101199001011')).toEqual({
      length: 15,
      tone: 'ok',
      message: '15 位旧证号，可直接查询',
    })
    expect(describeIdCardInput('110101199001011234')).toEqual({ length: 18, tone: 'ok', message: '' })
    expect(describeIdCardInput('11010119900101123X')).toEqual({ length: 18, tone: 'ok', message: '' })
    expect(ID_CARD_PATTERN.test('11010119900101123X')).toBe(true)
  })

  it('不足 15 位时报告还差几位', () => {
    expect(describeIdCardInput('110101199001011'.slice(0, 13))).toEqual({
      length: 13,
      tone: 'warn',
      message: '还差 2 位（需 15 或 18 位）',
    })
  })

  it('夹在 15 与 18 位之间时报出两个合法长度', () => {
    // 15 位已经合法，16/17 位不是「还差几位」而是「长度不对」
    const hint = describeIdCardInput('1101011990010112')
    expect(hint.length).toBe(16)
    expect(hint.tone).toBe('warn')
    expect(hint.message).toBe(`应为 ${ID_CARD_LEGACY_LENGTH} 位或 ${ID_CARD_FULL_LENGTH} 位，当前 16 位`)
  })

  it('x 出现在末位之外视为非法字符', () => {
    const hint = describeIdCardInput('110101199001011X34')
    expect(hint.tone).toBe('warn')
    expect(hint.message).toContain('末位可为 X')
  })
})
