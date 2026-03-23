import { createRouter, createWebHistory } from 'vue-router'
import ElementImageGallery from '../components/ImageGalleryEl.vue'
import ElementImageGallery3 from '../components/ImageGalleryEl-3.vue'
import ElementImageGalleryBAH from '../components/ImageGalleryAdmin.vue'
import PrintPage from '../components/PrintPage.vue'
import Login from '../components/Login.vue'
import Admin from '../components/Admin.vue'
import AdminDashboard from '../components/AdminDashboard.vue'
import RecordsStatisticsView from '../components/admin/RecordsStatisticsView.vue'
import Test from '../components/Test.vue'
import IDTest from '../components/IdCardTest.vue'
import { ElMessage } from 'element-plus'

const routes = [
  { path: '/', name: 'home', component: Login },
  { path: '/login', name: 'login', component: Login },
  // 需要管理员权限的路由
  { path: '/admin', name: 'admin', component: Admin, meta: { requiresAdmin: true } },
  { path: '/admin-dashboard', name: 'admin-dashboard', component: AdminDashboard, meta: { requiresAdmin: true } },
  { path: '/admin/statistics', name: 'admin-statistics', component: RecordsStatisticsView, meta: { requiresAdmin: true } },
  { path: '/test', name: 'test', component: Test },
  { path: '/idtest', name: 'idtest', component: IDTest },
  // 打印页面
  { path: '/print', name: 'print', component: PrintPage },
  // 搜索路由，处理加密的身份证号
  { 
    path: '/search/:encryptedIdCard', 
    name: 'search', 
    component: ElementImageGallery3, 
    props: (route) => ({
      encryptedIdCard: route.params.encryptedIdCard,
      userid: route.query.userid,
      iv: route.query.iv,
      timestamp: route.query.timestamp
    })
  },
  // 动态身份证号路由，传入到组件作为 props
  { path: '/:idCard', name: 'galleryByIdCard', component: ElementImageGallery, props: true },
  
  { path: '/admin/:idCard/:bah', name: 'galleryByBah', component: ElementImageGalleryBAH, props: true, meta: { requiresAdmin: true } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 添加路由守卫
router.beforeEach((to, from, next) => {
  // 检查路由是否需要管理员权限
  if (to.meta.requiresAdmin) {

    // 例如检查用户是否已登录
    const isLoggedIn = localStorage.getItem('token')

    if (isLoggedIn) {
      // 用户已登录且具有管理员权限，允许访问
      next()
    } else {
      // 用户没有权限，重定向到登录页
      ElMessage.error('请先登录')
      next('/login')
    }
  } else {
    // 不需要特殊权限的路由，直接允许访问
    next()
  }
})

export default router
