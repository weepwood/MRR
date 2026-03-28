import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { clearSession, getSession, hasAnyPermission, isAdminUser } from '@/utils/session.js'

const Login = () => import('@/pages/LoginPage.vue')
const ElementImageGallery = () => import('@/components/ImageGalleryEl.vue')
const ElementImageGalleryBAH = () => import('@/components/ImageGalleryAdmin.vue')
const PrintPage = () => import('@/components/PrintPage.vue')
const Admin = () => import('@/pages/AdminEntryPage.vue')
const AdminDashboard = () => import('@/pages/admin/AdminDashboardPage.vue')
const UsersPage = () => import('@/pages/admin/UsersPage.vue')
const PermissionsPage = () => import('@/pages/admin/PermissionsPage.vue')
const TestingPage = () => import('@/pages/admin/TestingPage.vue')
const SettingsPage = () => import('@/pages/admin/SettingsPage.vue')
const CrudView = () => import('@/pages/admin/RecordsPage.vue')
const RecordsStatisticsView = () => import('@/pages/admin/StatisticsPage.vue')
const Test = () => import('@/components/Test.vue')
const IDTest = () => import('@/components/IdCardTest.vue')

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
  { path: '/admin', name: 'admin', component: Admin, meta: { requiresAdmin: true } },
  { path: '/admin-dashboard', name: 'admin-dashboard', component: AdminDashboard, meta: { requiresAdmin: true } },
  {
    path: '/admin/users',
    name: 'admin-users',
    component: UsersPage,
    meta: { requiresAdmin: true, requiredAnyPermissions: ['user:manage'] }
  },
  {
    path: '/admin/permissions',
    name: 'admin-permissions',
    component: PermissionsPage,
    meta: { requiresAdmin: true, requiredAnyPermissions: ['role:read', 'role:manage', 'user:manage'] }
  },
  {
    path: '/admin/testing',
    name: 'admin-testing',
    component: TestingPage,
    meta: { requiresAdmin: true, requiredAnyPermissions: ['system:read', 'log:read', 'role:manage', 'user:manage'] }
  },
  {
    path: '/admin/settings',
    name: 'admin-settings',
    component: SettingsPage,
    meta: { requiresAdmin: true, requiredAnyPermissions: ['system:read', 'role:manage', 'user:manage'] }
  },
  { path: '/admin/crud', name: 'admin-crud', component: CrudView, meta: { requiresAdmin: true } },
  { path: '/admin/statistics', name: 'admin-statistics', component: RecordsStatisticsView, meta: { requiresAdmin: true } },
  { path: '/test', name: 'test', component: Test },
  { path: '/idtest', name: 'idtest', component: IDTest },
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
    return isAdminUser() ? '/admin-dashboard' : '/print'
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
