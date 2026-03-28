<template>
  <div class="admin-dashboard">
    <header class="admin-header">
      <div class="header-left">
        <h1>病案管理系统</h1>
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
    </header>

    <div class="admin-content">
      <aside class="admin-sidebar">
        <el-menu :default-active="activeMenu" class="admin-menu" @select="handleMenuSelect">
          <el-menu-item index="dashboard">
            <el-icon><DataBoard /></el-icon>
            <span>仪表盘</span>
          </el-menu-item>
          <el-menu-item index="users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="records">
            <el-icon><Document /></el-icon>
            <span>通用 CRUD</span>
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
      </aside>

      <main class="admin-main">
        <DashboardView v-if="activeMenu === 'dashboard'" :stats="stats" :recent-activities="recentActivities" :system-status="systemStatus" />
        <UsersView v-if="activeMenu === 'users'" :users="users" />
        <RecordsView v-if="activeMenu === 'records'" />
        <TestingView v-if="activeMenu === 'testing'" />
        <LogsView v-if="activeMenu === 'logs'" />
        <MonitoringView v-if="activeMenu === 'monitoring'" />
        <SettingsView v-if="activeMenu === 'settings'" :settings="settings" />

        <section v-if="activeMenu === 'dashboard'" class="docs-generator-section">
          <div class="docs-header">
            <h3>文档站点推荐</h3>
            <p>你可以将项目文档站、接口文档和部署说明统一聚合在这里。</p>
          </div>
          <div class="docs-grid">
            <article v-for="item in docsGenerators" :key="item.name" class="docs-card">
              <div class="docs-card-head">
                <h4>{{ item.name }}</h4>
                <span class="docs-tag">{{ item.tag }}</span>
              </div>
              <p class="docs-desc">{{ item.description }}</p>
              <div class="docs-meta">{{ item.note }}</div>
              <el-button class="docs-btn" type="primary" plain @click="openExternal(item.url)">
                <el-icon><Link /></el-icon>
                打开链接
              </el-button>
            </article>
          </div>
        </section>
      </main>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  DataBoard,
  Document,
  DocumentCopy,
  Link,
  Monitor,
  Setting,
  SwitchButton,
  Tools,
  User
} from '@element-plus/icons-vue'
import DashboardView from './admin/DashboardView.vue'
import UsersView from './admin/UsersView.vue'
import RecordsView from './admin/RecordsView.vue'
import TestingView from './admin/TestingView.vue'
import LogsView from './admin/LogsView.vue'
import MonitoringView from './admin/MonitoringView.vue'
import SettingsView from './admin/SettingsView.vue'

const router = useRouter()

const activeMenu = ref('dashboard')

const stats = reactive({
  totalUsers: 3,
  totalRecords: 3,
  todayRecords: 1,
  onlineUsers: 2
})

const systemStatus = reactive({
  cpu: 45,
  memory: 62,
  disk: 38
})

const recentActivities = ref([
  { id: 1, text: '管理员登录系统', time: '2分钟前' },
  { id: 2, text: '新增病案记录 #00788222', time: '5分钟前' },
  { id: 3, text: '系统备份完成', time: '10分钟前' },
  { id: 4, text: '管理员修改个人信息', time: '15分钟前' },
  { id: 5, text: '导出病案数据', time: '20分钟前' }
])

const users = ref([
  { id: 1, username: 'admin', email: 'admin@example.com', role: '管理员', status: 'active', lastLogin: '2024-01-15 10:30' },
  { id: 2, username: 'doctor1', email: 'doctor1@example.com', role: '用户', status: 'active', lastLogin: '2024-01-15 09:15' },
  { id: 3, username: 'nurse1', email: 'nurse1@example.com', role: '用户', status: 'active', lastLogin: '2024-01-15 08:45' }
])

const settings = reactive({
  systemName: '病案管理系统',
  maxFileSize: 10,
  sessionTimeout: 30,
  logLevel: 'info'
})

const docsGenerators = [
  {
    name: 'VitePress',
    tag: '项目文档站',
    description: '适合项目说明、接口文档和发布记录，支持 Markdown 直接生成静态站点。',
    note: '默认入口为 /docs/index.html。',
    url: '/docs/index.html'
  },
  {
    name: 'Docusaurus',
    tag: '成熟社区',
    description: '文档、博客和版本管理能力完善，适合中大型团队知识库。',
    note: '适合需要更强内容组织能力的团队。',
    url: 'https://docusaurus.io/'
  },
  {
    name: 'Astro Starlight',
    tag: '内容体验',
    description: '风格简洁，阅读体验好，适合追求高可读性和易维护性的站点。',
    note: '适合展示型文档与产品说明。',
    url: 'https://starlight.astro.build/'
  }
]

let statusTimer = null

const handleMenuSelect = (index) => {
  activeMenu.value = index
}

const openExternal = (url) => {
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

const handleLogout = () => {
  ElMessageBox.confirm('确认要退出登录吗？', '确认退出', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => {
      localStorage.removeItem('token')
      router.push('/login')
      ElMessage.success('已退出登录')
    })
    .catch(() => {})
}

onMounted(() => {
  statusTimer = setInterval(() => {
    systemStatus.cpu = Math.floor(Math.random() * 100)
    systemStatus.memory = Math.floor(Math.random() * 100)
    systemStatus.disk = Math.floor(Math.random() * 100)
  }, 5000)
})

onUnmounted(() => {
  if (statusTimer) {
    clearInterval(statusTimer)
    statusTimer = null
  }
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
