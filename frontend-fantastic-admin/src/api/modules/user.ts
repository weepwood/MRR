import type { AuthRole, AuthRoleUpdatePayload, AuthUser, AuthUserUpdatePayload, LoginResponse, PaginatedResult } from '../types'
import { deleteRequest, getRequest, postRequest, putRequest } from '../index'

const AUTH_SKIP_GLOBAL_ERROR = { skipGlobalError: true }

export default {
  login: (data: { account: string, password: string }) => postRequest<LoginResponse>(
    '/api/v1/auth/login',
    { username: data.account, password: data.password },
    AUTH_SKIP_GLOBAL_ERROR,
  ),

  register: (data: { account: string, password: string, displayName?: string }) => postRequest<LoginResponse>(
    '/api/v1/auth/register',
    { username: data.account, password: data.password, displayName: data.displayName },
    AUTH_SKIP_GLOBAL_ERROR,
  ),

  permission: () => getRequest<AuthUser>('/api/v1/auth/me', AUTH_SKIP_GLOBAL_ERROR),

  passwordEdit: (data: { password: string, newPassword: string }) => postRequest<void>(
    '/api/v1/auth/password/edit',
    data,
  ),

  logout: () => postRequest<void>('/api/v1/auth/logout'),

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

  updateRole: (code: string, data: AuthRoleUpdatePayload) =>
    putRequest<AuthRole, AuthRoleUpdatePayload>(`/api/v1/auth/roles/${code}`, data),

  updateUser: (id: string | number, data: AuthUserUpdatePayload) =>
    putRequest<AuthUser, AuthUserUpdatePayload>(`/api/v1/auth/users/${id}`, data),

  disableUser: (id: string | number) => deleteRequest<void>(`/api/v1/auth/users/${id}`),
}
