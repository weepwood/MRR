import { useUserStore } from '@/store/modules/user'
import { checkAnyPermission, checkPermission } from './permission'

export function getCurrentUser() {
  const userStore = useUserStore()
  return userStore.isSessionVerified ? userStore.profile || {} : {}
}

export function getUserDisplayName() {
  const userStore = useUserStore()
  if (!userStore.isSessionVerified) return ''
  return userStore.profile?.displayName || userStore.profile?.username || ''
}

export function getUserRoleName() {
  const userStore = useUserStore()
  if (!userStore.isSessionVerified) return ''
  return userStore.profile?.roleName || userStore.profile?.roleCode || ''
}

export function isAdminUser() {
  const userStore = useUserStore()
  if (!userStore.isSessionVerified) return false
  return String(userStore.profile?.roleCode || '').toUpperCase() === 'ADMIN'
}

export function hasPermission(permission: string) {
  const userStore = useUserStore()
  if (!userStore.isSessionVerified) return false
  if (isAdminUser()) {
    return true
  }
  return checkPermission(userStore.permissions || [], permission)
}

export function hasAnyPermission(permissions: string[]) {
  const userStore = useUserStore()
  if (!userStore.isSessionVerified) return false
  if (isAdminUser()) {
    return true
  }
  return checkAnyPermission(userStore.permissions || [], permissions)
}

export function clearSession() {
  void useUserStore().requestLogout()
}

export function isAuthenticated() {
  return useUserStore().isSessionVerified
}
