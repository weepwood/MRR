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

const manifestMembers = new Map(demoRecords.patients.map(patient => [patient.id, activeScans(patient.id).map(scan => scan.id)]))

function manifestVersion(version: number) {
  const archiveId = version > 1000 ? version - 1000 : version
  const members = manifestMembers.get(archiveId)
  if (!members) { return null }
  const ids = version > 1000 ? members : members.slice(0, Math.max(0, members.length - 1))
  return {
    ids,
    observation: { version, archiveId, status: 'COMPLETED', processed: ids.length, verified: 0, anomalies: ids.length, snapshotCount: ids.length, completeDigestAvailable: false, createdAt: hoursAgo(version > 1000 ? 2 : 26), updatedAt: hoursAgo(version > 1000 ? 1 : 25) },
  }
}

const demoOrders = [{ id: 1, issueId: 2, scanId: 3, entityType: 'mr_scan', entityId: '3', targetArchiveId: 2, status: 'PROPOSED', proposedBy: 2, approvedBy: null }]
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
    url: '/api/v1/system/data-relations/archives/:archiveId/scans',
    response: ({ params, query }) => {
      const archiveId = Number(params.archiveId)
      const limit = Number(query?.limit ?? 50)
      const afterId = query?.afterId == null ? null : Number(query.afterId)
      const afterPage = query?.afterPage == null ? null : Number(query.afterPage)
      if (!Number.isSafeInteger(archiveId) || archiveId <= 0 || !Number.isInteger(limit) || limit < 1 || limit > 100 || (afterId != null && (!Number.isSafeInteger(afterId) || afterId <= 0)) || (afterPage != null && (afterId == null || !Number.isInteger(afterPage)))) { return demoError(400, '无效分页') }
      const rows = activeScans(archiveId).sort((a, b) => a.pages - b.pages || a.id - b.id).filter(row => afterId == null || (afterPage != null && (row.pages > afterPage || (row.pages === afterPage && row.id > afterId)))).slice(0, limit + 1)
      const more = rows.length > limit
      if (more) { rows.pop() }
      const last = rows.at(-1)
      return demoOk({ archiveId, rows: rows.map(s => ({ id: s.id, archiveId: s.archiveId, uploadFlag: s.uploadFlag, pages: s.pages, btype: s.btype, migrationStatus: s.migrationStatus })), nextCursor: more && last ? { pages: last.pages, id: last.id } : null })
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
        scanSamples: scans.map(s => ({ id: s.id, archiveId: s.archiveId, uploadFlag: s.uploadFlag, pages: s.pages, filename: s.filename, btype: s.btype, migrationStatus: s.migrationStatus, hasOss: Boolean(s.ossUrl) })),
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
      const latest = manifestVersion(1000 + id)?.observation
      return latest ? demoOk({ enabled: false, latest }) : demoError(404, '演示病案不存在')
    },
  },
  {
    url: '/api/v1/operations/archive-manifests/history',
    response: ({ query }) => {
      const id = Number(query?.archiveId)
      const rows = [manifestVersion(1000 + id), manifestVersion(id)].flatMap(row => row ? [row.observation] : [])
      return demoOk(rows.filter(row => !query?.before || row.version < Number(query.before)))
    },
  },
  {
    url: '/api/v1/operations/archive-manifests/compare',
    response: ({ query }) => {
      const archiveId = Number(query?.archiveId)
      const baseVersion = Number(query?.baseVersion)
      const targetVersion = Number(query?.targetVersion)
      if (![archiveId, baseVersion, targetVersion].every(id => Number.isSafeInteger(id) && id > 0) || baseVersion === targetVersion) {
        return demoError(400, '请选择不同版本')
      }
      const base = manifestVersion(baseVersion)
      const target = manifestVersion(targetVersion)
      if (!base || !target || base.observation.archiveId !== archiveId || target.observation.archiveId !== archiveId) {
        return demoError(409, '版本不属于同一病案或不存在')
      }
      const unchanged = target.ids.filter(id => base.ids.includes(id)).length
      return demoOk({ archiveId, baseVersion, targetVersion, baseCount: base.ids.length, targetCount: target.ids.length, added: target.ids.length - unchanged, removed: base.ids.length - unchanged, changed: 0, unchanged, digestComparison: 'UNAVAILABLE' })
    },
  },
  {
    url: '/api/v1/operations/archive-manifests/:version',
    response: ({ params }) => {
      const row = manifestVersion(Number(params.version))?.observation
      return row ? demoOk(row) : demoError(404, '演示版本不存在')
    },
  },
  {
    url: '/api/v1/operations/archive-manifests/:version/report',
    response: ({ params, query }) => {
      const row = manifestVersion(Number(params.version))
      if (!row || row.observation.archiveId !== Number(query?.archiveId)) { return demoError(409, '演示版本与病案不一致') }
      const count = row.ids.length
      return demoOk({
        ...row.observation,
        segments: count ? [{ number: 1, firstOrdinal: 1, lastOrdinal: count, planned: count, processed: count, verified: 0, anomalies: count }] : [],
        anomalyCounts: count ? { UNVERIFIED: count } : {},
      })
    },
  },
  {
    url: '/api/v1/operations/archive-manifests/:version/anomalies',
    response: ({ params, query }) => demoOk((manifestVersion(Number(params.version))?.ids ?? []).map((id, i) => ({
      cursor: i + 1,
      scanId: id,
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
  {
    url: '/api/v1/system/data-quality/repair-orders/:id',
    response: ({ params }) => {
      const row = demoOrders.find(order => order.id === Number(params.id))
      return row ? demoOk(row) : demoError(404, '演示工单不存在')
    },
  },
  { url: '/api/v1/system/data-quality/case-reviews', response: () => demoOk({ enabled: false, reviews: [] }) },
  { url: '/api/v1/system/data-quality/case-reviews/issue/:issueId', response: () => demoOk({ enabled: false, latest: false, reviewedThisSample: false, review: null, events: [] }) },
  { url: '/api/v1/system/data-quality/case-reviews/:id/recheck', response: () => demoError(404, '演示无可复验案件') },
  { url: '/api/v1/system/data-quality/case-reviews/:id/events', response: () => demoError(404, '演示无人工决策历史') },
  { url: '/api/v1/system/data-quality/case-reviews/issue/:issueId', method: 'POST', response: () => demoError(503, '演示不写入真实决策') },
  { url: '/api/v1/system/data-quality/repair-orders', method: 'POST', response: () => demoError(503, '演示不执行真实修复') },
  { url: '/api/v1/system/data-quality/repair-orders/:id/approve', method: 'POST', response: () => demoError(503, '演示不执行真实修复') },
  { url: '/api/v1/system/data-quality/repair-orders/:id/apply', method: 'POST', response: () => demoError(503, '演示不执行真实修复') },
  { url: '/api/v1/system/data-quality/repair-orders/:id/verify', method: 'POST', response: () => demoError(503, '演示不执行真实修复') },
  { url: '/api/v1/system/data-quality/repair-orders/:id/cancel', method: 'POST', response: () => demoError(503, '演示不执行真实修复') },
  { url: '/api/v1/system/data-quality/repair-orders/:id/compensate', method: 'POST', response: () => demoError(503, '演示不执行真实修复') },
  { url: '/api/v1/operations/archive-manifests', method: 'POST', response: () => demoError(503, '演示不执行真实文件校验') },
  { url: '/api/v1/operations/archive-manifests/:version/resume', method: 'POST', response: () => demoError(503, '演示不恢复真实文件校验') },
  { url: '/api/v1/operations/archive-manifests/:version/cancel', method: 'POST', response: () => demoError(503, '演示不执行真实文件校验') },
])
