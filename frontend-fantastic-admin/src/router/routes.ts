import type { Route } from '#/global'
import type { RouteRecordRaw } from 'vue-router'
import generatedRoutes from 'virtual:generated-pages'
import { setupLayouts } from 'virtual:meta-layouts'
import { useSettingsStore } from '@/store/modules/settings'

const publicStatusRoute: RouteRecordRaw = {
  path: '/status',
  name: 'publicStatus',
  component: () => import('@/views/status/index.vue'),
  meta: { title: '系统状态' },
}

const archiveStandaloneRoute: RouteRecordRaw = {
  path: '/archive',
  name: 'archive',
  component: () => import('@/views/statistics/archive.vue'),
  meta: { title: '影像档案袋', auth: ['record:read'], cache: false },
}

const externalArchiveRoute: RouteRecordRaw = {
  path: '/archive/external',
  name: 'externalArchive',
  component: () => import('@/views/statistics/external-archive.vue'),
  meta: { title: '外部影像档案袋', cache: false },
}

const archiveEmbeddedRoute: RouteRecordRaw = {
  path: '/archive/embed',
  name: 'archiveEmbedded',
  component: () => import('@/views/statistics/archive.vue'),
  meta: {
    title: '影像档案袋',
    icon: 'i-ant-design:folder-open-twotone',
    auth: ['record:read'],
    cache: false,
  },
}

const monitoringFilesystemRoute: RouteRecordRaw = {
  path: '/monitoring',
  name: 'monitoring',
  component: () => import('@/views/monitoring-dashboard/index.vue'),
  meta: {
    title: '系统监控',
    icon: 'i-ant-design:dashboard-twotone',
    auth: ['system:read'],
    cache: true,
  },
}

const authenticationTestRoute: RouteRecordRaw = {
  path: '/auth-test',
  name: 'authenticationApiTest',
  component: () => import('@/views/auth-test/index.vue'),
  meta: {
    title: '认证接口测试',
    icon: 'i-ant-design:api-twotone',
    auth: ['user:manage'],
    cache: false,
  },
}

const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/idcard',
    name: 'idcardSearch',
    component: () => import('@/views/idcard/index.vue'),
    meta: { title: '病案查询' },
  },
  {
    path: '/idcard/:idCard',
    name: 'scanImg',
    component: () => import('@/views/scan-img/index.vue'),
    meta: { title: '病案图像查询' },
  },
  publicStatusRoute,
  externalArchiveRoute,
  archiveStandaloneRoute,
  {
    path: '/:all(.*)*',
    name: 'notFound',
    component: () => import('@/views/[...all].vue'),
    meta: { title: '页面不存在' },
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
        meta: { title: '重新加载', breadcrumb: false },
      },
    ],
  },
]

