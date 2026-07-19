import type { HealthInfo, MemoryInfo, RuntimeInfo, SystemInfo, SystemOverview } from '../types'
import { getRequest } from '../index'

export interface OperationsDiskStatus {
  status?: string
  location?: 'SERVER' | 'IMAGES' | string
  /** 兼容旧界面；后端只返回逻辑位置，不返回绝对路径。 */
  path?: string
  totalBytes?: number
  usableBytes?: number
  usedPercent?: number
  errorType?: string
  error?: string
}

export interface OperationsStatus {
  mode?: string
  checkedAt?: string
  application?: {
    status?: string
    jvmUptimeSeconds?: number
    availableProcessors?: number
    heapUsedBytes?: number
    heapCommittedBytes?: number
    heapMaxBytes?: number
    heapUsedPercent?: number
  }
  database?: {
    status?: string
    responseTimeMs?: number
    lockWaiters?: number | null
    unlinkedArchiveRecords?: number | null
    errorType?: string
    pool?: {
      status?: string
      active?: number
      idle?: number
      total?: number
      waiting?: number
    }
  }
  audit?: {
    status?: string
    queuedEvents?: number
    deadLetterEvents?: number
    fallbackAvailable?: boolean
    lostEventDetected?: boolean
    lastFailureCode?: string | null
    lastFailureAt?: string | null
    /** 兼容旧界面，值为脱敏后的失败代码。 */
    lastFailure?: string | null
  }
  backup?: {
    status?: string
    completedAt?: string | null
    ageHours?: number | null
    dumpSizeBytes?: number
    secondaryCopyConfigured?: boolean
    secretsIncluded?: boolean
    lastFailureAt?: string | null
    lastErrorCode?: string | null
    lastErrorType?: string | null
    /** 兼容旧界面，只返回“已配置（路径已隐藏）”。 */
    secondaryCopyPath?: string | null
    /** 兼容旧界面，值为稳定错误码，不返回原始异常。 */
    lastError?: string | null
  }
  storage?: {
    server?: OperationsDiskStatus
    images?: OperationsDiskStatus
  }
  logs?: {
    applicationBytes?: number
    errorBytes?: number
    applicationLogExists?: boolean
    errorLogExists?: boolean
  }
}

export function getSystemHealth() {
  return getRequest<HealthInfo>('/api/v1/system/health')
}

export function getSystemOverview() {
  return getRequest<SystemOverview>('/api/v1/system/overview')
}

export function getSystemOperations() {
  return getRequest<OperationsStatus>('/api/v1/system/operations')
}

export function getSystemRuntime() {
  return getRequest<RuntimeInfo>('/api/v1/system/runtime')
}

export function getSystemMemory() {
  return getRequest<MemoryInfo>('/api/v1/system/memory')
}

export function getSystemInfo() {
  return getRequest<SystemInfo>('/api/v1/system/info')
}

export function getSystemProperties() {
  return getRequest<Record<string, string>>('/api/v1/system/properties')
}
