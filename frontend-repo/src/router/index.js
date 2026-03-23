import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'

const Login = () => import('../components/Login.vue')
const ElementImageGallery = () => import('../components/ImageGalleryEl.vue')
const ElementImageGalleryBAH = () => import('../components/ImageGalleryAdmin.vue')
const PrintPage = () => import('../components/PrintPage.vue')
const Admin = () => import('../components/Admin.vue')
const AdminDashboard = () => import('../components/AdminDashboard.vue')
const RecordsStatisticsView = () => import('../components/admin/RecordsStatisticsView.vue')
const Test = () => import('../components/Test.vue')
const IDTest = () => import('../components/IdCardTest.vue')

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
  if (!to.meta.requiresAdmin) return true
  const isLoggedIn = localStorage.getItem('token')
  if (isLoggedIn) return true
  ElMessage.error('请先登录')
  return '/login'
})

export default router
