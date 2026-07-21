import { getRequest, postRequest } from '../index'

export interface DatabaseOverview {
  database: {
    name?: string
    version?: string
    size_bytes?: number
    uptime_seconds?: number
    max_connections?: number
  }
  connections: {
    total?: number
    active?: number
    idle?: number
    idle_in_transaction?: number
    waiting?: number
  }
  transactions: {
    commits?: number
    rollbacks?: number
    deadlocks?: number
    temp_files?: number
    temp_bytes?: number
    cache_hit_ratio?: number
  }
  contention: {
    lock_waiters?: number
    long_transactions?: number
    longest_transaction_seconds?: number
  }
  hikari: {
    available?: boolean
    maximum_pool_size?: number
    minimum_idle?: number
    active?: number
    idle?: number
    total?: number
    pending?: number
  }
  tables: DatabaseTableStat[]
}

export interface DatabaseTableStat {
  schemaname?: string
  table_name?: string
  total_bytes?: number
  table_bytes?: number
  index_bytes?: number
  live_rows?: number
  dead_rows?: number
  seq_scan?: number
  idx_scan?: number
  last_autovacuum?: string
  last_autoanalyze?: string
}

export interface DataQualityRun {
  id?: number
  status?: string
  triggered_by?: string
  check_count?: number
  total_issues?: number
  critical_count?: number
  warning_count?: number
  started_at?: string
  completed_at?: string
  error_message?: string
}

export interface DataQualityCheck {
  check_code: string
  check_name: string
  severity: 'CRITICAL' | 'WARNING'
  issue_count: number
  sampled_count: number
  checked_at?: string
}

export interface DataQualitySummary {
  enabled: boolean
  running: boolean
  latestRun: DataQualityRun | null
  checks: DataQualityCheck[]
}

export interface DataQualityIssue {
  id: number
  check_code: string
  check_name: string
  severity: 'CRITICAL' | 'WARNING'
  entity_type?: string
  entity_id?: string
  bah?: string
  sjh?: string
  detail?: string
  detected_at?: string
}

export function getDatabaseOverview() {
  return getRequest<DatabaseOverview>('/api/v1/system/database/overview')
}

export function getLargestDatabaseTables(limit = 20) {
  return getRequest<DatabaseTableStat[]>('/api/v1/system/database/tables', { params: { limit } })
}

export function getDataQualitySummary() {
  return getRequest<DataQualitySummary>('/api/v1/system/data-quality/summary')
}

export function getDataQualityIssues(limit = 100) {
  return getRequest<DataQualityIssue[]>('/api/v1/system/data-quality/issues', { params: { limit } })
}

export function runDataQualityChecks() {
  return postRequest<DataQualitySummary>('/api/v1/system/data-quality/run')
}
