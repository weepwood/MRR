import api from '../index'

export function login(credentials: { username: string, password: string }) {
  return api.post('/login', credentials, { skipGlobalError: true })
}

export function getCurrentUser() {
  return api.get('/v1/auth/me', { skipGlobalError: true })
}

export function getAuthUsers() {
  return api.get('/v1/auth/users')
}

export function getAuthRoles() {
  return api.get('/v1/auth/roles')
}

export function updateAuthUser(id: string | number, payload: any) {
  return api.put(`/v1/auth/users/${id}`, payload)
}

export function disableAuthUser(id: string | number) {
  return api.delete(`/v1/auth/users/${id}`)
}
