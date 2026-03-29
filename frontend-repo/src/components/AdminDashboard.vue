<template>
  <div class="admin-dashboard">
    <aside ref="sidebarRef" class="admin-sidebar">
      <div class="brand-block">
        <p class="brand-eyebrow">MRR</p>
        <h1 class="brand-title">病案管理系统</h1>
        <p class="brand-subtitle">后台管理中心 V2026.03.29_0.0.7</p>
      </div>

      <el-menu :default-active="activeMenu" class="sidebar-menu" @select="handleMenuSelect">
        <el-menu-item-group>
          <template #title>
            <span class="menu-group-title">总览</span>
          </template>
          <el-menu-item index="dashboard">
            <el-icon><DataBoard /></el-icon>
            <span>工作台总览</span>
          </el-menu-item>
          <el-menu-item index="statistics">
            <el-icon><TrendCharts /></el-icon>
            <span>统计分析</span>
          </el-menu-item>
          <el-menu-item index="statistics-detail">
            <el-icon><Document /></el-icon>
            <span>病案明细</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group>
          <template #title>
            <span class="menu-group-title">管理</span>
          </template>
          <el-menu-item index="users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="permissions">
            <el-icon><Key /></el-icon>
            <span>权限管理</span>
          </el-menu-item>
          <el-menu-item index="records">
            <el-icon><Document /></el-icon>
            <span>病案管理</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group>
          <template #title>
            <span class="menu-group-title">运行</span>
          </template>
          <el-menu-item index="testing">
            <el-icon><Tools /></el-icon>
            <span>测试中心</span>
          </el-menu-item>
          <el-menu-item index="logs">
            <el-icon><Document /></el-icon>
            <span>日志管理</span>
          </el-menu-item>
          <el-menu-item index="monitoring">
            <el-icon><Monitor /></el-icon>
            <span>监控中心</span>
          </el-menu-item>
          <el-menu-item index="settings">
            <el-icon><Setting /></el-icon>
            <span>系统设置</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group>
          <template #title>
            <span class="menu-group-title">资料</span>
          </template>
          <el-menu-item index="docs">
            <el-icon><Reading /></el-icon>
            <span>文档中心</span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>

      <div class="profile-card">
        <div class="profile-row">
          <el-button
            class="logout-sidebar-btn"
            type="danger"
            plain
            aria-label="退出登录"
            @click="handleLogout"
          >
            <el-icon><SwitchButton /></el-icon>
          </el-button>

          <div class="profile-meta">
            <p class="profile-name">{{ currentUserName || '管理员' }}</p>
            <p class="profile-role">{{ currentRoleName || '未分配角色' }}</p>
          </div>
        </div>
      </div>
    </aside>

    <main class="admin-main">

      <div ref="contentShellRef" class="content-shell pmr-fade-up">
        <template v-if="showDashboard">
          <section ref="heroRef" class="hero-block pmr-hover-lift">
            <div class="hero-copy">
              <p class="eyebrow">Clinical Sanctuary</p>
              <h2>欢迎进入后台管理中心</h2>
              <p>
                当前账号{{ accessSummary }}。你可以在这里直接进入用户、权限、测试、日志、监控、
                病案、统计、病案明细和系统设置页面，所有功能都嵌入在同一个后台壳中。
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

          <section ref="kpiGridRef" class="kpi-grid pmr-stagger">
            <article
              v-for="(card, index) in dashboardCards"
              :key="card.label"
              class="kpi-card pmr-stagger-item pmr-hover-lift"
              :class="card.toneClass"
              :style="{ '--pmr-stagger-index': index }"
            >
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

          <section ref="featureGridRef" class="feature-grid pmr-stagger">
            <article
              v-for="(item, index) in featureCards"
              :key="item.title"
              class="feature-card pmr-stagger-item pmr-hover-lift magnetic-card"
              :style="{ '--pmr-stagger-index': index }"
              @click="handleFeatureClick(item)"
            >
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
        </template>

        <section v-else class="route-panel pmr-fade-up">
          <router-view />
        </section>
      </div>
    </main>
  </div>
</template>
<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DataBoard,
  Document,
  Key,
  Monitor,
  Reading,
  Setting,
  SwitchButton,
  Tools,
  TrendCharts,
  User
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearSession, getCurrentUser, getUserDisplayName, getUserRoleName, isAdminUser } from '@/utils/session'
import { animatePageTransition, applyMagneticEffect, revealHero, revealSidebar, revealStaggeredGrid } from '@/utils/animations'

