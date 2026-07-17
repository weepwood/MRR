import { loadingFadeOut } from 'virtual:app-loading'
import { createRouter, createWebHistory } from 'vue-router'
import pinia from '@/store'
import setupExtensions from './extensions'
import setupGuards from './guards'
// 路由相关数据
import { constantRoutes, constantRoutesByFilesystem, systemRoutes } from './routes'

const router = createRouter({
  history: createWebHistory(),
  routes: useSettingsStore(pinia).settings.app.routeBaseOn === 'filesystem'
    ? constantRoutesByFilesystem
    : [...constantRoutes, ...systemRoutes],
})

setupGuards(router)
setupExtensions(router)

router.isReady()
  .then(() => {
    loadingFadeOut()
    window.dispatchEvent(new Event('mrr:app-ready'))
  })
  .catch(() => {
    window.dispatchEvent(new Event('mrr:app-startup-failed'))
  })

export default router
