import { describe, expect, it } from 'vitest'
import {
  getMedicalRecordTypeLabel,
  MEDICAL_RECORD_TYPE_CODES,
  MEDICAL_RECORD_TYPES,
} from './medical-record-types'

describe('medical record types', () => {
  it('defines the complete ordered 1-15 type list', () => {
    expect(MEDICAL_RECORD_TYPE_CODES).toEqual([
      1, 2, 3, 4, 5,
      6, 7, 8, 9, 10,
      11, 12, 13, 14, 15,
    ])
    expect(MEDICAL_RECORD_TYPES).toHaveLength(15)
  })

  it('uses the canonical labels for renamed and new types', () => {
    expect(getMedicalRecordTypeLabel(2)).toBe('02-病程录')
    expect(getMedicalRecordTypeLabel(11)).toBe('11-新生儿')
    expect(getMedicalRecordTypeLabel(13)).toBe('13-大病史')
    expect(getMedicalRecordTypeLabel(15)).toBe('15-分娩记录')
  })

  it('keeps a readable fallback for unknown values', () => {
    expect(getMedicalRecordTypeLabel(null)).toBe('未分类')
    expect(getMedicalRecordTypeLabel(99)).toBe('类型 99')
  })
})
