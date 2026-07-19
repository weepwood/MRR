import { loadingFadeOut } from 'virtual:app-loading'
import { createRouter, createWebHistory } from 'vue-router'
import pinia from '@/store'
import setupExtensions from './extensions'
import setupGuards from './guards'
import { constantRoutes, constantRoutesByFilesystem, systemRoutes } from './routes'

const passwordChangeRequiredRoute = {
  path: '/password/change-required',
  name: 'passwordChangeRequired',
  component: () => import('@/views/password/change-required.vue'),
  meta: {
    title: '修改初始密码',
  },
}

const settingsStore = useSettingsStore(pinia)
const router = createRouter({
  history: createWebHistory(),
  routes: settingsStore.settings.app.routeBaseOn === 'filesystem'
    ? constantRoutesByFilesystem
    : [...constantRoutes, passwordChangeRequiredRoute, ...systemRoutes],
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
