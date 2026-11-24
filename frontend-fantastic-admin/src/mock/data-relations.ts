import type { ArchiveSearchResult, RelationCoverage } from '@/api/modules/data-relations'
import { defineFakeRoute } from 'vite-plugin-fake-server/client'
import { demoRecords } from '../mock-support/records'
import { boundedInt, demoError, demoOk, hoursAgo } from '../mock-support/response'

function activeScans(id?: number) {
  return demoRecords.scans.filter(scan => scan.uploadFlag !== 0 && (id == null || scan.archiveId === id))
}

function archives(): ArchiveSearchResult[] {
  return demoRecords.patients.map((patient) => {
    const scans = activeScans(patient.id)
    return {
      id: patient.id,
      bah: patient.bah,
      sjh: scans[0]?.sjh ?? null,
      patientName: patient.name,
      department: patient.department,
      scanCount: scans.length,
      pageCount: scans.reduce((n, scan) => n + scan.pages, 0),
      matchType: 'EXACT',
    }
  })
}

function coverage(tableName: string, label: string, total: number, linked: number): RelationCoverage {
  return {
    tableName,
    label,
    relation: '演示关联',
    totalCount: total,
    linkedCount: linked,
    missingCount: total - linked,
    coverage: total ? linked / total * 100 : null,
    estimated: false,
    source: 'DEMO_SYNTHETIC',
    coverageIncluded: true,
    status: total === linked ? 'HEALTHY' : 'WARNING',
  }
}

const key = (value: unknown) => String(value ?? '').trim().replace(/^0+(?=\d)/, '')

function manifest(id: number) {
  const archive = archives().find(row => row.id === id)
  if (!archive) { return null }
  return { version: id, archiveId: id, status: 'COMPLETED', processed: archive.scanCount ?? 0, verified: 0, anomalies: archive.scanCount ?? 0, snapshotCount: archive.scanCount ?? 0, completeDigestAvailable: false, createdAt: hoursAgo(2), updatedAt: hoursAgo(1) }
}

