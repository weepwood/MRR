import { describe, expect, it } from 'vitest'
import { createArchiveExportPlanner } from './archive-export'
import { createDemoRecords } from './records'

describe('demo archive export planning', () => {
  it('resolves normalized archive keys, enforces the high BAH boundary and plans ZIP jobs', () => {
    const { scans } = createDemoRecords()
    const planner = createArchiveExportPlanner(scans)
    expect(planner.zip({ bah: scans[0].bah }).code).toBe(400)
    expect(planner.zip({}).code).toBe(400)
    expect(planner.zip({ sjh: 'unknown' }).code).toBe(404)
    const plan = planner.zip({ sjh: `0${scans[0].sjh}` }).data
    expect(plan).toMatchObject({ format: 'ZIP', executionMode: 'BACKEND_JOB', selectedCount: 1, totalCount: 1, wholeArchive: true, estimatedBytes: scans[0].fileSize })
    expect(planner.zip({ bah: '00800001' }).data?.selectedCount).toBe(14)
  })

  it('plans exact selected images instead of unrelated archive totals', () => {
    const { scans } = createDemoRecords()
    const planner = createArchiveExportPlanner(scans)
    const plan = planner.selection({ ids: ['1', '1', '2'] }).data!
    expect(plan).toMatchObject({ format: 'PDF', executionMode: 'CLIENT_PDF', selectedCount: 2, totalCount: 2, wholeArchive: false, estimatedBytes: scans[0].fileSize + scans[1].fileSize })
    expect(planner.selection({ ids: [] }).code).toBe(400)
    expect(planner.selection({ ids: ['99999'] }).code).toBe(404)
    expect(planner.selection({ ids: ['1', '99999'] }).code).toBe(404)
    expect(planner.pdf({ bah: '00800001', selectedCount: 1 }).data?.executionMode).toBe('CLIENT_PDF')
    expect(planner.pdf({ bah: '00800001', selectedCount: 14 }).data?.executionMode).toBe('BACKEND_STREAM')
    expect(planner.pdf({ bah: '00800001', selectedCount: 15 }).code).toBe(400)
  })
})