const asyncRoutes: Route.recordMainRaw[] = [
  {
    meta: { title: '系统', icon: 'i-ant-design:setting-twotone' },
    children: [
      {
        path: '/users',
        component: () => import('@/views/users/index.vue'),
        meta: {
          title: '用户管理',
          icon: 'i-ant-design:user-outlined',
          auth: ['user:manage'],
          cache: true,
        },
      },
      {
        path: '/permissions',
        component: () => import('@/views/permissions/index.vue'),
        meta: {
          title: '权限管理',
          icon: 'i-ant-design:lock-twotone',
          auth: ['role:read'],
          cache: true,
        },
      },
      {
        path: '/settings',
        component: () => import('@/views/settings/index.vue'),
        meta: {
          title: '系统设置',
          icon: 'i-ant-design:tool-twotone',
          auth: ['system:read'],
          cache: true,
        },
      },
      {
        path: '/login-settings',
        redirect: '/settings?section=login-support',
        meta: {
          title: '登录页文案',
          auth: ['system:read'],
          menu: false,
          breadcrumb: false,
          activeMenu: '/settings',
          cache: false,
        },
      },
    ],
  },
  {
    meta: { title: '业务', icon: 'i-ant-design:appstore-twotone' },
    children: [
      {
        path: '/records',
        component: () => import('@/views/records/index.vue'),
        meta: { title: '记录管理', icon: 'i-ant-design:database-twotone', auth: ['record:read'], cache: true },
      },
      {
        path: '/patients',
        component: () => import('@/views/patients/index.vue'),
        meta: { title: '患者管理', icon: 'i-ant-design:team-outlined', auth: ['record:read'], cache: true },
      },
      {
        path: '/statistics',
        component: () => import('@/views/statistics/index.vue'),
        meta: { title: '病案扫描统计', icon: 'i-ant-design:area-chart-outlined', auth: ['statistics:read'], cache: true },
      },
      {
        path: '/statistics-detail',
        component: () => import('@/views/statistics-detail/index.vue'),
        meta: { title: '统计明细', icon: 'i-ant-design:profile-twotone', auth: ['statistics:read'], cache: true },
      },
      {
        path: '/records-statistics',
        redirect: '/statistics',
        meta: {
          title: '病案扫描统计',
          auth: ['statistics:read'],
          menu: false,
          breadcrumb: false,
          activeMenu: '/statistics',
          cache: false,
        },
      },
      {
        path: '/archive-boxes',
        component: () => import('@/views/archive-boxes/index.vue'),
        meta: { title: '档案装箱', icon: 'i-ant-design:inbox-outlined', auth: ['record:read'], cache: true },
      },
      {
        path: '/oss-migration',
        component: () => import('@/views/oss-migration/index.vue'),
        meta: { title: 'OSS 迁移管理', icon: 'i-ant-design:cloud-upload-outlined', auth: ['record:read'], cache: true },
      },
      {
        path: '/oss-browser',
        component: () => import('@/views/oss-browser/index.vue'),
        meta: { title: 'OSS 文件浏览', icon: 'i-ant-design:folder-open-twotone', auth: ['record:read'], cache: true },
      },
      archiveEmbeddedRoute,
    ],
  },
  {
    meta: { title: '数据治理', icon: 'i-ant-design:cluster-outlined' },
    children: [
      {
        path: '/data-relations',
        component: () => import('@/views/data-relations/index.vue'),
        meta: {
          title: '数据关系工作台',
          icon: 'i-ant-design:apartment-outlined',
          auth: ['system:read'],
          cache: true,
        },
      },
    ],
  },
  {
    meta: { title: '运维', icon: 'i-ant-design:control-twotone' },
    children: [
      {
        path: '/logs',
        component: () => import('@/views/logs/index.vue'),
        meta: { title: '日志管理', icon: 'i-ant-design:file-search-outlined', auth: ['log:read'], cache: true },
      },
      {
        path: '/audit-images',
        component: () => import('@/views/audit-images/index.vue'),
        meta: { title: '病案图片访问审计', icon: 'i-ant-design:security-scan-outlined', auth: ['log:read'], cache: true },
      },
      {
        path: '/monitoring',
        component: () => import('@/views/monitoring-dashboard/index.vue'),
        meta: { title: '系统监控', icon: 'i-ant-design:dashboard-twotone', auth: ['system:read'], cache: true },
      },
      {
        path: '/system-status',
        name: 'systemStatusMenu',
        redirect: '/status',
        meta: {
          title: '服务状态',
          icon: 'i-ant-design:check-circle-twotone',
          link: '/status',
          auth: ['system:read'],
          cache: false,
        },
      },
      {
        path: '/response-analysis',
        component: () => import('@/views/response-analysis/index.vue'),
        meta: {
          title: '接口响应分析',
          icon: 'i-ant-design:fund-projection-screen-outlined',
          auth: ['system:read'],
          cache: true,
        },
      },
      authenticationTestRoute,
    ],
  },
  {
    meta: { title: '帮助', icon: 'i-ant-design:question-circle-twotone' },
    children: [
      {
        path: '/help',
        component: () => import('@/views/help/index.vue'),
        meta: { title: '帮助与文档', icon: 'i-ant-design:read-outlined', cache: true },
      },
    ],
  },
]

const externalArchiveGeneratedPaths = [
  '/archive/external',
  '/statistics/external-archive',
  '/statistics/external-archive-viewer',
]

const generatedConstantRoutes = generatedRoutes.filter(
  item => !['/status', ...externalArchiveGeneratedPaths].includes(item.path)
    && item.meta?.enabled !== false
    && item.meta?.constant === true,
)
const constantRoutesByFilesystem = [publicStatusRoute, externalArchiveRoute, archiveStandaloneRoute, ...generatedConstantRoutes]

const generatedAsyncRoutes = generatedRoutes.filter(
  item => ![
    '/status',
    '/auth-test',
    '/data-relations',
    ...externalArchiveGeneratedPaths,
    '/statistics/archive',
    '/monitoring',
    '/monitoring-dashboard',
  ].includes(item.path)
    && item.meta?.enabled !== false
    && item.meta?.constant !== true
    && item.meta?.layout !== false,
)
const asyncRoutesByFilesystem = [
  ...setupLayouts(generatedAsyncRoutes),
  archiveEmbeddedRoute,
  monitoringFilesystemRoute,
  authenticationTestRoute,
]

export {
  asyncRoutes,
  asyncRoutesByFilesystem,
  constantRoutes,
  constantRoutesByFilesystem,
  systemRoutes,
}
