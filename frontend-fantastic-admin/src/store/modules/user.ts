import type { AuthProfile } from '@/utils/auth-storage'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import apiUser from '@/api/modules/user'
import router from '@/router'
import {
  clearAuthSessionStorage,
  readAuthStorage,
  writeAuthProfile,
  writeAuthSession,
} from '@/utils/auth-storage'
import { useMenuStore } from './menu'
import { useRouteStore } from './route'
import { useSettingsStore } from './settings'
import { useTabbarStore } from './tabbar'

export type AuthSessionStatus = 'anonymous' | 'candidate' | 'verifying' | 'verified' | 'unavailable'

const isDemoMode = import.meta.env.VITE_APP_DEMO_MODE

export const useUserStore = defineStore('user', () => {
  const settingsStore = useSettingsStore()
  const routeStore = useRouteStore()
  const menuStore = useMenuStore()
  const tabbarStore = useTabbarStore()
  const initialSession = readAuthStorage()

  const account = ref(initialSession.account)
  const token = ref(initialSession.token)
  const avatar = ref(initialSession.avatar)
  const permissions = ref<string[]>(initialSession.permissions)
  const profile = ref<AuthProfile>(initialSession.profile)
  const sessionStatus = ref<AuthSessionStatus>(initialSession.token ? 'candidate' : 'anonymous')

  const isLogin = computed(() => Boolean(token.value))
  const isSessionVerified = computed(() => isLogin.value && sessionStatus.value === 'verified')
  const mustChangePassword = computed(() => isSessionVerified.value && Boolean(profile.value.mustChangePassword))

  function persistProfile(nextProfile: AuthProfile) {
    profile.value = nextProfile
    permissions.value = Array.isArray(nextProfile.permissions) ? nextProfile.permissions : []
    account.value = nextProfile.displayName || nextProfile.username || account.value || ''
    avatar.value = typeof nextProfile.avatar === 'string' ? nextProfile.avatar : avatar.value
    writeAuthProfile(nextProfile, account.value, permissions.value)
  }

  function setSession(session: { token: string, user: AuthProfile }) {
    const nextToken = session.token?.trim() || ''
    if (!nextToken) {
      clearSession()
      return
    }
    const user = session.user || {}
    token.value = nextToken
    avatar.value = typeof user.avatar === 'string' ? user.avatar : ''
    account.value = user.displayName || user.username || ''
    profile.value = user
    permissions.value = Array.isArray(user.permissions) ? user.permissions : []
    sessionStatus.value = 'verified'
    writeAuthSession({
      token: token.value,
      account: account.value,
      avatar: avatar.value,
      profile: profile.value,
      permissions: permissions.value,
    })
  }

  async function login(data: { account: string, password: string }) {
    const res = await apiUser.login(data)
    const payload: any = res.data || {}
    const loginData: any = payload.data || payload
    const returnedUser: AuthProfile = loginData.user || loginData.profile || payload.user || {}
    const user = returnedUser.displayName || returnedUser.username
      ? returnedUser
      : { ...returnedUser, username: data.account }
    setSession({ token: loginData.token || loginData.accessToken || loginData.jwt || '', user })
    return loginData.nextAction || (user.mustChangePassword ? 'CHANGE_PASSWORD' : 'NONE')
  }

  async function logout(redirect = router.currentRoute.value.fullPath) {
    const redirectTarget = redirect
    const shouldRevokeToken = !isDemoMode && Boolean(token.value)
    if (shouldRevokeToken) {
      try {
        await apiUser.logout()
      }
      catch {
        // 即使服务端不可达，也要保证本地会话可以退出。
      }
    }
    clearSession()
    await router.push({
      name: 'login',
      query: {
        ...(redirectTarget !== settingsStore.settings.home.fullPath && router.currentRoute.value.name !== 'login' && { redirect: redirectTarget }),
      },
    }).catch(() => {})
  }

  async function requestLogout() {
    clearSession()
    await router.push({ name: 'login' }).catch(() => {})
  }

  function clearSession() {
    clearAuthSessionStorage()
    token.value = ''
    account.value = ''
    avatar.value = ''
    permissions.value = []
    profile.value = {}
    sessionStatus.value = 'anonymous'
    settingsStore.updateSettings({}, true)
    tabbarStore.clean()
    routeStore.removeRoutes()
    menuStore.setActived(0)
  }

  async function refreshProfile() {
    const res = await apiUser.permission()
    const nextProfile: AuthProfile = (res.data || {}) as AuthProfile
    persistProfile(nextProfile)
    sessionStatus.value = 'verified'
  }

  async function verifySession() {
    if (!token.value) {
      sessionStatus.value = 'anonymous'
      return false
    }
    if (sessionStatus.value === 'verified') {
      return true
    }

    sessionStatus.value = 'verifying'
    try {
      await refreshProfile()
      return true
    }
    catch (error: any) {
      if (!token.value || error?.response?.status === 401) {
        sessionStatus.value = 'anonymous'
      }
      else {
        sessionStatus.value = 'unavailable'
      }
      throw error
    }
  }

  async function getPermissions() {
    await refreshProfile()
  }

  function markSessionVerified() {
    if (token.value) {
      sessionStatus.value = 'verified'
    }
  }

  function markPasswordChangeRequired() {
    const nextProfile = { ...profile.value, mustChangePassword: true }
    sessionStatus.value = isLogin.value ? 'verified' : 'anonymous'
    persistProfile(nextProfile)
  }

  async function editPassword(data: { password: string, newPassword: string }) {
    await apiUser.passwordEdit(data)
  }

  return {
    account,
    token,
    avatar,
    permissions,
    profile,
    sessionStatus,
    isLogin,
    isSessionVerified,
    mustChangePassword,
    login,
    setSession,
    logout,
    requestLogout,
    clearSession,
    verifySession,
    getPermissions,
    markSessionVerified,
    markPasswordChangeRequired,
    editPassword,
  }
})
