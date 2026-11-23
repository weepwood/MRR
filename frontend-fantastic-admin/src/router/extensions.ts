import type { _RouterClassic, NavigationFailure, RouteLocationRaw } from 'vue-router'
import pinia from '@/store'

/**
 * 框架扩展的路由实例类型：在原生 RouterClassic 之上增加 close 方法。
 * vue-router 5.5 起 `Router` 是条件类型别名，通过增强 TypesConfig
 * 的 Router 槽位让全部 `Router` 类型引用解析到这里。
 */
export type MrrRouter = _RouterClassic & {
  /**
   * 本方法为框架扩展语法，等同于 `push` 方法，并且同时会关闭当前标签页
   */
  close: (to: RouteLocationRaw) => Promise<NavigationFailure | void | undefined>
}

declare module 'vue-router' {
  interface TypesConfig {
    Router: MrrRouter
  }
}

function getId(router: MrrRouter) {
  return router.currentRoute.value.fullPath
}

function extendPush(router: MrrRouter) {
  const originalPush = router.push
  router.push = function (to: RouteLocationRaw) {
    const settingsStore = useSettingsStore(pinia)
    if (settingsStore.settings.tabbar.enable) {
      const tabbarStore = useTabbarStore(pinia)
      const index = tabbarStore.list.findIndex(item => item.tabId === getId(router))
      tabbarStore.$patch({
        leaveIndex: index,
      })
    }
    return originalPush(to)
  }
}

function extendGo(router: MrrRouter) {
  const originalGo = router.go
  router.go = function (delta: number) {
    const settingsStore = useSettingsStore(pinia)
    if (settingsStore.settings.tabbar.enable) {
      const tabId = getId(router)
      const tabbarStore = useTabbarStore(pinia)
      originalGo(delta)
      if (delta < 0) {
        tabbarStore.remove(tabId)
      }
    }
    else {
      originalGo(delta)
    }
  }
}

function extendReplace(router: MrrRouter) {
  const originalReplace = router.replace
  router.replace = function (to: RouteLocationRaw) {
    const settingsStore = useSettingsStore(pinia)
    if (settingsStore.settings.tabbar.enable) {
      const tabId = getId(router)
      const tabbarStore = useTabbarStore(pinia)
      return originalReplace(to).then(() => {
        tabbarStore.remove(tabId)
      })
    }
    else {
      return originalReplace(to)
    }
  }
}

// vue-router 5.5 起 `Router` 是解析到 RouterClassic 的条件类型别名，
// 对 `interface Router` 的模块增强不再生效；close 方法的增强定义
// 已通过上方 MrrRouter 与 TypesConfig 的 Router 槽位实现。

function extendClose(router: MrrRouter) {
  router.close = function (to: RouteLocationRaw) {
    const currentRoute = router.currentRoute.value
    const tabId = getId(router)
    return router.push(to).then(() => {
      const settingsStore = useSettingsStore(pinia)
      if (settingsStore.settings.tabbar.enable && currentRoute.meta.tabMerge !== 'activeMenu') {
        const tabbarStore = useTabbarStore(pinia)
        tabbarStore.remove(tabId)
      }
    })
  }
}

export default function setupExtensions(router: MrrRouter) {
  extendPush(router)
  extendGo(router)
  extendReplace(router)
  extendClose(router)
}