const router = useRouter()
const route = useRoute()

const currentUser = computed(() => getCurrentUser())
const currentUserName = computed(() => getUserDisplayName() || currentUser.value?.username || '')
const currentRoleName = computed(() => getUserRoleName() || currentUser.value?.roleCode || '')
const isAdmin = computed(() => isAdminUser())
const accessSummary = computed(() => (isAdmin.value ? '拥有完整后台权限' : '拥有受限后台权限'))

const activeMenu = computed(() => {
  const path = route.path
  if (path === '/admin' || path === '/admin-dashboard') return 'dashboard'
  if (path.startsWith('/admin/users')) return 'users'
  if (path.startsWith('/admin/permissions')) return 'permissions'
  if (path.startsWith('/admin/testing')) return 'testing'
  if (path.startsWith('/admin/logs')) return 'logs'
  if (path.startsWith('/admin/monitoring')) return 'monitoring'
  if (path.startsWith('/admin/crud')) return 'records'
  if (path.startsWith('/admin/statistics/detail/') && path !== '/admin/statistics/detail') return 'statistics-archive'
  if (path.startsWith('/admin/statistics/detail')) return 'statistics-detail'
  if (path.startsWith('/admin/statistics')) return 'statistics'
  if (path.startsWith('/admin/settings')) return 'settings'
  if (path === '/docs/index.html') return 'docs'
  return 'dashboard'
})

const showDashboard = computed(() => activeMenu.value === 'dashboard')

const sectionMetaMap = {
  dashboard: {
    title: '工作台总览',
    description: '从这里进入用户、权限、测试、日志、监控、病案、统计与设置模块。'
  },
  users: {
    title: '用户管理',
    description: '维护账号、角色、状态和最近登录信息。'
  },
  permissions: {
    title: '权限管理',
    description: '查看角色权限矩阵与当前账号可见范围。'
  },
  testing: {
    title: '测试中心',
    description: '进入接口冒烟、压力测试和日志清理页面。'
  },
  logs: {
    title: '日志管理',
    description: '查看系统日志、请求明细和审计记录。'
  },
  monitoring: {
    title: '监控中心',
    description: '查看 CPU、内存、磁盘和系统运行状态。'
  },
  records: {
    title: '病案管理',
    description: '进入病案列表、编辑和批量操作。'
  },
  statistics: {
    title: '统计分析',
    description: '查看病案统计、趋势图表与核心指标。'
  },
  'statistics-detail': {
    title: '病案明细',
    description: '独立查看病案分页列表、筛选和排序结果。'
  },
  'statistics-archive': {
    title: '病案档案图片',
    description: '查看单个病案的图片、批量选择、打印与 PDF 导出。'
  },
  settings: {
    title: '系统设置',
    description: '管理基础参数、日志与安全策略。'
  },
  docs: {
    title: '文档中心',
    description: '打开项目说明文档，查看接口和部署说明。'
  }
}

const currentSectionTitle = computed(() => sectionMetaMap[activeMenu.value]?.title || '工作台总览')
const currentSectionDescription = computed(
  () => sectionMetaMap[activeMenu.value]?.description || '嵌入式后台内容区'
)

