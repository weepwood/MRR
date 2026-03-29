import { defineStore } from 'pinia'

export interface SessionUser {
  username?: string
  displayName?: string
  roleCode?: string
  roleName?: string
  permissions?: string[]
  [key: string]: unknown
}

interface UserState {
  token: string
  user: SessionUser | null
}

const ADMIN_PERMISSION_CANDIDATES = ['user:manage', 'role:manage', 'auth:user:manage', 'auth:role:manage']

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: '',
    user: null
  }),
  actions: {
    setSession(session: any) {
      if (!session) {
        this.clearSession()
        return
      }
      this.token = session.token || session.accessToken || session.jwt || ''
      this.user = session.user || session.currentUser || null
    },
    clearSession() {
      this.token = ''
      this.user = null
    }
  },
  getters: {
    hasPermission: (state) => (permission: string): boolean => {
      if (!permission) return false
      const permissions = state.user?.permissions || []
      return permissions.includes(permission)
    },
    hasAnyPermission: (state) => (permissions: string[] = []): boolean => {
      if (!permissions.length) return false
      const userPermissions = state.user?.permissions || []
      return permissions.some(p => userPermissions.includes(p))
    },
    isAdminUser: (state): boolean => {
      if (!state.user) return false
      const roleCode = (state.user.roleCode || '').toUpperCase()
      const perms = state.user?.permissions || []
      return roleCode === 'ADMIN' || ADMIN_PERMISSION_CANDIDATES.some(p => perms.includes(p))
    },
    userDisplayName: (state): string => {
      return state.user?.displayName || state.user?.username || ''
    },
    userRoleName: (state): string => {
      return state.user?.roleName || state.user?.roleCode || ''
    }
  },
  persist: {
    key: 'pmr-auth-user',
    storage: window.localStorage,
    pick: ['token', 'user']
  }
})
