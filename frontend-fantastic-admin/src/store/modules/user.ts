import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import apiUser from '@/api/modules/user'
import router from '@/router'
import { useSettingsStore } from './settings'
import { useRouteStore } from './route'
import { useMenuStore } from './menu'
import { useTabbarStore } from './tabbar'

type Profile = {
  id?: number
  username?: string
  displayName?: string
  roleCode?: string
  roleName?: string
  permissions?: string[]
  status?: string
  lastLoginAt?: string
}

export const useUserStore = defineStore('user', () => {
  const settingsStore = useSettingsStore()
  const routeStore = useRouteStore()
  const menuStore = useMenuStore()
  const tabbarStore = useTabbarStore()

  const account = ref(localStorage.account ?? '')
  const token = ref(localStorage.token ?? '')
  const avatar = ref(localStorage.avatar ?? '')
  const permissions = ref<string[]>(JSON.parse(localStorage.permissions ?? '[]'))
  const profile = ref<Profile>(JSON.parse(localStorage.profile ?? 'null') ?? {})

  const isLogin = computed(() => Boolean(token.value))

  function persistProfile(nextProfile: Profile) {
    profile.value = nextProfile
    permissions.value = Array.isArray(nextProfile.permissions) ? nextProfile.permissions : []

    localStorage.setItem('profile', JSON.stringify(nextProfile))
    localStorage.setItem('permissions', JSON.stringify(permissions.value))
    localStorage.setItem('account', nextProfile.displayName || nextProfile.username || account.value || '')
  }

  function setSession(session: { token: string, user: Profile }) {
    const user = session.user || {}
    token.value = session.token || ''
    localStorage.setItem('token', session.token || '')
    avatar.value = user.avatar || ''
    localStorage.setItem('avatar', user.avatar || '')
    account.value = user.displayName || user.username || ''
    persistProfile(user)
  }

  async function login(data: { account: string, password: string }) {
    const res = await apiUser.login(data)
    const payload = res.data || {}
    const loginData = payload.data || payload
    const user = loginData.user || loginData.profile || payload.user || {}

    setSession({ token: loginData.token || loginData.accessToken || loginData.jwt || '', user })
    if (!user.displayName && !user.username) {
      account.value = data.account
    }
  }

  function logout(redirect = router.currentRoute.value.fullPath) {
    localStorage.removeItem('token')
    token.value = ''
    router.push({
      name: 'login',
      query: {
        ...(redirect !== settingsStore.settings.home.fullPath && router.currentRoute.value.name !== 'login' && { redirect }),
      },
    }).then(clearSession)
  }

  function requestLogout() {
    localStorage.removeItem('token')
    token.value = ''
    router.push({
      name: 'login',
      query: {
        ...(router.currentRoute.value.fullPath !== settingsStore.settings.home.fullPath
          && router.currentRoute.value.name !== 'login'
          && { redirect: router.currentRoute.value.fullPath }),
      },
    }).then(clearSession)
  }

  function clearSession() {
    ;['account', 'avatar', 'profile', 'permissions'].forEach(key => localStorage.removeItem(key))
    account.value = ''
    avatar.value = ''
    permissions.value = []
    profile.value = {}
    settingsStore.updateSettings({}, true)
    tabbarStore.clean()
    routeStore.removeRoutes()
    menuStore.setActived(0)
  }

  async function getPermissions() {
    const res = await apiUser.permission()
    const nextProfile = res.data || {}
    account.value = nextProfile.displayName || nextProfile.username || account.value
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
    isLogin,
    login,
    setSession,
    logout,
    requestLogout,
    clearSession,
    getPermissions,
    editPassword,
  }
})