const dashboardCards = computed(() => [
  {
    label: '当前账号',
    value: currentUserName.value || '未登录',
    note: '登录后会自动沿用会话信息',
    badge: 'LIVE',
    icon: User,
    iconClass: 'primary',
    toneClass: 'accent-blue'
  },
  {
    label: '当前角色',
    value: currentRoleName.value || '未分配',
    note: '角色决定默认权限范围',
    badge: 'ROLE',
    icon: Key,
    iconClass: 'secondary',
    toneClass: 'accent-violet'
  },
  {
    label: '可访问模块',
    value: isAdmin.value ? '全部后台模块' : '受限后台模块',
    note: '用户、权限、测试、日志、监控、病案、统计与设置模块',
    badge: 'ACCESS',
    icon: Tools,
    iconClass: 'tertiary',
    toneClass: 'accent-gold'
  },
  {
    label: '权限摘要',
    value: accessSummary.value,
    note: '会根据当前登录态动态计算',
    badge: 'OK',
    icon: TrendCharts,
    iconClass: 'primary',
    toneClass: 'accent-green'
  }
])

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
    description: '查看角色权限矩阵和当前账号可见范围。',
    badge: '权限视图',
    tone: 'tone-green',
    icon: Key,
    action: () => router.push('/admin/permissions')
  },
  {
    title: '测试中心',
    description: '进入接口冒烟、压力测试和日志清理页面。',
    badge: '测试入口',
    tone: 'tone-cyan',
    icon: Tools,
    action: () => router.push('/admin/testing')
  },
  {
    title: '日志管理',
    description: '查看系统日志、请求明细和审计记录。',
    badge: '日志入口',
    tone: 'tone-slate',
    icon: Document,
    action: () => router.push('/admin/logs')
  },
  {
    title: '监控中心',
    description: '查看 CPU、内存、磁盘和系统运行状态。',
    badge: '监控入口',
    tone: 'tone-blue',
    icon: Monitor,
    action: () => router.push('/admin/monitoring')
  },
  {
    title: '病案管理',
    description: '进入病案列表、编辑和批量操作。',
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
    title: '病案明细',
    description: '独立查看病案分页列表、筛选和排序结果。',
    badge: '明细页面',
    tone: 'tone-slate',
    icon: Document,
    action: () => router.push('/admin/statistics/detail')
  },
  {
    title: '系统设置',
    description: '管理基础参数、日志与安全策略。',
    badge: '系统参数',
    tone: 'tone-slate',
    icon: Setting,
    action: () => router.push('/admin/settings')
  },
  {
    title: '文档中心',
    description: '打开项目说明文档，快速查看接口和部署说明。',
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
  ElMessage.info(`${item.title} 功能即将上线`)
}

const handleMenuSelect = (index) => {
  const routes = {
    dashboard: '/admin',
    users: '/admin/users',
    permissions: '/admin/permissions',
    testing: '/admin/testing',
    logs: '/admin/logs',
    monitoring: '/admin/monitoring',
    records: '/admin/crud',
    statistics: '/admin/statistics',
    'statistics-detail': '/admin/statistics/detail',
    settings: '/admin/settings'
  }

  if (index === 'docs') {
    openDocs()
    return
  }

  const target = routes[index]
  if (target) {
    router.push(target)
  }
}

const sidebarRef = ref(null)
const contentShellRef = ref(null)
const heroRef = ref(null)
const kpiGridRef = ref(null)
const featureGridRef = ref(null)

// 初始化动画
onMounted(() => {
  // 1. 侧边栏优雅滑入
  revealSidebar(sidebarRef.value)

  // 2. 初始仪表盘揭晓
  if (showDashboard.value) {
    nextTick(() => {
      revealHero(heroRef.value)
      
      const kpiItems = kpiGridRef.value?.querySelectorAll('.kpi-card')
      if (kpiItems) revealStaggeredGrid(kpiItems, 0.5)
      
      const featureItems = featureGridRef.value?.querySelectorAll('.feature-card')
      if (featureItems) revealStaggeredGrid(featureItems, 0.7)

      // 3. 应用磁吸交互
      featureItems?.forEach(item => applyMagneticEffect(item, 0.04))
    })
  } else {
    // 处理路由子页面进入动画
    nextTick(() => {
      animatePageTransition(contentShellRef.value)
    })
  }
})

// 监听路由变化，触发现代感的切页效果
watch(() => route.path, () => {
  nextTick(() => {
    animatePageTransition(contentShellRef.value)
    
    // 如果回到仪表盘，重新绑定磁吸
    if (showDashboard.value) {
      setTimeout(() => {
        const featureItems = featureGridRef.value?.querySelectorAll('.feature-card')
        featureItems?.forEach(item => applyMagneticEffect(item, 0.04))
      }, 100)
    }
  })
})

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

.menu-group-title {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #98a2b3;
}

.sidebar-menu :deep(.el-menu-item-group__title) {
  padding: 12px 10px 6px;
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
}

.profile-card {
  margin: 0 24px 24px;
  padding: 14px;
  border-radius: 16px;
  background: #eef2f7;
}

.profile-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logout-sidebar-btn {
  width: 48px;
  height: 48px;
  flex: 0 0 48px;
  padding: 0;
  justify-content: center;
  border-radius: 14px;
  font-weight: 700;
}

