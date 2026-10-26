import type { RouteRecordRaw } from 'vue-router'
import { createRouter, createWebHistory } from 'vue-router'
import pinia from '@/store'
import setupExtensions from './extensions'
import setupGuards from './guards'
import { constantRoutes, constantRoutesByFilesystem, systemRoutes } from './routes'

const passwordChangeRequiredRoute: RouteRecordRaw = {
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

function removeAppLoading() {
  const loadingEl = document.querySelector<HTMLElement>('[data-app-loading]')
  if (!loadingEl) {
    return
  }

  loadingEl.style.pointerEvents = 'none'
  loadingEl.style.transition = 'opacity 120ms ease-out'
  void getComputedStyle(loadingEl).opacity
  loadingEl.style.opacity = '0'
  window.setTimeout(() => loadingEl.remove(), 160)
}

router.isReady()
  .then(() => {
    removeAppLoading()
    window.dispatchEvent(new Event('mrr:app-ready'))
  })
  .catch(() => {
    removeAppLoading()
    window.dispatchEvent(new Event('mrr:app-startup-failed'))
  })

export default router
