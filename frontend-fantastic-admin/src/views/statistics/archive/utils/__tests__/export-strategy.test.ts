import { describe, expect, it } from 'vitest'
import {
  CLIENT_PDF_MAX_IMAGES,
  resolveArchiveExportMode,
} from '../export-strategy'

describe('archive export strategy', () => {
  it('always routes archive ZIP through the backend stream', () => {
    expect(resolveArchiveExportMode({
      format: 'zip',
      selectedCount: 1,
      totalCount: 100,
    })).toBe('backend-stream')
  })

  it('keeps a small partial PDF selection in the browser', () => {
    expect(resolveArchiveExportMode({
      format: 'pdf',
      selectedCount: CLIENT_PDF_MAX_IMAGES,
      totalCount: CLIENT_PDF_MAX_IMAGES + 1,
    })).toBe('client-pdf')
  })

  it('routes a complete archive PDF through the backend', () => {
    expect(resolveArchiveExportMode({
      format: 'pdf',
      selectedCount: 3,
      totalCount: 3,
    })).toBe('backend-stream')
  })

  it('routes a large partial PDF selection through the backend', () => {
    expect(resolveArchiveExportMode({
      format: 'pdf',
      selectedCount: CLIENT_PDF_MAX_IMAGES + 1,
      totalCount: CLIENT_PDF_MAX_IMAGES + 5,
    })).toBe('backend-stream')
  })
})
