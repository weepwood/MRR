import { useUserStore } from '@/store/modules/user'
import { checkAnyPermission, checkPermission } from './permission'

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
  return String(profile?.roleCode || '').toUpperCase() === 'ADMIN'
}

export function hasPermission(permission: string) {
  if (isAdminUser()) {
    return true
  }
  return checkPermission(useUserStore().permissions || [], permission)
}

export function hasAnyPermission(permissions: string[]) {
  if (isAdminUser()) {
    return true
  }
  return checkAnyPermission(useUserStore().permissions || [], permissions)
}

export function clearSession() {
  useUserStore().requestLogout()
}

export function isAuthenticated() {
  return useUserStore().isLogin
}
