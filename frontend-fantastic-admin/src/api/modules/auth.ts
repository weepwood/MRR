import api from '../index'
import type { RegisterRequest } from '../types'

export function login(credentials: { username: string, password: string }) {
  return api.post('/api/v1/auth/login', credentials, { skipGlobalError: true })
}

export function register(data: RegisterRequest) {
  return api.post('/api/v1/auth/register', data, { skipGlobalError: true })
}

export function getCurrentUser() {
  return api.get('/api/v1/auth/me', { skipGlobalError: true })
}

export function getAuthUsers() {
  return api.get('/api/v1/auth/users')
}

export function getAuthRoles() {
  return api.get('/api/v1/auth/roles')
}

export function updateAuthUser(id: string | number, payload: any) {
  return api.put(`/api/v1/auth/users/${id}`, payload)
}

export function disableAuthUser(id: string | number) {
  return api.delete(`/api/v1/auth/users/${id}`)
}
