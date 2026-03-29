import { authApi } from './config'

export function getSystemRuntime() {
  return authApi.get('/system/runtime')
}

export function getSystemProperties() {
  return authApi.get('/system/properties')
}

export function getSystemMemory() {
  return authApi.get('/system/memory')
}

export function getSystemInfo() {
  return authApi.get('/system/info')
}

export function getSystemHealth() {
  return authApi.get('/system/health')
}

export function getSystemOverview() {
  return authApi.get('/system/overview')
}
