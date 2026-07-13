import { describe, expect, it } from 'vitest'
import {
  formatMedicalRecordCode,
  normalizeMedicalRecordCode,
  normalizeMedicalRecordCodeFields,
  toMedicalRecordCodeSearchTerm,
} from './medical-record-code'

describe('medical record code utilities', () => {
  it('pads numeric bah and sjh values to eight digits', () => {
    expect(normalizeMedicalRecordCode('123')).toBe('00000123')
    expect(normalizeMedicalRecordCode(' 123 ')).toBe('00000123')
    expect(normalizeMedicalRecordCode(123)).toBe('00000123')
    expect(normalizeMedicalRecordCode('12345678')).toBe('12345678')
  })

  it('keeps unsupported values visible instead of truncating them', () => {
    expect(normalizeMedicalRecordCode('123456789')).toBe('123456789')
    expect(normalizeMedicalRecordCode(' SJH001 ')).toBe('SJH001')
    expect(formatMedicalRecordCode(null)).toBe('-')
    expect(formatMedicalRecordCode('NULL')).toBe('-')
  })

  it('produces the same search term for padded and unpadded values', () => {
    expect(toMedicalRecordCodeSearchTerm('123')).toBe('123')
    expect(toMedicalRecordCodeSearchTerm('00000123')).toBe('123')
    expect(toMedicalRecordCodeSearchTerm('00000000')).toBe('0')
  })

  it('normalizes nested bah and sjh fields without mutating the input', () => {
    const input = {
      bah: '123',
      nested: {
        SJH: 45,
        untouched: '123',
      },
      rows: [{ Bah: '00000678' }],
    }

    const normalized = normalizeMedicalRecordCodeFields(input)

    expect(normalized).toEqual({
      bah: '00000123',
      nested: {
        SJH: '00000045',
        untouched: '123',
      },
      rows: [{ Bah: '00000678' }],
    })
    expect(input.bah).toBe('123')
    expect(input.nested.SJH).toBe(45)
  })
})
