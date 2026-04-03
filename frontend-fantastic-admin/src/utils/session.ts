import { useUserStore } from '@/store/modules/user'

export function getCurrentUser() {
  return useUserStore().profile || {}
}

export function getUserDisplayName() {
  const profile = useUserStore().profile
  return profile?.displayName || profile?.username || ''
}

export function getUserRoleName() {
  const profile = useUserStore().profile
  return profile?.roleName || profile?.roleCode || ''
}

export function isAdminUser() {
  const profile = useUserStore().profile
  const permissions = useUserStore().permissions || []
  return String(profile?.roleCode || '').toUpperCase() === 'ADMIN'
    || permissions.includes('user:manage')
    || permissions.includes('role:manage')
}

export function hasPermission(permission: string) {
  return useUserStore().permissions.includes(permission)
}

export function hasAnyPermission(permissions: string[]) {
  return permissions.some(permission => hasPermission(permission))
}

export function clearSession() {
  useUserStore().requestLogout()
}

export function isAuthenticated() {
  return useUserStore().isLogin
}
