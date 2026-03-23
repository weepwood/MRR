<template>
  <div class="admin-dashboard">
    <!-- 顶部导航栏 -->
    <div class="admin-header">
      <div class="header-left">
        <h1>病案翻拍管理</h1>
        <div class="header-info">
          <span class="welcome-text">欢迎回来，管理员</span>
          <span class="version-text">v2.0</span>
        </div>
      </div>
      <div class="header-stats">
        <div class="header-stat-card">
          <span class="stat-label">总用户</span>
          <span class="stat-value">{{ stats.totalUsers }}</span>
        </div>
        <div class="header-stat-card">
          <span class="stat-label">总病案</span>
          <span class="stat-value">{{ stats.totalRecords }}</span>
        </div>
      </div>
      <div class="header-right">
        <el-button class="logout-btn" type="primary" @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
          退出登录
        </el-button>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="admin-content">
      <!-- 侧边栏 -->
      <div class="admin-sidebar">
        <el-menu
          :default-active="activeMenu"
          class="admin-menu"
          @select="handleMenuSelect"
        >
          <el-menu-item index="dashboard">
            <el-icon><DataBoard /></el-icon>
            <span>仪表板</span>
          </el-menu-item>
          <el-menu-item index="users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="records">
            <el-icon><Document /></el-icon>
            <span>病案管理</span>
          </el-menu-item>
          <el-menu-item index="testing">
            <el-icon><Tools /></el-icon>
            <span>系统测试</span>
          </el-menu-item>
          <el-menu-item index="logs">
            <el-icon><DocumentCopy /></el-icon>
            <span>系统日志</span>
          </el-menu-item>
          <el-menu-item index="monitoring">
            <el-icon><Monitor /></el-icon>
            <span>系统监控</span>
          </el-menu-item>
          <el-menu-item index="settings">
            <el-icon><Setting /></el-icon>
            <span>系统设置</span>
          </el-menu-item>
        </el-menu>
      </div>

      <!-- 内容区域 -->
      <div class="admin-main">
        <!-- 仪表板 -->
        <DashboardView v-if="activeMenu === 'dashboard'" :stats="stats" :recent-activities="recentActivities" :system-status="systemStatus" />
        
        <!-- 用户管理 -->
        <UsersView v-if="activeMenu === 'users'" :users="users" @edit-user="editUser" @delete-user="deleteUser" @add-user="addUser" />
        
        <!-- 病案管理 -->
        <RecordsView v-if="activeMenu === 'records'" :records="records" :search-keyword="searchKeyword" @search="searchRecords" @view-record="viewRecord" @edit-record="editRecord" />
        
        <!-- 系统测试 -->
        <TestingView v-if="activeMenu === 'testing'" />
        
        <!-- 系统日志 -->
        <LogsView v-if="activeMenu === 'logs'" />
        
        <!-- 系统监控 -->
        <MonitoringView v-if="activeMenu === 'monitoring'" :system-status="systemStatus" :browser-info="browserInfo" :ip-info="ipInfo" :local-ips="localIps" />
        
        <!-- 系统设置 -->
        <SettingsView v-if="activeMenu === 'settings'" :settings="settings" @save="saveSettings" @reset="resetSettings" />

        <section v-if="activeMenu === 'dashboard'" class="docs-generator-section">
          <div class="docs-header">
            <h3>说明文档静态网站生成器推荐</h3>
            <p>可将 Markdown 说明文档构建为静态站点，推荐优先考虑与 Vue 生态契合的方案。</p>
          </div>
          <div class="docs-grid">
            <article
              v-for="item in docsGenerators"
              :key="item.name"
              class="docs-card"
            >
              <div class="docs-card-head">
                <h4>{{ item.name }}</h4>
                <span class="docs-tag">{{ item.tag }}</span>
              </div>
              <p class="docs-desc">{{ item.description }}</p>
              <div class="docs-meta">{{ item.note }}</div>
              <el-button class="docs-btn" type="primary" plain @click="openExternal(item.url)">
                <el-icon><Link /></el-icon>
                查看官方文档
              </el-button>
            </article>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { 
  SwitchButton, DataBoard, User, Document, Tools, 
  DocumentCopy, Monitor, Setting, Link
} from '@element-plus/icons-vue'
import logger from '../utils/logger.js'

