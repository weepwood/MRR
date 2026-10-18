import { describe, expect, it } from 'vitest'
import { checkPermission, PERMISSION_DEFINITIONS } from '../permission'

describe('ocr permission boundaries', () => {
  it('keeps status, full text, execution and automatic authorization separate', () => {
    expect(checkPermission(['ocr:read'], 'ocr:text-read')).toBe(false)
    expect(checkPermission(['ocr:read'], 'ocr:run')).toBe(false)
    expect(checkPermission(['ocr:run'], 'ocr:batch-run')).toBe(false)
    expect(checkPermission(['classification:review'], 'classification:auto-apply')).toBe(false)
    expect(PERMISSION_DEFINITIONS.map(item => item.value)).toContain('classification:auto-apply')
  })
})
