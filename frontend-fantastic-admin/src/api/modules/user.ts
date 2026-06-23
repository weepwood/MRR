import type { AuthRole, AuthUser, AuthUserUpdatePayload, PaginatedResult } from '../types'
import api, { deleteRequest, getRequest, putRequest } from '../index'

export default {
  login: (data: { account: string, password: string }) => api.post('/api/v1/auth/login', {
    username: data.account,
    password: data.password,
  }, { skipGlobalError: true }),

  register: (data: { account: string, password: string, displayName?: string }) => api.post('/api/v1/auth/register', {
    username: data.account,
    password: data.password,
    displayName: data.displayName,
  }, { skipGlobalError: true }),

  permission: () => api.get('/api/v1/auth/me', { skipGlobalError: true }),

  passwordEdit: (data: { password: string, newPassword: string }) => api.post('/api/v1/auth/password/edit', data),

  logout: () => api.post('/api/v1/auth/logout'),

  getUsers: (params: { page?: number, size?: number, keyword?: string, roleCode?: string, status?: string } = {}) =>
    getRequest<PaginatedResult<AuthUser>>('/api/v1/auth/users', {
      params: {
        page: params.page ?? 1,
        size: params.size ?? 20,
        keyword: params.keyword || undefined,
        roleCode: params.roleCode || undefined,
        status: params.status || undefined,
      },
    }),

  getRoles: () => getRequest<AuthRole[]>('/api/v1/auth/roles'),

  updateUser: (id: string | number, data: AuthUserUpdatePayload) =>
    putRequest<AuthUser, AuthUserUpdatePayload>(`/api/v1/auth/users/${id}`, data),

  disableUser: (id: string | number) => deleteRequest<void>(`/api/v1/auth/users/${id}`),
}
