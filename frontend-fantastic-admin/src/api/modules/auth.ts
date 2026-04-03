import api from '../index'

export function login(credentials: any) {
  return api.post('', credentials)
}

export function getCurrentUser() {
  return api.get('/auth/me')
}

export function getAuthUsers() {
  return api.get('/auth/users')
}

export function getAuthRoles() {
  return api.get('/auth/roles')
}

export function updateAuthUser(id: string | number, payload: any) {
  return api.put(`/auth/users/${id}`, payload)
}

export function disableAuthUser(id: string | number) {
  return api.delete(`/auth/users/${id}`)
}
