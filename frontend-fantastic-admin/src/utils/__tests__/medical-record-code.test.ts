import { describe, expect, it } from 'vitest'
import {
  getArchiveLookupValidationMessage,
  normalizeMedicalRecordCode,
  padMedicalRecordCode,
  requiresSjhForBah,
  resolveArchiveLookup,
} from '../medical-record-code'

describe('medical record archive lookup rules', () => {
  it('preserves short numeric codes', () => {
    expect(normalizeMedicalRecordCode('123')).toBe('123')
  })

  it('uses bah below 10000000 even when sjh is also present', () => {
    expect(requiresSjhForBah('9999999')).toBe(false)
    expect(getArchiveLookupValidationMessage('9999999', '')).toBe('')
    expect(resolveArchiveLookup('9999999', '456')).toEqual({
      mode: 'bah',
      bah: '9999999',
      sjh: '456',
      requestBah: '9999999',
      validationMessage: '',
    })
  })

  it('uses sjh when bah is 10000000 or greater', () => {
    expect(requiresSjhForBah('10000000')).toBe(true)
    expect(requiresSjhForBah('10000001')).toBe(true)
    expect(resolveArchiveLookup('10000000', '456')).toEqual({
      mode: 'sjh',
      bah: '10000000',
      sjh: '456',
      requestSjh: '456',
      validationMessage: '',
    })
  })

  it('rejects a non-unique bah when sjh is missing', () => {
    expect(getArchiveLookupValidationMessage('10000000', ''))
      .toBe('病案号大于等于 10000000 时必须输入上架号')
    expect(resolveArchiveLookup('10000001', '')).toEqual({
      mode: null,
      bah: '10000001',
      sjh: '',
      validationMessage: '病案号大于等于 10000000 时必须输入上架号',
    })
  })

  it('allows searching by the unique sjh only', () => {
    expect(getArchiveLookupValidationMessage('', '456')).toBe('')
    expect(resolveArchiveLookup('', '456')).toEqual({
      mode: 'sjh',
      bah: '',
      sjh: '456',
      requestSjh: '456',
      validationMessage: '',
    })
  })
})

describe('padMedicalRecordCode', () => {
  it('补齐省略的前导零到 8 位', () => {
    expect(padMedicalRecordCode('123456')).toBe('00123456')
    expect(padMedicalRecordCode('1')).toBe('00000001')
    expect(padMedicalRecordCode('1234567')).toBe('01234567')
  })

  it('已满 8 位或超长时不改值', () => {
    expect(padMedicalRecordCode('00123456')).toBe('00123456')
    expect(padMedicalRecordCode('12345678')).toBe('12345678')
    // 超长说明号本身不合规，补零也救不了，原样留给用户核对
    expect(padMedicalRecordCode('123456789')).toBe('123456789')
  })

  it('非数字的历史异常编号原样保留', () => {
    for (const code of ['SJH-A1', 'NULL', 'A1']) {
      expect(padMedicalRecordCode(code)).toBe(code)
    }
  })

  it('清理首尾空白后再判断，空值仍是空值', () => {
    expect(padMedicalRecordCode(' 123456 ')).toBe('00123456')
    expect(padMedicalRecordCode('')).toBe('')
    expect(padMedicalRecordCode('   ')).toBe('')
    expect(padMedicalRecordCode(null)).toBe('')
  })
})
