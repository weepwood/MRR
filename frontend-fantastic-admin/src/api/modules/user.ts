import type { AuthRole, AuthRoleUpdatePayload, AuthUser, AuthUserUpdatePayload, LoginResponse, PaginatedResult } from '../types'
import type {
  AdminCreateUserPayload,
  AdminResetPasswordPayload,
  RegistrationApprovalPayload,
  RegistrationPayload,
  RegistrationRejectionPayload,
  RegistrationResult,
  RequiredPasswordChangePayload,
  UserCredentialResult,
} from '../user-credential-types'
import { deleteRequest, getRequest, postRequest, putRequest } from '../index'

const AUTH_SKIP_GLOBAL_ERROR = { skipGlobalError: true }

export default {
  login: (data: { account: string, password: string }) => postRequest<LoginResponse>(
    '/api/v1/auth/login',
    { username: data.account, password: data.password },
    AUTH_SKIP_GLOBAL_ERROR,
  ),

  register: (data: RegistrationPayload) => postRequest<RegistrationResult>(
    '/api/v1/auth/register',
    data,
    AUTH_SKIP_GLOBAL_ERROR,
  ),

  permission: () => getRequest<AuthUser>('/api/v1/auth/me', AUTH_SKIP_GLOBAL_ERROR),

  passwordEdit: (data: { password: string, newPassword: string }) => postRequest<void>(
    '/api/v1/auth/password/edit',
    data,
  ),

  requiredPasswordChange: (data: RequiredPasswordChangePayload) => postRequest<void>(
    '/api/v1/auth/password/required-change',
    data,
    AUTH_SKIP_GLOBAL_ERROR,
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

  createUser: (data: AdminCreateUserPayload) => postRequest<UserCredentialResult>(
    '/api/v1/auth/users',
    data,
  ),

  approveRegistration: (id: string | number, data: RegistrationApprovalPayload) => postRequest<AuthUser>(
    `/api/v1/auth/users/${id}/registration/approve`,
    data,
  ),

  rejectRegistration: (id: string | number, data: RegistrationRejectionPayload) => postRequest<AuthUser>(
    `/api/v1/auth/users/${id}/registration/reject`,
    data,
  ),

  resetUserPassword: (id: string | number, data: AdminResetPasswordPayload) => postRequest<UserCredentialResult>(
    `/api/v1/auth/users/${id}/password/reset`,
    data,
  ),

  getRoles: () => getRequest<AuthRole[]>('/api/v1/auth/roles'),

  updateRole: (code: string, data: AuthRoleUpdatePayload) =>
    putRequest<AuthRole, AuthRoleUpdatePayload>(`/api/v1/auth/roles/${code}`, data),

  updateUser: (id: string | number, data: AuthUserUpdatePayload) =>
    putRequest<AuthUser, AuthUserUpdatePayload>(`/api/v1/auth/users/${id}`, data),

  disableUser: (id: string | number) => deleteRequest<void>(`/api/v1/auth/users/${id}`),
}
