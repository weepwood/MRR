import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { clearSession, getSession, hasAnyPermission, isAdminUser } from '@/utils/session'

const Login = () => import('@/views/LoginPage.vue')
const ElementImageGallery = () => import('@/components/ImageGalleryEl.vue')
const ElementImageGalleryBAH = () => import('@/components/ImageGalleryAdmin.vue')
const PrintPage = () => import('@/components/PrintPage.vue')
const AdminDashboard = () => import('@/views/admin/AdminDashboardPage.vue')
const UsersPage = () => import('@/views/admin/UsersPage.vue')
const PermissionsPage = () => import('@/views/admin/PermissionsPage.vue')
const TestingPage = () => import('@/views/admin/TestingPage.vue')
const LogsView = () => import('@/views/admin/LogsView.vue')
const MonitoringView = () => import('@/views/admin/MonitoringView.vue')
const SettingsView = () => import('@/views/admin/SettingsView.vue')
const CrudView = () => import('@/views/admin/RecordsPage.vue')
const RecordsStatisticsView = () => import('@/views/admin/StatisticsPage.vue')
const StatisticsDetailPage = () => import('@/views/admin/StatisticsDetailPage.vue')
const ArchiveImagePage = () => import('@/views/admin/ArchiveImagePage.vue')
const Test = () => import('@/components/Test.vue')

const docsRedirectRoute = {
  path: '/docs/:pathMatch(.*)*',
  name: 'docs-redirect',
  beforeEnter: () => {
    window.location.href = '/docs/index.html'
    return false
  }
} as unknown as RouteRecordRaw

const routes: RouteRecordRaw[] = [
  { path: '/', name: 'home', component: Login },
  { path: '/login', name: 'login', component: Login },
  docsRedirectRoute,
  {
    path: '/admin',
    name: 'admin',
    component: AdminDashboard,
    meta: { requiresAdmin: true },
    children: [
      {
        path: 'users',
        name: 'admin-users',
        component: UsersPage,
        meta: { requiresAdmin: true, requiredAnyPermissions: ['user:manage'] }
      },
      {
        path: 'permissions',
        name: 'admin-permissions',
        component: PermissionsPage,
        meta: { requiresAdmin: true, requiredAnyPermissions: ['role:read', 'role:manage', 'user:manage'] }
      },
      {
        path: 'testing',
        name: 'admin-testing',
        component: TestingPage,
        meta: { requiresAdmin: true, requiredAnyPermissions: ['system:read', 'log:read', 'role:manage', 'user:manage'] }
      },
      {
        path: 'logs',
        name: 'admin-logs',
        component: LogsView,
        meta: { requiresAdmin: true, requiredAnyPermissions: ['log:read', 'system:read'] }
      },
      {
        path: 'monitoring',
        name: 'admin-monitoring',
        component: MonitoringView,
        meta: { requiresAdmin: true, requiredAnyPermissions: ['system:read'] }
      },
      {
        path: 'settings',
        name: 'admin-settings',
        component: SettingsView,
        meta: { requiresAdmin: true, requiredAnyPermissions: ['system:read', 'role:manage', 'user:manage'] }
      },
      { path: 'crud', name: 'admin-crud', component: CrudView, meta: { requiresAdmin: true } },
      { path: 'statistics', name: 'admin-statistics', component: RecordsStatisticsView, meta: { requiresAdmin: true } },
      { path: 'statistics/detail', name: 'admin-statistics-detail', component: StatisticsDetailPage, meta: { requiresAdmin: true } },
      {
        path: 'statistics/detail/:bah',
        name: 'admin-statistics-archive',
        component: ArchiveImagePage,
        props: true,
        meta: { requiresAdmin: true }
      }
    ]
  },
  { path: '/admin-dashboard', redirect: '/admin' },
  { path: '/test', name: 'test', component: Test },
  { path: '/idtest', redirect: '/admin/testing' },
  { path: '/print', name: 'print', component: PrintPage },
  { path: '/:idCard', name: 'galleryByIdCard', component: ElementImageGallery, props: true },
  { path: '/admin/:idCard/:bah', name: 'galleryByBah', component: ElementImageGalleryBAH, props: true, meta: { requiresAdmin: true } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const session = getSession()
  const isAuthenticated = Boolean(session?.token)

  if ((to.path === '/' || to.path === '/login') && isAuthenticated) {
    return isAdminUser() ? '/admin' : '/print'
  }

  if (!to.meta.requiresAdmin) {
    return true
  }

  if (!isAuthenticated) {
    clearSession()
    ElMessage.error('Please log in first')
    return '/login'
  }

  const requiredAnyPermissions = Array.isArray(to.meta.requiredAnyPermissions) ? to.meta.requiredAnyPermissions : []
  const permissionsToCheck = requiredAnyPermissions.length ? requiredAnyPermissions : ['user:manage', 'role:manage']

  if (!isAdminUser() && !hasAnyPermission(permissionsToCheck)) {
    ElMessage.error('Current account has no admin permission')
    return '/login'
  }

  return true
})

export default router
