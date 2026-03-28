<template>
  <div class="admin-dashboard">
    <aside class="admin-sidebar">
      <div class="brand-block">
        <p class="brand-eyebrow">CXRMYY</p>
        <h1 class="brand-title">病案管理系统</h1>
        <p class="brand-subtitle">后台管理中心</p>
      </div>

      <el-menu :default-active="activeMenu" class="sidebar-menu" @select="handleMenuSelect">
        <el-menu-item index="dashboard"><el-icon><DataBoard /></el-icon><span>仪表盘</span></el-menu-item>
        <el-menu-item index="users"><el-icon><User /></el-icon><span>用户管理</span></el-menu-item>
        <el-menu-item index="permissions"><el-icon><Key /></el-icon><span>权限管理</span></el-menu-item>
        <el-menu-item index="testing"><el-icon><Tools /></el-icon><span>测试中心</span></el-menu-item>
        <el-menu-item index="settings"><el-icon><Setting /></el-icon><span>系统设置</span></el-menu-item>
        <el-menu-item index="records"><el-icon><Document /></el-icon><span>病案管理</span></el-menu-item>
        <el-menu-item index="statistics"><el-icon><TrendCharts /></el-icon><span>统计分析</span></el-menu-item>
        <el-menu-item index="docs"><el-icon><Reading /></el-icon><span>文档中心</span></el-menu-item>
      </el-menu>

      <div class="profile-card">
        <div class="profile-avatar"><el-icon><User /></el-icon></div>
        <div class="profile-copy">
          <p class="profile-name">{{ currentUserName || '管理员' }}</p>
          <p class="profile-role">{{ currentRoleName || '未分配角色' }}</p>
        </div>
      </div>
    </aside>

    <main class="admin-main">
      <header class="topbar">
        <div class="topbar-copy">
          <p class="topbar-eyebrow">后台总览</p>
          <h2 class="topbar-title">工作台入口</h2>
        </div>
        <div class="topbar-actions">
          <div class="topbar-user">
            <span>{{ currentUserName || 'Administrator' }}</span>
            <small>{{ currentRoleName || 'Admin console' }}</small>
          </div>
          <el-button class="logout-btn" type="primary" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
            退出登录
          </el-button>
        </div>
      </header>

      <div class="page-canvas">
        <section class="hero-block">
          <div>
            <p class="eyebrow">Clinical Sanctuary</p>
            <h2>欢迎进入后台管理中心</h2>
            <p>
              当前账号{{ accessSummary }}。你可以从这里直接进入用户、权限、测试、系统设置和业务管理页面。
            </p>
          </div>
          <div class="hero-actions">
            <el-button type="primary" @click="router.push('/admin/users')">
              <el-icon><User /></el-icon>
              用户管理
            </el-button>
            <el-button @click="router.push('/admin/permissions')">
              <el-icon><Key /></el-icon>
              权限管理
            </el-button>
          </div>
        </section>

        <section class="kpi-grid">
          <article v-for="card in dashboardCards" :key="card.label" class="kpi-card" :class="card.toneClass">
            <div class="kpi-top">
              <div class="kpi-icon" :class="card.iconClass">
                <el-icon><component :is="card.icon" /></el-icon>
              </div>
              <span class="kpi-chip">{{ card.badge }}</span>
            </div>
            <p class="kpi-label">{{ card.label }}</p>
            <div class="kpi-value">{{ card.value }}</div>
            <p class="kpi-note">{{ card.note }}</p>
          </article>
        </section>

        <section class="feature-grid">
          <article v-for="item in featureCards" :key="item.title" class="feature-card" @click="handleFeatureClick(item)">
            <div class="feature-icon" :class="item.tone">
              <el-icon><component :is="item.icon" /></el-icon>
            </div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.description }}</p>
            <div class="feature-footer">
              <span>{{ item.badge }}</span>
            </div>
          </article>
        </section>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DataBoard,
  Document,
  Key,
  Reading,
  Setting,
  SwitchButton,
  Tools,
  TrendCharts,
  User
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearSession, getCurrentUser, getUserDisplayName, getUserRoleName, isAdminUser } from '@/utils/session.js'

