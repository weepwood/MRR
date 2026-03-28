import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { clearSession, getSession, hasAnyPermission, isAdminUser } from '@/utils/session.js'

const Login = () => import('@/pages/LoginPage.vue')
const ElementImageGallery = () => import('@/components/ImageGalleryEl.vue')
const ElementImageGalleryBAH = () => import('@/components/ImageGalleryAdmin.vue')
const PrintPage = () => import('@/components/PrintPage.vue')
const AdminDashboard = () => import('@/pages/admin/AdminDashboardPage.vue')
const UsersPage = () => import('@/pages/admin/UsersPage.vue')
const PermissionsPage = () => import('@/pages/admin/PermissionsPage.vue')
const TestingPage = () => import('@/pages/admin/TestingPage.vue')
const LogsView = () => import('@/pages/admin/LogsView.vue')
const MonitoringView = () => import('@/pages/admin/MonitoringView.vue')
const SettingsView = () => import('@/pages/admin/SettingsView.vue')
const CrudView = () => import('@/pages/admin/RecordsPage.vue')
const RecordsStatisticsView = () => import('@/pages/admin/StatisticsPage.vue')
const StatisticsDetailPage = () => import('@/pages/admin/StatisticsDetailPage.vue')
const ArchiveImagePage = () => import('@/pages/admin/ArchiveImagePage.vue')
const Test = () => import('@/components/Test.vue')

const routes = [
  { path: '/', name: 'home', component: Login },
  { path: '/login', name: 'login', component: Login },
  {
    path: '/docs/:pathMatch(.*)*',
    name: 'docs-redirect',
    beforeEnter: () => {
      window.location.href = '/docs/index.html'
      return false
    }
  },
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
