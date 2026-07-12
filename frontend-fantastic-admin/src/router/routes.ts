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
    path: '/idcard',
    name: 'idcardSearch',
    component: () => import('@/views/idcard/index.vue'),
    meta: {
      title: '病案查询',
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
      title: '系统',
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
          auth: ['role:read'],
        },
      },
      {
        path: '/settings',
        component: () => import('@/views/settings/index.vue'),
        meta: {
          title: '系统设置',
          icon: 'i-ant-design:tool-twotone',
          auth: ['system:read'],
        },
      },
    ],
  },
  {
    meta: {
      title: '业务',
      icon: 'i-ant-design:appstore-twotone',
    },
    children: [
      {
        path: '/records',
        component: () => import('@/views/records/index.vue'),
        meta: {
          title: '记录管理',
          icon: 'i-ant-design:database-twotone',
          auth: ['record:read'],
        },
      },
      {
        path: '/patients',
        component: () => import('@/views/patients/index.vue'),
        meta: {
          title: '患者管理',
          icon: 'i-ant-design:team-outlined',
          auth: ['record:read'],
        },
      },
      {
        path: '/statistics',
        component: () => import('@/views/statistics/index.vue'),
        meta: {
          title: '统计分析',
          icon: 'i-ant-design:area-chart-outlined',
          auth: ['statistics:read'],
        },
      },
      {
        path: '/statistics-detail',
        component: () => import('@/views/statistics-detail/index.vue'),
        meta: {
          title: '统计明细',
          icon: 'i-ant-design:profile-twotone',
          auth: ['statistics:read'],
        },
      },
      {
        path: '/records-statistics',
        component: () => import('@/views/records-statistics/index.vue'),
        meta: {
          title: '病案统计',
          icon: 'i-ant-design:bar-chart-outlined',
          auth: ['statistics:read'],
        },
      },
      {
        path: '/oss-migration',
        component: () => import('@/views/oss-migration/index.vue'),
        meta: {
          title: 'OSS 迁移管理',
          icon: 'i-ant-design:cloud-upload-outlined',
          auth: ['record:read'],
        },
      },
      {
        path: '/archive/:bah?',
        component: () => import('@/views/statistics/archive.vue'),
        meta: {
          title: '影像档案袋',
          icon: 'i-ant-design:folder-open-twotone',
          auth: ['record:read'],
        },
      },
      {
        path: '/archive-search/:keyword?',
        component: () => import('@/views/archive-search/index.vue'),
        meta: {
          title: '档案搜索',
          icon: 'i-ant-design:search-outlined',
          auth: ['record:read'],
        },
      },
    ],
  },
  {
    meta: {
      title: '运维',
      icon: 'i-ant-design:control-twotone',
    },
    children: [
      {
        path: '/logs',
        component: () => import('@/views/logs/index.vue'),
        meta: {
          title: '日志管理',
          icon: 'i-ant-design:file-search-outlined',
          auth: ['log:read'],
        },
      },
      {
        path: '/audit-images',
        component: () => import('@/views/audit-images/index.vue'),
        meta: {
          title: '病案图片访问审计',
          icon: 'i-ant-design:security-scan-outlined',
          auth: ['log:read'],
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
        path: '/response-analysis',
        component: () => import('@/views/response-analysis/index.vue'),
        meta: {
          title: '接口响应分析',
          icon: 'i-ant-design:fund-projection-screen-outlined',
          auth: ['system:read'],
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
