import api from '../index'

export async function getSystemRuntime() {
  const response = await api.get('/v1/system/runtime')
  return response.data
}

export async function getSystemProperties() {
  const response = await api.get('/v1/system/properties')
  return response.data
}

export async function getSystemMemory() {
  const response = await api.get('/v1/system/memory')
  return response.data
}

export async function getSystemInfo() {
  const response = await api.get('/v1/system/info')
  return response.data
}

export async function getSystemHealth() {
  const response = await api.get('/v1/system/health')
  return response.data
}

export async function getSystemOverview() {
  const response = await api.get('/v1/system/overview')
  return response.data
}
