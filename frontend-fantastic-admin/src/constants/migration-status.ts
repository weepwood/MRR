/** Shared display mapping for scan migration and migration jobs; raw codes stay unchanged. */
export type MigrationTagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

export const MIGRATION_STATUS_LABELS: Record<string, { label: string, type: MigrationTagType }> = {
  pending: { label: '等待启动', type: 'warning' },
  running: { label: '迁移中', type: 'primary' },
  cancelling: { label: '正在取消', type: 'warning' },
  cancelled: { label: '已取消', type: 'info' },
  interrupted: { label: '已中断', type: 'warning' },
  completed: { label: '已完成', type: 'success' },
  completed_with_errors: { label: '完成但有失败', type: 'warning' },
  failed: { label: '失败', type: 'danger' },
  success: { label: '成功', type: 'success' },
  retry_wait: { label: '等待重试', type: 'warning' },
  waiting_sjh: { label: '等待上架号', type: 'info' },
  migrating: { label: '迁移中', type: 'primary' },
  migrated: { label: '已迁移', type: 'success' },
  verified: { label: '已验证', type: 'success' },
  not_migrated: { label: '未迁移', type: 'info' },
  skipped: { label: '已跳过', type: 'info' },
}

export function migrationStatusMeta(status?: string) {
  const code = status?.trim() ?? ''
  return (Object.hasOwn(MIGRATION_STATUS_LABELS, code) ? MIGRATION_STATUS_LABELS[code] : undefined)
    ?? { label: code || '-', type: 'info' as const }
}

export function migrationStatusTag(status?: string) {
  const meta = migrationStatusMeta(status)
  const tone: 'info' | 'neutral' | 'success' | 'warning' | 'danger' = meta.type === 'primary' ? 'info' : meta.type === 'info' ? 'neutral' : meta.type
  return { label: meta.label, tone }
}
