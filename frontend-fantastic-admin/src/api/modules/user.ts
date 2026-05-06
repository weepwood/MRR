import api from '../index'

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

  getUsers: () => api.get('/api/v1/auth/users'),

  getRoles: () => api.get('/api/v1/auth/roles'),

  updateUser: (id: string | number, data: any) => api.put(`/api/v1/auth/users/${id}`, data),

  disableUser: (id: string | number) => api.delete(`/api/v1/auth/users/${id}`),
}
