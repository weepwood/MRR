import { authApi, loginApi } from './config'

export function login(credentials: any) {
  return loginApi.post('', credentials)
}

export function getCurrentUser() {
  return authApi.get('/auth/me')
}

export function getAuthUsers() {
  return authApi.get('/auth/users')
}

export function getAuthRoles() {
  return authApi.get('/auth/roles')
}

export function updateAuthUser(id: string | number, payload: any) {
  return authApi.put(`/auth/users/${id}`, payload)
}

export function disableAuthUser(id: string | number) {
  return authApi.delete(`/auth/users/${id}`)
}
