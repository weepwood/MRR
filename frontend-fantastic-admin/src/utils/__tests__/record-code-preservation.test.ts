import { describe, expect, it } from 'vitest'
import { normalizeMedicalRecordCode, resolveArchiveLookup } from '../medical-record-code'

describe('original medical record codes', () => {
  it('preserves short codes, original zeros and abnormal historical values', () => {
    for (const code of ['123', '00000123', '0', '123456789', 'SJH-A1', 'NULL']) {
      expect(normalizeMedicalRecordCode(` ${code} `)).toBe(code)
    }
  })

  it('preserves the actual query and display identifiers', () => {
    expect(resolveArchiveLookup('123', '456')).toMatchObject({ bah: '123', sjh: '456', requestBah: '123' })
  })
})
