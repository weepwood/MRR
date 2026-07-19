import type { HealthInfo, MemoryInfo, RuntimeInfo, SystemInfo, SystemOverview } from '../types'
import { getRequest } from '../index'

export interface OperationsDiskStatus {
  status?: string
  path?: string
  totalBytes?: number
  usableBytes?: number
  usedPercent?: number
  error?: string
}

export interface OperationsStatus {
  mode?: string
  checkedAt?: string
  audit?: {
    status?: string
    queuedEvents?: number
    lastFailure?: string | null
  }
  backup?: {
    status?: string
    completedAt?: string | null
    ageHours?: number | null
    dumpSizeBytes?: number
    secondaryCopyPath?: string | null
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
