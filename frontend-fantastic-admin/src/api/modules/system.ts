import type { HealthInfo, MemoryInfo, RuntimeInfo, SystemInfo, SystemOverview } from '../types'
import { getRequest } from '../index'

export function getSystemHealth() {
  return getRequest<HealthInfo>('/api/v1/system/health')
}

export function getSystemOverview() {
  return getRequest<SystemOverview>('/api/v1/system/overview')
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
