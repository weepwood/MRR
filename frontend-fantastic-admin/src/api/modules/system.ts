import { getRequest } from '../index'

export function getSystemHealth() {
  return getRequest('/api/v1/system/health')
}

export function getSystemOverview() {
  return getRequest('/api/v1/system/overview')
}

export function getSystemRuntime() {
  return getRequest('/api/v1/system/runtime')
}

export function getSystemMemory() {
  return getRequest('/api/v1/system/memory')
}

export function getSystemInfo() {
  return getRequest('/api/v1/system/info')
}

export function getSystemProperties() {
  return getRequest('/api/v1/system/properties')
}