// 导入子组件
import DashboardView from './admin/DashboardView.vue'
import UsersView from './admin/UsersView.vue'
import RecordsView from './admin/RecordsView.vue'
import TestingView from './admin/TestingView.vue'
import LogsView from './admin/LogsView.vue'
import MonitoringView from './admin/MonitoringView.vue'
import SettingsView from './admin/SettingsView.vue'

const router = useRouter()

// 响应式数据
const activeMenu = ref('dashboard')
const searchKeyword = ref('')
const logLevel = ref('')

// 统计数据
const stats = reactive({
  totalUsers: 3,
  totalRecords: 3,
  todayRecords: 1,
  onlineUsers: 2,
})

// 系统状态
const systemStatus = reactive({
  cpu: 45,
  memory: 62,
  disk: 38
})

// 浏览器信息
const browserInfo = reactive({
  userAgent: '',
  language: '',
  platform: '',
  timezone: '',
  viewport: ''
})

// IP信息
const ipInfo = ref(null)
const localIps = ref([])
const docsGenerators = [
  {
    name: 'VitePress（已集成）',
    tag: '项目文档站',
    description: '项目已接入 VitePress，可将说明文档构建为静态网站并独立部署。',
    note: '默认入口为 /docs/index.html，本地可通过 npm run docs:dev 预览。',
    url: '/docs/index.html'
  },
  {
    name: 'Docusaurus',
    tag: '成熟社区',
    description: '文档、博客、版本化能力完整，生态成熟，适合中大型文档站点或多版本管理。',
    note: '基于 React 体系，若团队有跨栈经验会更容易发挥优势。',
    url: 'https://docusaurus.io/'
  },
  {
    name: 'Astro Starlight',
    tag: '内容体验',
    description: '基于 Astro 的文档主题，默认样式美观，文档可读性与性能表现都很好。',
    note: '适合追求站点展示效果和内容组织体验的场景。',
    url: 'https://starlight.astro.build/'
  }
]

// 最近活动
const recentActivities = ref([
  { id: 1, text: '用户xxx登录系统', time: '2分钟前' },
  { id: 2, text: '新增病案记录 #00788222', time: '5分钟前' },
  { id: 3, text: '系统备份完成', time: '10分钟前' },
  { id: 4, text: '用户xxx修改个人信息', time: '15分钟前' },
  { id: 5, text: '导出病案数据', time: '20分钟前' }
])

// 用户数据
const users = ref([
  { id: 1, username: 'admin', email: 'admin@example.com', role: '管理员', status: 'active', lastLogin: '2024-01-15 10:30' },
  { id: 2, username: 'doctor1', email: 'doctor1@example.com', role: '用户', status: 'active', lastLogin: '2024-01-15 09:15' },
  { id: 3, username: 'nurse1', email: 'nurse1@example.com', role: '用户', status: 'active', lastLogin: '2024-01-15 08:45' }
])

// 病案数据
const records = ref([
  { bah: '00788222', patientName: '张三', department: '内科', admissionDate: '2024-01-10', imageCount: 15, status: 'completed' },
  { bah: '00788223', patientName: '李四', department: '外科', admissionDate: '2024-01-11', imageCount: 8, status: 'processing' },
  { bah: '00788224', patientName: '王五', department: '儿科', admissionDate: '2024-01-12', imageCount: 12, status: 'completed' }
])

// 日志数据
const logs = ref([])

// 系统设置
const settings = reactive({
  systemName: '病案管理系统',
  maxFileSize: 10,
  sessionTimeout: 30,
  logLevel: 'info'
})

// 方法
const handleMenuSelect = (index) => {
  activeMenu.value = index
}

