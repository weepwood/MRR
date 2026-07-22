import type { Router, RouteRecordRaw } from 'vue-router'
import { useNProgress } from '@vueuse/integrations/useNProgress'
import { canUseArchiveLegacyRoute, getRuntimeDeveloperModeStatus } from '@/api/modules/developer-mode'
import { asyncRoutes, asyncRoutesByFilesystem } from './routes'
import '@/assets/styles/nprogress.css'
import '@/assets/styles/archive-legacy-mode.css'

const isDemoMode = import.meta.env.VITE_APP_DEMO_MODE
const PASSWORD_CHANGE_ROUTE_NAME = 'passwordChangeRequired'
const ARCHIVE_LEGACY_ACCESS_MODE = 'archive-legacy'
const EXTERNAL_TICKET_ACCESS_MODE = 'external-ticket'
const EXTERNAL_ARCHIVE_SESSION_STORAGE_KEY = 'MRR-EXTERNAL-ARCHIVE:session'

function setArchiveDomAccessMode(mode = '') {
  if (typeof document === 'undefined') {
    return
  }
  if (mode) {
    document.documentElement.dataset.mrrAccessMode = mode
  }
  else {
    delete document.documentElement.dataset.mrrAccessMode
  }
}

function hasStoredExternalArchiveSession(): boolean {
  if (typeof window === 'undefined') {
    return false
  }
  try {
    const raw = sessionStorage.getItem(EXTERNAL_ARCHIVE_SESSION_STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) as { cases?: unknown[] } : null
    return Array.isArray(parsed?.cases) && parsed.cases.length > 0
  }
  catch {
    return false
  }
}

function firstQueryValue(value: unknown): string {
  return String(Array.isArray(value) ? value[0] : value ?? '').trim()
}

function isExternalTicketArchiveRoute(name: unknown, query: Record<string, unknown>): boolean {
  return name === 'archive'
    && (firstQueryValue(query.external) === 'ticket'
      || firstQueryValue(query.id) === EXTERNAL_TICKET_ACCESS_MODE)
}

function setupRoutes(router: Router) {
  router.beforeEach(async (to) => {
    setArchiveDomAccessMode()

    if (to.name === 'publicStatus' || to.name === 'externalArchive') {
      return true
    }

    if (isExternalTicketArchiveRoute(to.name, to.query)) {
      if (hasStoredExternalArchiveSession()) {
        setArchiveDomAccessMode(EXTERNAL_TICKET_ACCESS_MODE)
        return true
      }
      const bah = firstQueryValue(to.query.bah)
      const sjh = firstQueryValue(to.query.sjh)
      return {
        name: 'externalArchive',
        replace: true,
        query: {
          ...(bah ? { bah } : {}),
          ...(sjh ? { sjh } : {}),
        },
      }
    }

    const settingsStore = useSettingsStore()
    const userStore = useUserStore()
    const routeStore = useRouteStore()
    const menuStore = useMenuStore()
    const loginRedirect = (reason?: string) => ({
      name: 'login',
      replace: true,
      query: {
        ...(to.fullPath !== settingsStore.settings.home.fullPath ? { redirect: to.fullPath } : {}),
        ...(reason ? { session: reason } : {}),
      },
    })

    // localStorage 恢复出的 Token 只是候选会话。必须先通过 /auth/me
    // 获取当前用户，旧权限和旧强制改密标记才允许参与路由判断。
    if (userStore.isLogin && !userStore.isSessionVerified) {
      if (isDemoMode) {
        userStore.markSessionVerified()
      }
      else if (userStore.sessionStatus === 'unavailable' && to.name === 'login') {
        return true
      }
      else {
        try {
          await userStore.verifySession()
        }
        catch (error: any) {
          if (!userStore.isLogin || error?.response?.status === 401) {
            return loginRedirect('expired')
          }

          // 网络错误或认证服务 503 不应误删仍可能有效的 Token。
          // 允许进入登录页重新认证，并在下次访问受保护页面时重试验证。
          console.error('[Router Guard] Session verification unavailable:', error)
          if (to.name === 'login') return true
          return loginRedirect('unavailable')
        }
      }
    }

    if (to.name === PASSWORD_CHANGE_ROUTE_NAME) {
      if (!userStore.isSessionVerified) {
        return loginRedirect()
      }
      if (!userStore.mustChangePassword) {
        return { path: settingsStore.settings.home.fullPath, replace: true }
      }
      return true
    }

    if (userStore.isSessionVerified && userStore.mustChangePassword) {
      return { name: PASSWORD_CHANGE_ROUTE_NAME, replace: true }
    }

    if (userStore.isSessionVerified) {
      if (routeStore.isGenerate) {
        if (settingsStore.settings.menu.mode !== 'single') {
          menuStore.setActived(to.path)
        }

        if (to.name === 'login') {
          return {
            path: settingsStore.settings.home.fullPath,
            replace: true,
          }
        }

        if (!settingsStore.settings.home.enable && to.fullPath === settingsStore.settings.home.fullPath) {
          if (menuStore.sidebarMenus.length > 0) {
            return {
              path: menuStore.sidebarMenusFirstDeepestPath,
              replace: true,
            }
          }
        }
      }
      else {
        try {
          switch (settingsStore.settings.app.routeBaseOn) {
            case 'frontend':
              routeStore.generateRoutesAtFront(asyncRoutes)
              break
            case 'filesystem':
              routeStore.generateRoutesAtFilesystem(asyncRoutesByFilesystem)
              menuStore.generateMenusAtFront()
              break
          }

          const removeRoutes: (() => void)[] = []
          routeStore.routes.forEach((route) => {
            if (!/^(?:https?:|mailto:|tel:)/.test(route.path)) {
              const childRoute = { ...route } as RouteRecordRaw
              if (childRoute.path.startsWith('/')) {
                childRoute.path = childRoute.path.slice(1)
              }
              removeRoutes.push(router.addRoute('layout', childRoute))
            }
          })
          routeStore.setCurrentRemoveRoutes(removeRoutes)

          return {
            path: to.path,
            query: to.query,
            replace: true,
          }
        }
        catch (error) {
          console.error('[Router Guard] Failed to generate routes:', error)
          void userStore.logout()
          return loginRedirect()
        }
      }
    }
    else if (isDemoMode) {
      userStore.setSession({
        token: 'dev-token',
        user: {
          username: 'dev',
          displayName: 'Dev User',
          roleCode: 'ADMIN',
          roleName: 'Administrator',
          status: 'active',
          mustChangePassword: false,
          passwordVersion: 1,
          permissions: [],
        },
      })

      return {
        path: to.path,
        query: to.query,
        replace: true,
      }
    }
    else {
      const developerModeStatus = await getRuntimeDeveloperModeStatus()
      if (canUseArchiveLegacyRoute(to.name, developerModeStatus)) {
        setArchiveDomAccessMode(ARCHIVE_LEGACY_ACCESS_MODE)
        return true
      }

      if (to.name === 'login') {
        return true
      }

      return loginRedirect()
    }
  })
}

