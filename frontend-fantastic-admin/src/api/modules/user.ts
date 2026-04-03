import api from '../index'

export default {
  login: (data: { account: string, password: string }) => api.post('/login', {
    username: data.account,
    password: data.password,
  }, { skipGlobalError: true }),

  permission: () => api.get('/v1/auth/me', { skipGlobalError: true }),

  passwordEdit: (data: { password: string, newPassword: string }) => api.post('/v1/auth/password/edit', data),

  getUsers: () => api.get('/v1/auth/users'),

  updateUser: (id: string | number, data: any) => api.put(`/v1/auth/users/${id}`, data),

  disableUser: (id: string | number) => api.delete(`/v1/auth/users/${id}`),
}