const router = useRouter()
const route = useRoute()

const currentUser = computed(() => getCurrentUser())
const currentUserName = computed(() => getUserDisplayName() || currentUser.value?.username || '')
const currentRoleName = computed(() => getUserRoleName() || currentUser.value?.roleCode || '')
const isAdmin = computed(() => isAdminUser())
const accessSummary = computed(() => (isAdmin.value ? '拥有全部后台权限' : '拥有受限后台权限'))

const activeMenu = computed(() => {
  const path = route.path
  if (path === '/admin/users') return 'users'
  if (path === '/admin/permissions') return 'permissions'
  if (path === '/admin/testing') return 'testing'
  if (path === '/admin/settings') return 'settings'
  if (path === '/admin/crud') return 'records'
  if (path === '/admin/statistics') return 'statistics'
  if (path === '/docs/index.html') return 'docs'
  return 'dashboard'
})

const dashboardCards = [
  {
    label: '当前账号',
    value: currentUserName,
    note: '登录后会自动沿用会话信息',
    badge: 'LIVE',
    icon: User,
    iconClass: 'primary',
    toneClass: 'accent-blue'
  },
  {
    label: '当前角色',
    value: currentRoleName,
    note: '角色决定默认权限范围',
    badge: 'ROLE',
    icon: Key,
    iconClass: 'secondary',
    toneClass: 'accent-violet'
  },
  {
    label: '可访问模块',
    value: isAdmin,
    note: '用户、权限、测试、设置等后台模块',
    badge: 'ACCESS',
    icon: Tools,
    iconClass: 'tertiary',
    toneClass: 'accent-gold'
  },
  {
    label: '权限摘要',
    value: accessSummary,
    note: '从会话中实时计算当前访问能力',
    badge: 'OK',
    icon: TrendCharts,
    iconClass: 'primary',
    toneClass: 'accent-green'
  }
]

const featureCards = [
  {
    title: '用户管理',
    description: '维护账号、角色和基础状态，支持编辑和禁用。',
    badge: '用户中心',
    tone: 'tone-pink',
    icon: User,
    action: () => router.push('/admin/users')
  },
  {
    title: '权限管理',
    description: '查看角色权限矩阵、模块分类和当前账号可见范围。',
    badge: '权限视图',
    tone: 'tone-green',
    icon: Key,
    action: () => router.push('/admin/permissions')
  },
  {
    title: '测试中心',
    description: '进入接口冒烟、压力测试和日志清理等后台测试页面。',
    badge: '测试入口',
    tone: 'tone-cyan',
    icon: Tools,
    action: () => router.push('/admin/testing')
  },
  {
    title: '病案管理',
    description: '继续处理病案列表、编辑和批量操作。',
    badge: '业务入口',
    tone: 'tone-orange',
    icon: Document,
    action: () => router.push('/admin/crud')
  },
  {
    title: '统计分析',
    description: '查看病案统计、趋势图表和业务分布。',
    badge: '数据分析',
    tone: 'tone-blue',
    icon: TrendCharts,
    action: () => router.push('/admin/statistics')
  },
  {
    title: '系统设置',
    description: '调整系统参数、日志保留策略和安全配置。',
    badge: '系统参数',
    tone: 'tone-slate',
    icon: Setting,
    action: () => router.push('/admin/settings')
  },
  {
    title: '文档中心',
    description: '打开项目文档，快速查看接口和部署说明。',
    badge: '团队协作',
    tone: 'tone-orange',
    icon: Reading,
    action: openDocs
  }
]

const handleFeatureClick = (item) => {
  if (typeof item.action === 'function') {
    item.action()
    return
  }
  ElMessage.info(`${item.title} feature coming soon`)
}

