import api from '../index'

export function getSystemHealth() {
  return api.get('/api/v1/system/health')
}

export function getSystemOverview() {
  return api.get('/api/v1/system/overview')
}

export function getSystemRuntime() {
  return api.get('/api/v1/system/runtime')
}

export function getSystemMemory() {
  return api.get('/api/v1/system/memory')
}

export function getSystemInfo() {
  return api.get('/api/v1/system/info')
}

export function getSystemProperties() {
  return api.get('/api/v1/system/properties')
}
