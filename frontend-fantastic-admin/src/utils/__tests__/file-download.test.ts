import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createCsvBlob,
  downloadBlob,
  getResponseHeader,
  resolveDownloadFileName,
} from '../file-download'

describe('file download utilities', () => {
  beforeEach(() => {
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:test-download'),
      revokeObjectURL: vi.fn(),
    })
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('reads axios-style headers and resolves RFC 5987 filenames', () => {
    const headers = {
      get: (name: string) => name === 'content-disposition'
        ? 'attachment; filename*=UTF-8\'\'mr_%E7%97%85%E6%A1%88.csv'
        : undefined,
    }

    expect(getResponseHeader(headers, 'content-disposition')).toContain('filename*=')
    expect(resolveDownloadFileName(headers, 'fallback.csv')).toBe('mr_病案.csv')
  })

  it('sanitizes unsafe response filenames', () => {
    expect(resolveDownloadFileName(
      { 'content-disposition': 'attachment; filename="../mr:patient.csv"' },
      'fallback.csv',
    )).toBe('..-mr-patient.csv')
  })

  it('creates spreadsheet-safe UTF-8 BOM CSV reports', async () => {
    const blob = createCsvBlob(
      ['rowNumber', 'message', 'value'],
      [[2, '字段包含,逗号', '=SUM(1,1)'], [3, '包含"引号"', '普通值']],
    )
    const text = await readBlob(blob)

    expect(text.startsWith('\uFEFFrowNumber,message,value\r\n')).toBe(true)
    expect(text).toContain('"字段包含,逗号","\'=SUM(1,1)"')
    expect(text).toContain('"包含""引号""",普通值')
  })

  it('defers object URL cleanup until after the click', () => {
    vi.useFakeTimers()
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    downloadBlob(new Blob(['data']), 'report.csv')

    expect(click).toHaveBeenCalledOnce()
    expect(URL.revokeObjectURL).not.toHaveBeenCalled()
    vi.runAllTimers()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:test-download')
  })
})

function readBlob(blob: Blob) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(reader.error)
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.readAsText(blob)
  })
}
