import type { Router, RouteRecordRaw } from 'vue-router'
import { useNProgress } from '@vueuse/integrations/useNProgress'
import { asyncRoutes, asyncRoutesByFilesystem } from './routes'
import '@/assets/styles/nprogress.css'

function setupRoutes(router: Router) {
  router.beforeEach(async (to) => {
    const settingsStore = useSettingsStore()
    const userStore = useUserStore()
    const routeStore = useRouteStore()
    const menuStore = useMenuStore()

    if (userStore.isLogin) {
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
          if (settingsStore.settings.app.enablePermission) {
            await userStore.getPermissions()
          }

          switch (settingsStore.settings.app.routeBaseOn) {
            case 'frontend':
              routeStore.generateRoutesAtFront(asyncRoutes)
              break
            case 'backend':
              await routeStore.generateRoutesAtBack()
              break
            case 'filesystem':
              routeStore.generateRoutesAtFilesystem(asyncRoutesByFilesystem)
              switch (settingsStore.settings.menu.baseOn) {
                case 'frontend':
                  menuStore.generateMenusAtFront()
                  break
                case 'backend':
                  await menuStore.generateMenusAtBack()
                  break
              }
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
          userStore.logout()
          return {
            name: 'login',
            query: {
              redirect: to.fullPath !== settingsStore.settings.home.fullPath ? to.fullPath : undefined,
            },
          }
        }
      }
    }
    else {
      if (to.name !== 'login') {
        return {
          name: 'login',
          query: {
            redirect: to.fullPath !== settingsStore.settings.home.fullPath ? to.fullPath : undefined,
          },
        }
      }
    }
  })
}

// 当父级路由未配置重定向时，自动重定向到有访问权限的子路由
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

// 进度条
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

// 标题
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

// 页面缓存
function setupKeepAlive(router: Router) {
  router.afterEach(async (to, from) => {
    const keepAliveStore = useKeepAliveStore()
    if (to.fullPath !== from.fullPath) {
      if (to.meta.cache) {
        const componentName = to.matched.at(-1)?.components?.default.name
        if (componentName) {
          // 缓存当前页面前，先判断是否需要进行清除缓存，判断依据：
          // 1. 如果 to.meta.cache 为 boolean 类型，并且不为 true，则需要清除缓存
          // 2. 如果 to.meta.cache 为 string 类型，并且与 from.name 不一致，则需要清除缓存
          // 3. 如果 to.meta.cache 为 array 类型，并且不包含 from.name，则需要清除缓存
          // 4. 如果 to.meta.noCache 为 string 类型，并且与 from.name 一致，则需要清除缓存
          // 5. 如果 to.meta.noCache 为 array 类型，并且包含 from.name，则需要清除缓存
          // 6. 如果是刷新页面，则需要清除缓存
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
          // turbo-console-disable-next-line
          console.warn('[MRR-ADMIN] 该页面组件未设置组件名，会导致缓存失效，请检查')
        }
      }
    }
  })
}

// 其他
function setupOther(router: Router) {
  router.afterEach(() => {
    document.documentElement.scrollTop = 0
  })
}

export default function setupGuards(router: Router) {
  setupRoutes(router)
  setupRedirectAuthChildrenRoute(router)
  setupProgress(router)
  setupTitle(router)
  setupKeepAlive(router)
  setupOther(router)
}