const demoOrders = [{ id: 1, issueId: 2, scanId: 3, targetArchiveId: 2, status: 'PROPOSED', proposedBy: 2, approvedBy: null }]
export default defineFakeRoute([
  {
    url: '/api/v1/system/data-relations/overview',
    response: () => {
      const scans = activeScans()
      const linked = scans.filter(scan => archives().some(archive => archive.id === scan.archiveId)).length
      return demoOk({
        generatedAt: hoursAgo(0),
        archiveCount: archives().length,
        healthScore: scans.length ? linked / scans.length * 100 : null,
        relations: [coverage('mr_scan', '扫描记录', scans.length, linked), coverage('mr_patient', '患者记录', demoRecords.patients.length, demoRecords.patients.length)],
        latestQualityRun: null,
        relationChecks: [],
        notes: ['合成演示数据，非生产统计'],
      })
    },
  },
  {
    url: '/api/v1/system/data-relations/archives/search',
    response: ({ query }) => {
      const value = String(query?.value ?? '').trim()
      if (!value) {
        return demoOk([])
      }
      const type = String(query?.type ?? '')
      const rows = archives().flatMap((archive) => {
        const values = type === 'ARCHIVE_ID' ? [String(archive.id)] : type === 'BAH' ? [archive.bah] : type === 'SJH' ? activeScans(archive.id).map(scan => scan.sjh) : []
        if (!values.some(v => v && key(v) === key(value))) {
          return []
        }
        return [{ ...archive, matchType: values.includes(value) ? 'EXACT' : 'FORMAT_ONLY' }]
      })
      return demoOk(rows.slice(0, boundedInt(query?.limit, 20, 100)))
    },
  },
  {
    url: '/api/v1/system/data-relations/archives/:archiveId',
    response: ({ params }) => {
      const archive = archives().find(row => row.id === Number(params.archiveId))
      if (!archive) {
        return demoError(404, '演示病案不存在')
      }
      const scans = activeScans(archive.id)
      const patients = demoRecords.patients.filter(p => p.id === archive.id).map(p => ({ id: p.id, name: p.name, bah: p.bah, department: p.department, relationMode: 'DEMO' }))
      return demoOk({
        archive,
        statistics: [],
        patients,
        boxes: [],
        scanSummary: { activeCount: scans.length, unlinkedCandidateCount: 0 },
        scanSamples: scans.map(s => ({ id: s.id, pages: s.pages, filename: s.filename, btype: s.btype, migrationStatus: s.migrationStatus, hasOss: Boolean(s.ossUrl) })),
        migrationSummary: {},
        comparisons: patients.map(p => ({ field: 'BAH', canonicalValue: archive.bah, source: 'mr_patient', sourceValue: p.bah, status: 'EXACT' })),
        warnings: ['合成演示记录，不能用于真实关联修复'],
        readOnly: true,
      })
    },
  },
  {
    url: '/api/v1/operations/archive-manifests',
    response: ({ query }) => {
      const id = Number(query?.archiveId)
      const latest = manifest(id)
      return latest ? demoOk({ enabled: false, latest }) : demoError(404, '演示病案不存在')
    },
  },
  {
    url: '/api/v1/operations/archive-manifests/history',
    response: ({ query }) => {
      const row = manifest(Number(query?.archiveId))
      return demoOk(row && (!query?.before || row.version < Number(query.before)) ? [row] : [])
    },
  },
  {
    url: '/api/v1/operations/archive-manifests/:version',
    response: ({ params }) => {
      const row = manifest(Number(params.version))
      return row ? demoOk(row) : demoError(404, '演示版本不存在')
    },
  },
  {
    url: '/api/v1/operations/archive-manifests/:version/anomalies',
    response: ({ params, query }) => demoOk(activeScans(Number(params.version)).map((scan, i) => ({
      cursor: i + 1,
      scanId: scan.id,
      status: 'UNVERIFIED',
      bytesRead: 0,
      digestAvailable: false,
      checkedAt: hoursAgo(1),
    })).filter(row => row.cursor > Number(query?.after ?? 0)).slice(0, 100)),
  },
  {
    url: '/api/v1/system/data-quality/repair-orders',
    response: ({ query }) => demoOk({
      enabled: false,
      actorId: 1,
      orders: demoOrders.filter(row => (!query?.archiveId || row.targetArchiveId === Number(query.archiveId))
        && (!query?.issueId || row.issueId === Number(query.issueId)) && (!query?.before || row.id < Number(query.before))),
    }),
  },
  {
    url: '/api/v1/system/data-quality/repair-orders/:id/events',
    response: ({ params, query }) => Number(params.id) === 1
      ? demoOk(Number(query?.after ?? 0) < 1 ? [{ id: 1, actorId: 2, action: 'PROPOSED', beforeArchiveId: null, afterArchiveId: null, createdAt: hoursAgo(2) }] : [])
      : demoError(404, '演示工单不存在'),
  },
  { url: '/api/v1/system/data-quality/repair-orders', method: 'POST', response: () => demoError(503, '演示不执行真实修复') },
  { url: '/api/v1/system/data-quality/repair-orders/:id/approve', method: 'POST', response: () => demoError(503, '演示不执行真实修复') },
  { url: '/api/v1/system/data-quality/repair-orders/:id/apply', method: 'POST', response: () => demoError(503, '演示不执行真实修复') },
  { url: '/api/v1/system/data-quality/repair-orders/:id/verify', method: 'POST', response: () => demoError(503, '演示不执行真实修复') },
  { url: '/api/v1/system/data-quality/repair-orders/:id/cancel', method: 'POST', response: () => demoError(503, '演示不执行真实修复') },
  { url: '/api/v1/system/data-quality/repair-orders/:id/compensate', method: 'POST', response: () => demoError(503, '演示不执行真实修复') },
  { url: '/api/v1/operations/archive-manifests', method: 'POST', response: () => demoError(503, '演示不执行真实文件校验') },
  { url: '/api/v1/operations/archive-manifests/:version/cancel', method: 'POST', response: () => demoError(503, '演示不执行真实文件校验') },
])