.profile-meta {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.profile-name {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.3;
  color: #191c1d;
}

.profile-role {
  margin: 0;
  font-size: 12px;
  line-height: 1.3;
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

.topbar-subtitle {
  margin: 0;
  font-size: 13px;
  color: #667085;
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

.back-btn,
.logout-btn {
  border: none;
  border-radius: 12px;
  box-shadow: none;
}

.content-shell {
  display: grid;
  gap: 20px;
  padding: 24px 32px 40px;
}

.route-panel {
  min-height: calc(100vh - 120px);
}

.hero-block {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) auto;
  gap: 20px;
  align-items: center;
  padding: 28px;
  border-radius: 24px;
  border: 1px solid rgba(195, 197, 215, 0.18);
  background: linear-gradient(140deg, rgba(255, 255, 255, 0.92) 0%, rgba(244, 248, 255, 0.9) 100%);
  box-shadow: 0 16px 40px rgba(24, 65, 134, 0.08);
}

.hero-copy .eyebrow {
  margin: 0;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #003fb1;
}

.hero-copy h2 {
  margin: 10px 0;
  font-size: 32px;
  line-height: 1.2;
  color: #1f2b42;
}

.hero-copy p {
  margin: 0;
  max-width: 760px;
  font-size: 15px;
  line-height: 1.75;
  color: #4b5563;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.kpi-card {
  padding: 20px;
  border-radius: 20px;
  border: 1px solid rgba(195, 197, 215, 0.18);
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 10px 28px rgba(24, 65, 134, 0.08);
}

.kpi-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.kpi-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  color: #fff;
}

.kpi-icon.primary {
  background: linear-gradient(135deg, #1d4ed8, #3b82f6);
}

.kpi-icon.secondary {
  background: linear-gradient(135deg, #7c3aed, #a855f7);
}

.kpi-icon.tertiary {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
}

.kpi-chip {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.12em;
  color: #6b7280;
}

.kpi-label {
  margin: 14px 0 8px;
  font-size: 13px;
  color: #6b7280;
}

.kpi-value {
  font-size: 22px;
  font-weight: 800;
  color: #111827;
  word-break: break-word;
}

.kpi-note {
  margin: 10px 0 0;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.6;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.feature-card {
  min-height: 180px;
  padding: 22px;
  border-radius: 22px;
  border: 1px solid rgba(195, 197, 215, 0.18);
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 10px 28px rgba(24, 65, 134, 0.08);
  cursor: pointer;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;
}

.feature-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 16px 34px rgba(24, 65, 134, 0.12);
  border-color: rgba(0, 63, 177, 0.24);
}

.feature-icon {
  width: 46px;
  height: 46px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  color: #fff;
}

.tone-pink {
  background: linear-gradient(135deg, #d946ef, #ec4899);
}

.tone-green {
  background: linear-gradient(135deg, #16a34a, #14b8a6);
}

.tone-cyan {
  background: linear-gradient(135deg, #0891b2, #06b6d4);
}

.tone-orange {
  background: linear-gradient(135deg, #ea580c, #fb923c);
}

.tone-blue {
  background: linear-gradient(135deg, #2563eb, #3b82f6);
}

.tone-slate {
  background: linear-gradient(135deg, #475569, #64748b);
}

.feature-card h3 {
  margin: 14px 0 8px;
  font-size: 18px;
  font-weight: 800;
  color: #111827;
}

.feature-card p {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: #6b7280;
}

.feature-footer {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

.feature-footer span {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #003fb1;
}

@media (max-width: 1280px) {
  .kpi-grid,
  .feature-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1024px) {
  .admin-sidebar {
    position: relative;
    inset: auto;
    width: 100%;
  }

  .admin-main {
    margin-left: 0;
  }

  .hero-block {
    grid-template-columns: 1fr;
  }

  .topbar {
    padding: 16px 20px;
    flex-wrap: wrap;
  }

  .content-shell {
    padding: 20px;
  }
}

@media (max-width: 720px) {
  .kpi-grid,
  .feature-grid {
    grid-template-columns: 1fr;
  }

  .topbar-actions,
  .hero-actions {
    width: 100%;
    justify-content: stretch;
  }

  .topbar-actions {
    flex-wrap: wrap;
  }

  .topbar-user {
    width: 100%;
    text-align: left;
  }

  .back-btn,
  .logout-btn,
  .hero-actions :deep(.el-button) {
    width: 100%;
    justify-content: center;
  }
}
</style>
