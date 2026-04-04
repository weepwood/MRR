import type { Route } from '#/global'
import type { RouteRecordRaw } from 'vue-router'
import generatedRoutes from 'virtual:generated-pages'
import { setupLayouts } from 'virtual:meta-layouts'
import { useSettingsStore } from '@/store/modules/settings'

const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login.vue'),
    meta: {
      title: '登录',
    },
  },
  {
    path: '/idcard/:idCard',
    name: 'scanImg',
    component: () => import('@/views/scan-img/index.vue'),
    meta: {
      title: '病案图像查询',
    },
  },
  {
    path: '/:all(.*)*',
    name: 'notFound',
    component: () => import('@/views/[...all].vue'),
    meta: {
      title: '页面不存在',
    },
  },
]

const systemRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'layout',
    component: () => import('@/layouts/index.vue'),
    meta: {
      title: () => useSettingsStore().settings.home.title,
      breadcrumb: false,
    },
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/index.vue'),
        meta: {
          title: () => useSettingsStore().settings.home.title,
          icon: 'i-ant-design:home-twotone',
          breadcrumb: false,
        },
      },
      {
        path: 'reload',
        name: 'reload',
        component: () => import('@/views/reload.vue'),
        meta: {
          title: '重新加载',
          breadcrumb: false,
        },
      },
    ],
  },
]

const asyncRoutes: Route.recordMainRaw[] = [
  {
    meta: {
      title: '系统管理',
      icon: 'i-ant-design:setting-twotone',
    },
    children: [
      {
        path: '/users',
        component: () => import('@/views/users/index.vue'),
        meta: {
          title: '用户管理',
          icon: 'i-ant-design:user-outlined',
          auth: ['user:manage'],
        },
      },
      {
        path: '/permissions',
        component: () => import('@/views/permissions/index.vue'),
        meta: {
          title: '权限管理',
          icon: 'i-ant-design:lock-twotone',
          auth: ['role:read', 'role:manage', 'user:manage'],
        },
      },
      {
        path: '/settings',
        component: () => import('@/views/settings/index.vue'),
        meta: {
          title: '系统设置',
          icon: 'i-ant-design:tool-twotone',
          auth: ['system:read', 'role:manage', 'user:manage'],
        },
      },
    ],
  },
  {
    meta: {
      title: '业务管理',
      icon: 'i-ant-design:appstore-twotone',
    },
    children: [
      {
        path: '/records',
        component: () => import('@/views/records/index.vue'),
        meta: {
          title: '记录管理',
          icon: 'i-ant-design:database-twotone',
          auth: ['user:manage', 'role:manage', 'system:read'],
        },
      },
      {
        path: '/statistics',
        component: () => import('@/views/statistics/index.vue'),
        meta: {
          title: '统计分析',
          icon: 'i-ant-design:area-chart-outlined',
          auth: ['system:read', 'user:manage', 'role:manage'],
        },
      },
      {
        path: '/statistics-detail',
        component: () => import('@/views/statistics-detail/index.vue'),
        meta: {
          title: '统计明细',
          icon: 'i-ant-design:profile-twotone',
          auth: ['system:read', 'user:manage', 'role:manage'],
        },
      },
      {
        path: '/statistics/archive/:bah',
        component: () => import('@/views/statistics/archive.vue'),
        meta: {
          title: '归档图像',
          menu: false,
          auth: ['system:read', 'user:manage', 'role:manage'],
        },
      },
    ],
  },
  {
    meta: {
      title: '运维中心',
      icon: 'i-ant-design:control-twotone',
    },
    children: [
      {
        path: '/logs',
        component: () => import('@/views/logs/index.vue'),
        meta: {
          title: '日志管理',
          icon: 'i-ant-design:file-search-outlined',
          auth: ['log:read', 'system:read'],
        },
      },
      {
        path: '/monitoring',
        component: () => import('@/views/monitoring/index.vue'),
        meta: {
          title: '系统监控',
          icon: 'i-ant-design:dashboard-twotone',
          auth: ['system:read'],
        },
      },
      {
        path: '/testing',
        component: () => import('@/views/testing/index.vue'),
        meta: {
          title: '测试中心',
          icon: 'i-ant-design:experiment-twotone',
          auth: ['system:read', 'log:read', 'role:manage', 'user:manage'],
        },
      },
    ],
  },
]



const constantRoutesByFilesystem = generatedRoutes.filter(item => item.meta?.enabled !== false && item.meta?.constant === true)

const asyncRoutesByFilesystem = [
  ...setupLayouts(
    generatedRoutes.filter(item => item.meta?.enabled !== false && item.meta?.constant !== true && item.meta?.layout !== false),
  ),
]

export {
  asyncRoutes,
  asyncRoutesByFilesystem,
  constantRoutes,
  constantRoutesByFilesystem,
  systemRoutes,
}