const handleMenuSelect = (index) => {
  const routes = {
    dashboard: '/admin-dashboard',
    users: '/admin/users',
    permissions: '/admin/permissions',
    testing: '/admin/testing',
    settings: '/admin/settings',
    records: '/admin/crud',
    statistics: '/admin/statistics',
    docs: '/docs/index.html'
  }

  const target = routes[index]
  if (!target) return
  if (index === 'docs') {
    openDocs()
    return
  }
  router.push(target)
}

function openDocs() {
  window.open('/docs/index.html', '_blank', 'noopener,noreferrer')
}

function handleLogout() {
  ElMessageBox.confirm('确认退出登录吗？', '退出登录', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => {
      clearSession()
      router.push('/login')
      ElMessage.success('已退出登录')
    })
    .catch(() => {})
}
</script>

<style scoped>
.admin-dashboard {
  min-height: 100vh;
  background:
    radial-gradient(circle at top right, rgba(26, 86, 219, 0.08), transparent 28%),
    linear-gradient(160deg, #f8fbff 0%, #fbfdff 42%, #eef4ff 100%);
  color: #191c1d;
}

.admin-sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  width: 256px;
  display: flex;
  flex-direction: column;
  background: rgba(248, 249, 250, 0.92);
  backdrop-filter: blur(14px);
  border-right: 1px solid rgba(195, 197, 215, 0.18);
  z-index: 50;
}

.brand-block {
  padding: 32px 24px 20px;
}

.brand-eyebrow {
  margin: 0;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #003fb1;
}

.brand-title {
  margin: 6px 0 0;
  font-size: 24px;
  font-weight: 800;
  color: #003fb1;
}

.brand-subtitle {
  margin: 6px 0 0;
  font-size: 12px;
  color: #737686;
}

.sidebar-menu {
  flex: 1;
  border-right: none;
  background: transparent;
  padding: 0 16px 16px;
}

.sidebar-menu :deep(.el-menu-item) {
  height: 48px;
  line-height: 48px;
  margin-bottom: 8px;
  border-radius: 14px;
  color: #6b7280;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: rgba(0, 63, 177, 0.08);
  color: #1d4ed8;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: rgba(219, 225, 255, 0.55);
  color: #003fb1;
  font-weight: 700;
  border-left: 4px solid #003fb1;
}

.profile-card {
  margin: 0 24px 24px;
  padding: 14px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  background: #eef2f7;
}

