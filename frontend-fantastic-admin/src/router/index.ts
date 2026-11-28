import type { RouteRecordRaw } from 'vue-router'
import { createRouter, createWebHashHistory, createWebHistory } from 'vue-router'
import setupExtensions from './extensions'
import setupGuards from './guards'
import { constantRoutes, systemRoutes } from './routes'

const passwordChangeRequiredRoute: RouteRecordRaw = {
  path: '/password/change-required',
  name: 'passwordChangeRequired',
  component: () => import('@/views/password/change-required.vue'),
  meta: {
    title: '修改初始密码',
  },
}

const router = createRouter({
  // Pages 无服务端 SPA 回退，使用 hash 路由支持刷新和深链。
  history: import.meta.env.MODE === 'pages'
    ? createWebHashHistory(import.meta.env.BASE_URL)
    : createWebHistory(),
  routes: [...constantRoutes, passwordChangeRequiredRoute, ...systemRoutes],
})

setupGuards(router)
setupExtensions(router)

export default router
