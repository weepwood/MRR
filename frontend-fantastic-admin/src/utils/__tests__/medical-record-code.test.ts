import { describe, expect, it } from 'vitest'
import {
  getArchiveLookupValidationMessage,
  normalizeMedicalRecordCode,
  requiresSjhForBah,
} from '../medical-record-code'

describe('medical record archive lookup rules', () => {
  it('normalizes short numeric codes to eight digits', () => {
    expect(normalizeMedicalRecordCode('123')).toBe('00000123')
  })

  it('allows a bah below 10000000 without sjh', () => {
    expect(requiresSjhForBah('9999999')).toBe(false)
    expect(getArchiveLookupValidationMessage('9999999', '')).toBe('')
  })

  it('requires sjh when bah is 10000000 or greater', () => {
    expect(requiresSjhForBah('10000000')).toBe(true)
    expect(requiresSjhForBah('10000001')).toBe(true)
    expect(getArchiveLookupValidationMessage('10000000', ''))
      .toBe('病案号大于等于 10000000 时必须输入上架号')
  })

  it('allows a non-unique bah when the unique sjh is provided', () => {
    expect(getArchiveLookupValidationMessage('10000000', '456')).toBe('')
  })

  it('allows searching by the unique sjh only', () => {
    expect(getArchiveLookupValidationMessage('', '456')).toBe('')
  })
})
