import { useUserStore } from '@/store/modules/user'

// 获取当前用户信息
export function getCurrentUser() {
  const userStore = useUserStore()
  return {
    username: userStore.account,
    roleCode: 'ADMIN' // 暂时硬编码，后续从API获取
  }
}

// 获取用户显示名称
export function getUserDisplayName() {
  const userStore = useUserStore()
  return userStore.account || ''
}

// 获取用户角色名称
export function getUserRoleName() {
  // 暂时返回ADMIN，后续从API获取
  return 'ADMIN'
}

// 检查是否为管理员用户
export function isAdminUser() {
  // 暂时返回true，后续从API获取权限信息
  return true
}

// 检查是否拥有指定权限
export function hasPermission(permission: string) {
  const userStore = useUserStore()
  const permissions = userStore.permissions || []
  return permissions.includes(permission)
}

// 检查是否拥有任意指定权限
export function hasAnyPermission(permissions: string[]) {
  return permissions.some(permission => hasPermission(permission))
}

// 清除会话信息
export function clearSession() {
  const userStore = useUserStore()
  userStore.requestLogout()
}

// 检查是否已登录
export function isAuthenticated() {
  const userStore = useUserStore()
  return userStore.isLogin
}