function setupRedirectAuthChildrenRoute(router: Router) {
  router.beforeEach((to) => {
    const { auth } = useAuth()
    const currentRoute = router.getRoutes().find(route => route.path === (to.matched.at(-1)?.path ?? ''))
    if (!currentRoute?.redirect) {
      const findAuthRoute = currentRoute?.children?.find(route => route.meta?.menu !== false && auth(route.meta?.auth ?? ''))
      if (findAuthRoute) {
        return findAuthRoute
      }
    }
  })
}

function setupProgress(router: Router) {
  const { isLoading } = useNProgress()
  router.beforeEach(() => {
    const settingsStore = useSettingsStore()
    if (settingsStore.settings.app.enableProgress) {
      isLoading.value = true
    }
  })
  router.afterEach(() => {
    const settingsStore = useSettingsStore()
    if (settingsStore.settings.app.enableProgress) {
      isLoading.value = false
    }
  })
}

function setupTitle(router: Router) {
  router.afterEach((to) => {
    const settingsStore = useSettingsStore()
    if (settingsStore.settings.app.routeBaseOn !== 'filesystem') {
      settingsStore.setTitle(to.matched?.at(-1)?.meta?.title ?? to.meta.title)
    }
    else {
      settingsStore.setTitle(to.meta.title)
    }
  })
}

function setupKeepAlive(router: Router) {
  router.afterEach(async (to, from) => {
    const keepAliveStore = useKeepAliveStore()
    if (to.fullPath !== from.fullPath) {
      if (to.meta.cache) {
        const componentName = to.matched.at(-1)?.components?.default.name
        if (componentName) {
          let shouldClearCache = false
          if (typeof to.meta.cache === 'boolean') {
            shouldClearCache = !to.meta.cache
          }
          else if (typeof to.meta.cache === 'string') {
            shouldClearCache = to.meta.cache !== from.name
          }
          else if (Array.isArray(to.meta.cache)) {
            shouldClearCache = !to.meta.cache.includes(from.name as string)
          }
          if (to.meta.noCache) {
            if (typeof to.meta.noCache === 'string') {
              shouldClearCache = to.meta.noCache === from.name
            }
            else if (Array.isArray(to.meta.noCache)) {
              shouldClearCache = to.meta.noCache.includes(from.name as string)
            }
          }
          if (from.name === 'reload') {
            shouldClearCache = true
          }
          if (shouldClearCache) {
            keepAliveStore.remove(componentName)
            await nextTick()
          }
          keepAliveStore.add(componentName)
        }
        else {
          console.warn('[MRR] 该页面组件未设置组件名，会导致缓存失效，请检查')
        }
      }
    }
  })
}

export default function setupGuards(router: Router) {
  setupRoutes(router)
  setupRedirectAuthChildrenRoute(router)
  setupProgress(router)
  setupTitle(router)
  setupKeepAlive(router)
}