.profile-avatar {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: #fff;
  background: linear-gradient(135deg, #003fb1, #1a56db);
}

.profile-name {
  margin: 0;
  font-size: 14px;
  font-weight: 800;
  color: #191c1d;
}

.profile-role {
  margin: 4px 0 0;
  font-size: 12px;
  color: #737686;
}

.admin-main {
  margin-left: 256px;
  min-height: 100vh;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 40;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 18px 32px;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(195, 197, 215, 0.16);
  box-shadow: 0 12px 32px rgba(25, 28, 29, 0.06);
}

.topbar-copy {
  display: grid;
  gap: 6px;
}

.topbar-eyebrow {
  margin: 0;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #737686;
}

.topbar-title {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  color: #1f2b42;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.topbar-user {
  display: grid;
  text-align: right;
}

.topbar-user span {
  font-size: 13px;
  font-weight: 700;
  color: #191c1d;
}

.topbar-user small {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: #737686;
}

.logout-btn {
  border: none;
  border-radius: 12px;
  background: #003fb1;
  box-shadow: none;
}

.page-canvas {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 28px 32px 40px;
}

.hero-block {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 24px;
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #737686;
}

.hero-block h2 {
  margin: 0;
  font-size: 44px;
  line-height: 1;
  font-weight: 800;
  letter-spacing: -0.04em;
  color: #191c1d;
}

.hero-block p {
  margin: 12px 0 0;
  max-width: 720px;
  font-size: 18px;
  line-height: 1.6;
  color: #434654;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.kpi-card {
  position: relative;
  overflow: hidden;
  border-radius: 18px;
  padding: 24px;
  background: #fff;
  border: 1px solid rgba(195, 197, 215, 0.22);
}

.kpi-card::before {
  content: '';
  position: absolute;
  inset: 0 auto auto 0;
  width: 100%;
  height: 4px;
  background: linear-gradient(90deg, #003fb1, #1a56db);
}

.kpi-card.accent-violet::before {
  background: linear-gradient(90deg, #4b5c92, #b5c4ff);
}

.kpi-card.accent-gold::before {
  background: linear-gradient(90deg, #d97706, #f59e0b);
}

.kpi-card.accent-green::before {
  background: linear-gradient(90deg, #0f9d58, #34c759);
}

.kpi-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 18px;
}

.kpi-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: grid;
  place-items: center;
}

.kpi-icon.primary {
  background: rgba(0, 63, 177, 0.08);
  color: #003fb1;
}

.kpi-icon.secondary {
  background: rgba(75, 92, 146, 0.08);
  color: #4b5c92;
}

.kpi-icon.tertiary {
  background: rgba(173, 59, 0, 0.1);
  color: #852b00;
}

.kpi-chip {
  padding: 5px 10px;
  border-radius: 999px;
  background: #dbe1ff;
  color: #003fb1;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.kpi-label {
  margin: 0;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #434654;
}

.kpi-value {
  margin-top: 8px;
  font-size: 28px;
  line-height: 1.1;
  font-weight: 800;
  color: #191c1d;
}

.kpi-note {
  margin: 10px 0 0;
  font-size: 13px;
  color: #5f6b84;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.feature-card {
  cursor: pointer;
  transition:
    transform var(--pmr-motion-duration-normal) var(--pmr-motion-ease-standard),
    box-shadow var(--pmr-motion-duration-normal) var(--pmr-motion-ease-standard),
    border-color var(--pmr-motion-duration-normal) var(--pmr-motion-ease-standard);
  border: 1px solid rgba(195, 197, 215, 0.18);
  border-radius: 18px;
  padding: 22px;
  background: #fff;
}

.feature-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--pmr-shadow-surface-lg);
  border-color: #cddcf9;
}

.feature-icon {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-size: 22px;
}

.tone-blue {
  color: #1e54d6;
  background: rgba(47, 111, 255, 0.12);
}

.tone-green {
  color: #0f766e;
  background: rgba(20, 184, 166, 0.14);
}

.tone-orange {
  color: #b45309;
  background: rgba(245, 158, 11, 0.16);
}

.tone-pink {
  color: #be185d;
  background: rgba(244, 114, 182, 0.18);
}

.tone-cyan {
  color: #0e7490;
  background: rgba(34, 211, 238, 0.18);
}

.tone-slate {
  color: #475569;
  background: rgba(148, 163, 184, 0.2);
}

.feature-card h3 {
  margin: 14px 0 8px;
  font-size: 19px;
  color: #1f2b42;
}

.feature-card p {
  margin: 0;
  color: #566887;
  line-height: 1.65;
  min-height: 72px;
  font-size: 14px;
}

.feature-footer {
  margin-top: 16px;
}

.feature-footer span {
  display: inline-flex;
  align-items: center;
  height: 26px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  padding: 0 10px;
  color: #1e54d6;
  background: rgba(47, 111, 255, 0.12);
}

@media (max-width: 1200px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .admin-sidebar {
    position: relative;
    width: 100%;
    height: auto;
    inset: auto;
    border-right: none;
    border-bottom: 1px solid rgba(195, 197, 215, 0.18);
  }

  .admin-main {
    margin-left: 0;
  }

  .topbar {
    position: relative;
    flex-direction: column;
    align-items: stretch;
  }

  .topbar-actions {
    justify-content: space-between;
    flex-wrap: wrap;
  }

  .page-canvas {
    padding: 20px;
  }

  .hero-block {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 640px) {
  .kpi-grid,
  .feature-grid {
    grid-template-columns: 1fr;
  }

  .hero-block h2 {
    font-size: 32px;
  }
}
</style>
