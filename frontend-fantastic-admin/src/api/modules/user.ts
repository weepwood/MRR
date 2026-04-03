import api from '../index'

export default {
  // 登录
  login: (data: {
    account: string
    password: string
  }) => api.post('', data),

  // 获取权限
  permission: () => api.get('/auth/roles'),

  // 修改密码
  passwordEdit: (data: {
    password: string
    newPassword: string
  }) => api.post('/auth/password/edit', data),

  // 获取用户列表
  getUsers: () => api.get('/auth/users'),

  // 更新用户
  updateUser: (id: string | number, data: any) => api.put(`/auth/users/${id}`, data),

  // 禁用用户
  disableUser: (id: string | number) => api.delete(`/auth/users/${id}`),
}
