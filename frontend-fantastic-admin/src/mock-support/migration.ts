import type { MockScanRecord } from './records'
import { getMedicalRecordTypeLabel } from '@/constants/medical-record-types'
import { pageSvg } from './image'

function hasValidShelf(scan: MockScanRecord) {
  return /^\d{4,}$/.test(String(scan.sjh ?? '').trim())
}

export function migrationSnapshot(scans: readonly MockScanRecord[], now = Date.now()) {
  const uploaded = scans.filter(scan => scan.uploadFlag !== 0)
  const migrated = uploaded.filter(scan => Boolean(scan.ossUrl))
  const unmigrated = uploaded.filter(scan => !scan.ossUrl)
  const waiting = unmigrated.filter(scan => !hasValidShelf(scan))
  const pending = unmigrated.filter((scan) => {
    if (!hasValidShelf(scan)) { return false }
    if (!scan.migrationStatus || ['not_migrated', 'waiting_sjh'].includes(scan.migrationStatus)) { return true }
    return scan.migrationStatus === 'retry_wait'
      && (!scan.migrationNextRetryAt || Date.parse(scan.migrationNextRetryAt) <= now)
  })
  const counts = new Map<string, number>()
  for (const scan of pending) {
    counts.set(scan.folder, (counts.get(scan.folder) ?? 0) + 1)
  }
  const folders = [...counts].map(([folder, cnt]) => ({ folder, cnt })).sort((a, b) => a.folder.localeCompare(b.folder))
  return {
    pending,
    waiting,
    folders,
    statistics: {
      totalCount: uploaded.length,
      migratedCount: migrated.length,
      pendingCount: pending.length,
      waitingSjhCount: waiting.length,
      failedCount: unmigrated.filter(scan => scan.migrationStatus === 'failed').length,
      retryWaitCount: unmigrated.filter(scan => scan.migrationStatus === 'retry_wait').length,
      migratingCount: unmigrated.filter(scan => scan.migrationStatus === 'migrating').length,
      percentage: uploaded.length ? Math.round(migrated.length * 10000 / uploaded.length) / 100 : 0,
    },
  }
}

export function resetDemoFailures(scans: MockScanRecord[], ids: number[], now = Date.now()) {
  const selected = new Set(ids)
  let updated = 0
  for (const scan of scans) {
    if (!selected.has(scan.id) || scan.ossUrl || scan.migrationStatus !== 'failed') { continue }
    Object.assign(scan, {
      migrationStatus: 'not_migrated',
      migrationAttempts: 0,
      migrationErrorCode: undefined,
      migrationNextRetryAt: null,
      migrationUpdatedAt: new Date(now).toISOString(),
    })
    updated++
  }
  return updated
}

export function uploadDemoScans(scans: MockScanRecord[], ids: number[], now = Date.now()) {
  const timestamp = new Date(now).toISOString()
  const results = ids.map((scanId) => {
    const scan = scans.find(item => item.id === scanId)
    if (!scan) { return { scanId, status: 'failed', errorMessage: '演示扫描记录不存在' } }
    if (scan.ossUrl) { return { scanId, status: 'skipped', ossUrl: scan.ossUrl } }
    if (!scan.uploadFlag) { return { scanId, status: 'skipped', errorMessage: '尚未上传本地影像' } }
    if (!hasValidShelf(scan)) {
      Object.assign(scan, { migrationStatus: 'waiting_sjh', migrationErrorCode: 'MISSING_SJH', migrationNextRetryAt: null, migrationUpdatedAt: timestamp })
      return { scanId, status: 'waiting_sjh', errorMessage: '等待合法上架号' }
    }
    if (!migrationSnapshot([scan], now).pending.length) {
      return { scanId, status: 'skipped', errorMessage: '当前不可领取，请检查失败或重试状态' }
    }
    scan.migrationAttempts = (scan.migrationAttempts ?? 0) + 1
    scan.migrationUpdatedAt = timestamp
    if (scan.id % 7 === 0 && scan.migrationAttempts <= 2) {
      Object.assign(scan, {
        migrationStatus: 'retry_wait',
        migrationErrorCode: 'SOURCE_TIMEOUT',
        migrationNextRetryAt: new Date(now + 60_000).toISOString(),
      })
      return { scanId, status: 'retry_wait', errorMessage: '演示：影像源连接超时' }
    }
    const ossUrl = pageSvg(scan.bah, scan.id, getMedicalRecordTypeLabel(scan.btype))
    Object.assign(scan, {
      ossUrl,
      migrationStatus: 'migrated',
      migratedAt: timestamp,
      migrationErrorCode: undefined,
      migrationNextRetryAt: null,
    })
    return { scanId, status: 'success', ossUrl, fileSize: scan.fileSize, checksumMd5: scan.checksumMd5 }
  })
  const success = results.filter(item => ['success', 'skipped'].includes(item.status)).length
  const waitingSjh = results.filter(item => item.status === 'waiting_sjh').length
  return { results, total: results.length, success, waitingSjh, failed: results.length - success - waitingSjh }
}
