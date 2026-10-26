import type { RouteRecordRaw } from 'vue-router'
import type { Route } from '#/global'
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
    icon: 'i-lucide:folder-open',
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
    icon: 'i-lucide:activity',
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
    icon: 'i-lucide:key-round',
    auth: ['user:manage'],
    cache: false,
  },
}

const nginxBrowserRoute: RouteRecordRaw = {
  path: '/nginx-browser',
  name: 'nginxBrowser',
  component: () => import('@/views/nginx-browser/index.vue'),
  meta: {
    title: 'Nginx 文件浏览',
    icon: 'i-lucide:folder-open',
    auth: ['record:read'],
    cache: true,
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
          icon: 'i-lucide:house',
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
    meta: { title: '系统', icon: 'i-lucide:settings' },
    children: [
      {
        path: '/users',
        component: () => import('@/views/users/index.vue'),
        meta: {
          title: '用户管理',
          icon: 'i-lucide:user-round',
          auth: ['user:manage'],
          cache: true,
        },
      },
      {
        path: '/permissions',
        component: () => import('@/views/permissions/index.vue'),
        meta: {
          title: '权限管理',
          icon: 'i-lucide:shield-check',
          auth: ['role:read'],
          cache: true,
        },
      },
      {
        path: '/settings',
        component: () => import('@/views/settings/index.vue'),
        meta: {
          title: '系统设置',
          icon: 'i-lucide:sliders-horizontal',
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
    meta: { title: '业务', icon: 'i-lucide:layout-grid' },
    children: [
      {
        path: '/records',
        component: () => import('@/views/records/index.vue'),
        meta: { title: '记录管理', icon: 'i-lucide:files', auth: ['record:read'], cache: true },
      },
      {
        path: '/patients',
        component: () => import('@/views/patients/index.vue'),
        meta: { title: '患者管理', icon: 'i-lucide:users-round', auth: ['record:read'], cache: true },
      },
      {
        path: '/ocr-batches',
        component: () => import('@/views/ocr-batches/index.vue'),
        meta: { title: '历史影像识别', icon: 'i-lucide:scan-text', auth: ['ocr:batch-run'] },
      },
      {
        path: '/statistics',
        component: () => import('@/views/statistics/index.vue'),
        meta: { title: '病案扫描统计', icon: 'i-lucide:chart-column', auth: ['statistics:read'], cache: true },
      },
      {
        path: '/statistics-detail',
        component: () => import('@/views/statistics-detail/index.vue'),
        meta: { title: '统计明细', icon: 'i-lucide:table-2', auth: ['statistics:read'], cache: true },
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
        meta: { title: '档案装箱', icon: 'i-lucide:archive', auth: ['record:read'], cache: true },
      },
      {
        path: '/oss-migration',
        component: () => import('@/views/oss-migration/index.vue'),
        meta: { title: 'OSS 迁移管理', icon: 'i-lucide:cloud-upload', auth: ['record:read'], cache: true },
      },
      {
        path: '/oss-browser',
        component: () => import('@/views/oss-browser/index.vue'),
        meta: { title: 'OSS 文件浏览', icon: 'i-lucide:folder-open', auth: ['record:read'], cache: true },
      },
      nginxBrowserRoute,
      archiveEmbeddedRoute,
    ],
  },
  {
    meta: { title: '数据治理', icon: 'i-lucide:network' },
    children: [
      {
        path: '/data-exchange',
        component: () => import('@/views/data-exchange/index.vue'),
        meta: { title: '数据导入导出', icon: 'i-lucide:arrow-left-right', auth: ['record:read', 'statistics:read'], cache: true },
      },
      {
        path: '/data-relations',
        component: () => import('@/views/data-relations/index.vue'),
        meta: {
          title: '数据关系工作台',
          icon: 'i-lucide:workflow',
          auth: ['system:read'],
          cache: true,
        },
      },
    ],
  },
  {
    meta: { title: '运维', icon: 'i-lucide:wrench' },
    children: [
      {
        path: '/operations-center',
        component: () => import('@/views/operations-center/index.vue'),
        meta: {
          title: '运维诊断中心',
          icon: 'i-lucide:clipboard-check',
          auth: ['system:read'],
          cache: true,
        },
      },
      {
        path: '/logs',
        component: () => import('@/views/logs/index.vue'),
        meta: { title: '日志管理', icon: 'i-lucide:file-search', auth: ['log:read'], cache: true },
      },
      {
        path: '/runtime-errors',
        component: () => import('@/views/runtime-errors/index.vue'),
        meta: {
          title: '运行错误中心',
          icon: 'i-lucide:bug',
          auth: ['system:error:read'],
          cache: true,
        },
      },
      {
        path: '/runtime-logs',
        component: () => import('@/views/runtime-logs/index.vue'),
        meta: { title: '运行日志', icon: 'i-lucide:terminal', auth: ['system:manage'], cache: true },
      },
      {
        path: '/audit-images',
        component: () => import('@/views/audit-images/index.vue'),
        meta: { title: '病案图片访问审计', icon: 'i-lucide:eye', auth: ['log:read'], cache: true },
      },
      {
        path: '/monitoring',
        component: () => import('@/views/monitoring-dashboard/index.vue'),
        meta: { title: '系统监控', icon: 'i-lucide:activity', auth: ['system:read'], cache: true },
      },
      {
        path: '/response-analysis',
        component: () => import('@/views/response-analysis/index.vue'),
        meta: {
          title: '接口响应分析',
          icon: 'i-lucide:gauge',
          auth: ['system:read'],
          cache: true,
        },
      },
      authenticationTestRoute,
    ],
  },
  {
    meta: { title: '帮助', icon: 'i-lucide:circle-help' },
    children: [
      {
        path: '/help',
        component: () => import('@/views/help/index.vue'),
        meta: { title: '帮助与文档', icon: 'i-lucide:book-open', cache: true },
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
    '/nginx-browser',
    '/operations-center',
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
  nginxBrowserRoute,
]

export {
  asyncRoutes,
  asyncRoutesByFilesystem,
  constantRoutes,
  constantRoutesByFilesystem,
  systemRoutes,
}