const openExternal = (url) => {
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

const handleLogout = () => {
  ElMessageBox.confirm(
    '确定要退出登录吗？',
    '确认退出',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
    .then(() => {
      localStorage.removeItem('token')
      router.push('/login')
      ElMessage.success('已退出登录')
    })
    .catch(() => {})
}

const editUser = (user) => {
  logger.info('编辑用户', `管理员编辑用户: ${user.username}`)
  ElMessage.info(`编辑用户: ${user.username}`)
}

const deleteUser = (user) => {
  ElMessageBox.confirm(
    `确定要删除用户 ${user.username} 吗？`,
    '确认删除',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(() => {
    logger.warn('删除用户', `管理员删除用户: ${user.username}`)
    ElMessage.success('用户删除成功')
  })
}

const addUser = () => {
  logger.info('添加用户', '管理员尝试添加新用户')
  ElMessage.info('添加用户功能')
}

const searchRecords = () => {
  logger.info('搜索病案', `搜索关键词: ${searchKeyword.value}`)
  ElMessage.info(`搜索: ${searchKeyword.value}`)
}

const viewRecord = (record) => {
  logger.info('查看病案', `查看病案: ${record.bah}`)
  ElMessage.info(`查看病案: ${record.bah}`)
}

const editRecord = (record) => {
  logger.info('编辑病案', `编辑病案: ${record.bah}`)
  ElMessage.info(`编辑病案: ${record.bah}`)
}

const refreshLogs = () => {
  logs.value = logger.getLogs()
  ElMessage.success('日志已刷新')
}

const clearLogs = () => {
  ElMessageBox.confirm(
    '确定要清空所有日志吗？',
    '确认清空',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(() => {
    logger.clearLogs()
    logs.value = []
    ElMessage.success('日志已清空')
  })
}

const filterLogs = (level) => {
  logLevel.value = level
}

const saveSettings = () => {
  logger.info('保存设置', '管理员保存系统设置')
  ElMessage.success('设置已保存')
}

const resetSettings = () => {
  logger.warn('重置设置', '管理员重置系统设置')
  ElMessage.info('设置已重置')
}

// 加载浏览器信息
const loadBrowserInfo = () => {
  try {
    browserInfo.userAgent = navigator.userAgent
    browserInfo.language = navigator.language
    browserInfo.platform = navigator.platform
    browserInfo.timezone = Intl.DateTimeFormat().resolvedOptions().timeZone
    browserInfo.viewport = `${window.innerWidth} x ${window.innerHeight}`
  } catch (e) {
    console.error('获取浏览器信息失败:', e)
  }
}

// 加载IP信息
const loadIpInfo = async () => {
  try {
    const res = await fetch('https://ipapi.co/json/')
    if (res.ok) {
      ipInfo.value = await res.json()
    }
  } catch (e) {
    console.error('获取IP信息失败:', e)
  }
}

// 获取本地IP
const getLocalIps = () => {
  try {
    const RTCPeerConnection = window.RTCPeerConnection || window.webkitRTCPeerConnection || window.mozRTCPeerConnection
    if (RTCPeerConnection) {
      const pc = new RTCPeerConnection({ iceServers: [] })
      pc.createDataChannel('')
      pc.onicecandidate = (e) => {
        if (!e || !e.candidate || !e.candidate.candidate) return
        const m = /([0-9]{1,3}(?:\.[0-9]{1,3}){3})/.exec(e.candidate.candidate)
        if (m && m[1] && !localIps.value.includes(m[1])) {
          localIps.value.push(m[1])
        }
      }
      pc.createOffer().then((sdp) => pc.setLocalDescription(sdp)).catch(() => {})
      setTimeout(() => {
        try { pc.close() } catch (e) {}
      }, 3000)
    }
  } catch (e) {
    console.error('获取本地IP失败:', e)
  }
}

onMounted(() => {
  loadBrowserInfo()
  loadIpInfo()
  getLocalIps()
  
  // 初始化日志
  logs.value = logger.getLogs()
  
  // 监听日志更新事件
  window.addEventListener('logUpdate', (event) => {
    logs.value = event.detail.logs
  })
  
  // 记录系统启动日志
  logger.info('管理面板启动', '用户进入后台管理界面')
  
  // 模拟实时更新系统状态
  setInterval(() => {
    systemStatus.cpu = Math.floor(Math.random() * 100)
    systemStatus.memory = Math.floor(Math.random() * 100)
    systemStatus.disk = Math.floor(Math.random() * 100)
    
    // 记录系统状态变化
    if (systemStatus.cpu > 80) {
      logger.warn('CPU使用率过高', `当前CPU使用率: ${systemStatus.cpu}%`)
    }
    if (systemStatus.memory > 85) {
      logger.warn('内存使用率过高', `当前内存使用率: ${systemStatus.memory}%`)
    }
  }, 5000)
})
</script>

<style scoped>
.admin-dashboard {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(160deg, #f2f7ff 0%, #f8fbff 42%, #eef5ff 100%);
}

.admin-header {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  padding: 0 20px;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  box-shadow: 0 8px 24px rgba(44, 98, 185, 0.1);
  border-bottom: 1px solid rgba(64, 158, 255, 0.12);
  z-index: 1000;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.header-left h1 {
  margin: 0;
  font-size: 22px;
  letter-spacing: 0.4px;
  color: #1d2b42;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.welcome-text {
  color: #5c6e8a;
  font-size: 13px;
}

.version-text {
  color: #2e81ff;
  font-size: 12px;
  background: #ebf3ff;
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid #cbe0ff;
}

.header-stats {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-stat-card {
  min-width: 92px;
  padding: 8px 12px;
  border-radius: 10px;
  background: linear-gradient(135deg, #f6faff 0%, #eef5ff 100%);
  border: 1px solid #d8e8ff;
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.stat-label {
  color: #5f7290;
  font-size: 12px;
}

.stat-value {
  margin-top: 2px;
  color: #285ca8;
  font-size: 18px;
  font-weight: 700;
}

.logout-btn {
  border-radius: 10px;
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.26);
}

.admin-content {
  flex: 1;
  display: flex;
  overflow: hidden;
  padding: 16px;
  gap: 16px;
  min-height: 0;
}

.admin-sidebar {
  width: 228px;
  background: #ffffff;
  border-radius: 14px;
  border: 1px solid #e2ebf7;
  box-shadow: 0 8px 24px rgba(27, 72, 145, 0.08);
  overflow: hidden;
}

.admin-menu {
  border-right: none;
  padding: 8px;
}

.admin-menu :deep(.el-menu-item) {
  margin: 6px 0;
  border-radius: 10px;
  transition: all 0.22s ease;
}

.admin-menu :deep(.el-menu-item:hover) {
  background: #f2f7ff;
}

.admin-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, #e9f2ff 0%, #dceaff 100%);
  color: #2467c6;
  font-weight: 600;
}

.admin-main {
  flex: 1;
  padding: 22px;
  background: rgba(255, 255, 255, 0.88);
  border-radius: 14px;
  border: 1px solid #e2ebf7;
  box-shadow: 0 8px 24px rgba(27, 72, 145, 0.08);
  overflow-y: auto;
}

.docs-generator-section {
  margin-top: 24px;
  border-top: 1px solid #e8eef8;
  padding-top: 20px;
}

.docs-header h3 {
  margin: 0;
  font-size: 18px;
  color: #1f3f75;
}

.docs-header p {
  margin: 8px 0 0;
  color: #5f7290;
  font-size: 13px;
}

.docs-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.docs-card {
  border: 1px solid #dce8fb;
  border-radius: 12px;
  padding: 14px;
  background: linear-gradient(180deg, #fbfdff 0%, #f4f8ff 100%);
}

.docs-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.docs-card-head h4 {
  margin: 0;
  font-size: 15px;
  color: #1d2b42;
}

.docs-tag {
  font-size: 12px;
  color: #2d65b7;
  background: #e9f2ff;
  border: 1px solid #cfe1ff;
  border-radius: 999px;
  padding: 2px 8px;
}

.docs-desc {
  margin: 10px 0 0;
  color: #485f80;
  font-size: 13px;
  line-height: 1.5;
}

.docs-meta {
  margin-top: 8px;
  font-size: 12px;
  color: #7388a8;
}

.docs-btn {
  margin-top: 12px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .admin-content {
    padding: 10px;
    gap: 10px;
  }

  .admin-sidebar {
    width: 60px;
  }
  
  .admin-menu .el-menu-item span {
    display: none;
  }
  
  .admin-header {
    padding: 0 10px;
    height: 64px;
  }

  .header-stats {
    display: none;
  }
  
  .header-left h1 {
    font-size: 16px;
  }

  .docs-generator-section {
    margin-top: 16px;
    padding-top: 16px;
  }
}
</style>
