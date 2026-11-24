import { describe, expect, it } from 'vitest'
import { sharedObjectKey, sharedObjectEntries, sharedObjectUrl } from './oss-browser'
import { createDemoRecords } from './records'

describe('shared demo OSS objects', () => {
  it('derives directory hierarchy and file metadata from migrated scans only', () => {
    const { scans } = createDemoRecords()
    const scan = scans.find(item => item.ossUrl)!
    const key = sharedObjectKey(scan)!
    const prefix = key.slice(0, key.lastIndexOf('/') + 1)
    const entries = sharedObjectEntries(scans, prefix)
    expect(entries).toEqual([expect.objectContaining({ key, name: scan.filename, directory: false, size: scan.fileSize })])
    expect(sharedObjectUrl(scans, key)).toBe(scan.ossUrl)
    scan.ossUrl = undefined
    expect(sharedObjectEntries(scans, prefix)).toEqual([])
    expect(sharedObjectUrl(scans, key)).toBeUndefined()
  })

  it('rejects unsafe segments and reflects newly uploaded scans without a second fixture', () => {
    const { scans } = createDemoRecords()
    const scan = scans[0]
    const key = sharedObjectKey(scan)!
    expect(sharedObjectUrl(scans, key)).toBeUndefined()
    scan.ossUrl = 'data:image/svg+xml;charset=utf-8,synthetic'
    expect(sharedObjectUrl(scans, key)).toBe(scan.ossUrl)
    expect(sharedObjectEntries(scans, 'medical-records/').some(entry => entry.key === 'medical-records/9000/')).toBe(true)
    scan.filename = '../bad.jpg'
    expect(sharedObjectKey(scan)).toBeUndefined()
    expect(sharedObjectUrl(scans, key)).toBeUndefined()
  })
})
