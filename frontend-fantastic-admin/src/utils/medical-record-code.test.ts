import { describe, expect, it } from 'vitest'
import {
  formatMedicalRecordCode,
  normalizeMedicalRecordCode,
  toMedicalRecordCodeSearchTerm,
} from './medical-record-code'

describe('medical record code utilities', () => {
  it('preserves original bah and sjh values', () => {
    expect(normalizeMedicalRecordCode('123')).toBe('123')
    expect(normalizeMedicalRecordCode(' 123 ')).toBe('123')
    expect(normalizeMedicalRecordCode(123)).toBe('123')
    expect(normalizeMedicalRecordCode('12345678')).toBe('12345678')
  })

  it('keeps unsupported values visible instead of truncating them', () => {
    expect(normalizeMedicalRecordCode('123456789')).toBe('123456789')
    expect(normalizeMedicalRecordCode(' SJH001 ')).toBe('SJH001')
    expect(formatMedicalRecordCode(null)).toBe('-')
    expect(formatMedicalRecordCode('NULL')).toBe('NULL')
  })

  it('produces the same search term for padded and unpadded values', () => {
    expect(toMedicalRecordCodeSearchTerm('123')).toBe('123')
    expect(toMedicalRecordCodeSearchTerm('00000123')).toBe('123')
    expect(toMedicalRecordCodeSearchTerm('00000000')).toBe('0')
  })
})
