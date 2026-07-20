import { checkPermission } from '@/utils/permission'

export default function useAuth() {
  function hasPermission(permission: string) {
    const settingsStore = useSettingsStore()
    const userStore = useUserStore()
    if (String(userStore.profile?.roleCode || '').toUpperCase() === 'ADMIN') {
      return true
    }
    if (settingsStore.settings.app.enablePermission) {
      return checkPermission(userStore.permissions || [], permission)
    }
    else {
      return true
    }
  }

  function auth(value: string | string[]) {
    let auth
    if (typeof value === 'string') {
      auth = value !== '' ? hasPermission(value) : true
    }
    else {
      auth = value.length > 0 ? value.some(item => hasPermission(item)) : true
    }
    return auth
  }

  function authAll(value: string[]) {
    return value.length > 0 ? value.every(item => hasPermission(item)) : true
  }

  return {
    auth,
    authAll,
  }
}
