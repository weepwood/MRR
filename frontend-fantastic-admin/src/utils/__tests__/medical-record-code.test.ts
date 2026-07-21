import { describe, expect, it } from 'vitest'
import {
  getArchiveLookupValidationMessage,
  normalizeMedicalRecordCode,
  requiresSjhForBah,
  resolveArchiveLookup,
} from '../medical-record-code'

describe('medical record archive lookup rules', () => {
  it('normalizes short numeric codes to eight digits', () => {
    expect(normalizeMedicalRecordCode('123')).toBe('00000123')
  })

  it('uses bah below 10000000 even when sjh is also present', () => {
    expect(requiresSjhForBah('9999999')).toBe(false)
    expect(getArchiveLookupValidationMessage('9999999', '')).toBe('')
    expect(resolveArchiveLookup('9999999', '456')).toEqual({
      mode: 'bah',
      bah: '09999999',
      sjh: '00000456',
      requestBah: '09999999',
      validationMessage: '',
    })
  })

  it('uses sjh when bah is 10000000 or greater', () => {
    expect(requiresSjhForBah('10000000')).toBe(true)
    expect(requiresSjhForBah('10000001')).toBe(true)
    expect(resolveArchiveLookup('10000000', '456')).toEqual({
      mode: 'sjh',
      bah: '10000000',
      sjh: '00000456',
      requestSjh: '00000456',
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
      sjh: '00000456',
      requestSjh: '00000456',
      validationMessage: '',
    })
  })
})
