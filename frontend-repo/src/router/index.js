import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { clearSession, getSession, hasAnyPermission, isAdminUser } from '@/utils/session.js'

const Login = () => import('@/pages/LoginPage.vue')
const ElementImageGallery = () => import('@/components/ImageGalleryEl.vue')
const ElementImageGalleryBAH = () => import('@/components/ImageGalleryAdmin.vue')
const PrintPage = () => import('@/components/PrintPage.vue')
const Admin = () => import('@/pages/AdminEntryPage.vue')
const AdminDashboard = () => import('@/pages/admin/AdminDashboardPage.vue')
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

  if (!isAdminUser() && !hasAnyPermission(['user:manage', 'role:manage'])) {
    ElMessage.error('Current account has no admin permission')
    return '/login'
  }

  return true
})

export default router
