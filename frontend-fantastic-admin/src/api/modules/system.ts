import api from '../index'

export function getSystemRuntime() {
  return api.get('/system/runtime')
}

export function getSystemProperties() {
  return api.get('/system/properties')
}

export function getSystemMemory() {
  return api.get('/system/memory')
}

export function getSystemInfo() {
  return api.get('/system/info')
}

export function getSystemHealth() {
  return api.get('/system/health')
}

export function getSystemOverview() {
  return api.get('/system/overview')
}
