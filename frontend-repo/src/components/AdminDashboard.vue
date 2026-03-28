<template>
  <div class="admin-dashboard">
    <aside class="admin-sidebar">
      <div class="brand-block">
        <p class="brand-eyebrow">CXRMYY</p>
        <h1 class="brand-title">病案翻拍管理系统</h1>
        <p class="brand-subtitle">@weepwood</p>
      </div>

      <el-menu :default-active="activeMenu" class="sidebar-menu" @select="handleMenuSelect">
        <el-menu-item index="dashboard"><el-icon><DataBoard /></el-icon><span>仪表盘</span></el-menu-item>
        <el-menu-item index="users"><el-icon><User /></el-icon><span>用户管理</span></el-menu-item>
        <el-menu-item index="records"><el-icon><Document /></el-icon><span>病案管理</span></el-menu-item>
        <el-menu-item index="testing"><el-icon><Tools /></el-icon><span>系统测试</span></el-menu-item>
        <el-menu-item index="logs"><el-icon><DocumentCopy /></el-icon><span>系统日志</span></el-menu-item>
        <el-menu-item index="monitoring"><el-icon><Monitor /></el-icon><span>系统监控</span></el-menu-item>
        <el-menu-item index="settings"><el-icon><Setting /></el-icon><span>系统设置</span></el-menu-item>
      </el-menu>

      <div class="profile-card">
        <div class="profile-avatar"><el-icon><User /></el-icon></div>
        <div class="profile-copy">
          <p class="profile-name">管理员</p>
          <p class="profile-role">Chief of Operations</p>
        </div>
      </div>
    </aside>

    <main class="admin-main">
      <header class="topbar">
        <div class="search-shell">
          <el-icon class="search-icon"><Search /></el-icon>
          <input v-model="searchTerm" type="text" placeholder="搜索病例、用户或日志..." />
        </div>
        <div class="topbar-actions">
          <button class="icon-button" type="button"><el-icon><Bell /></el-icon></button>
          <button class="icon-button" type="button"><el-icon><Setting /></el-icon></button>
          <div class="topbar-divider"></div>
          <div class="topbar-copy">
            <span class="topbar-role">Case Manager</span>
            <span class="topbar-version">v2.4.0</span>
          </div>
          <el-button class="logout-btn" type="primary" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>退出登录
          </el-button>
        </div>
      </header>

      <div class="page-canvas">
        <template v-if="activeMenu === 'dashboard'">
          <section class="hero-block">
            <div>
              <p class="eyebrow">Clinical Sanctuary</p>
              <h2>临床总览</h2>
              <p>系统状态 <span>稳定</span>，本班次还有 {{ stats.todayRecords }} 条待处理记录。</p>
            </div>
            <div class="hero-tools">
              <div class="segmented">
                <button type="button" class="active">最近 30 天</button>
                <button type="button">最近一季度</button>
              </div>
              <el-button class="export-btn" type="primary"><el-icon><Download /></el-icon>导出报告</el-button>
            </div>
          </section>

          <section class="api-access-panel">
            <div class="api-access-copy">
              <p class="eyebrow">API Gateway</p>
              <h3>Swagger 文档</h3>
              <p>当前地址：{{ swaggerUrl || '未配置' }}</p>
            </div>
            <div class="api-access-actions">
              <el-button class="swagger-btn" type="primary" :disabled="!swaggerUrl" @click="openSwagger">
                <el-icon><Link /></el-icon>打开 Swagger
              </el-button>
            </div>
          </section>

          <section class="kpi-grid">
            <article v-for="card in dashboardCards" :key="card.key" class="kpi-card" :class="card.toneClass">
              <div class="kpi-top">
                <div class="kpi-icon" :class="card.iconClass"><el-icon><component :is="card.icon" /></el-icon></div>
                <span class="kpi-chip">{{ card.trend }}</span>
              </div>
              <p class="kpi-label">{{ card.label }}</p>
              <div class="kpi-value">{{ card.value }}</div>
              <p class="kpi-note">{{ card.note }}</p>
            </article>
          </section>

          <section class="dashboard-grid">
            <div class="left-column">
              <article class="panel">
                <div class="panel-header">
                  <div>
                    <h3 class="panel-title">我的任务</h3>
                    <p class="panel-subtitle">按优先级排序的临床工作流</p>
                  </div>
                  <button class="panel-action" type="button">查看全部 <el-icon><ArrowRight /></el-icon></button>
                </div>
                <div class="task-list">
                  <div v-for="task in dashboardTasks" :key="task.title" class="task-row">
                    <div class="task-left">
                      <div class="task-mark" :class="task.markClass">{{ task.mark }}</div>
                      <div class="task-copy">
                        <p class="task-title">{{ task.title }}</p>
                        <p class="task-desc">{{ task.description }}</p>
                      </div>
                    </div>
                    <span class="task-badge" :class="task.badgeClass">{{ task.badge }}</span>
                  </div>
                </div>
              </article>

              <article class="panel">
                <div class="panel-header">
                  <div>
                    <h3 class="panel-title">病例趋势</h3>
                    <p class="panel-subtitle">当前周的收治与出院分布</p>
                  </div>
                  <div class="trend-legend">
                    <span><i class="legend-dot admissions"></i>收治</span>
                    <span><i class="legend-dot discharges"></i>出院</span>
                  </div>
                </div>
                <div class="trend-chart">
                  <div class="trend-grid"><span></span><span></span><span></span><span></span></div>
                  <div class="trend-bars">
                    <div v-for="item in weeklyTrend" :key="item.day" class="trend-slot">
                      <span class="trend-admissions" :style="{ height: `${item.admissions}%` }"></span>
                      <span class="trend-discharges" :style="{ height: `${item.discharges}%` }"></span>
                    </div>
                  </div>
                  <div class="trend-labels">
                    <span v-for="item in weeklyTrend" :key="`${item.day}-label`">{{ item.day }}</span>
                  </div>
                </div>
              </article>
            </div>

            <aside class="right-column">
              <article class="panel">
                <div class="panel-header">
                  <div>
                    <h3 class="panel-title">最近动态</h3>
                    <p class="panel-subtitle">临床工作流的实时事件线程</p>
                  </div>
                </div>
                <div class="timeline">
                  <div class="timeline-track"></div>
                  <div v-for="item in recentActivities" :key="item.title" class="timeline-item">
                    <div class="timeline-dot" :class="item.level"></div>
                    <p class="timeline-meta" :class="item.level">{{ item.time }}</p>
                    <p class="timeline-title">{{ item.title }}</p>
                    <p class="timeline-desc">{{ item.description }}</p>
                  </div>
                </div>
                <button class="ghost-button" type="button">查看完整审计日志</button>
              </article>

              <article class="panel">
                <div class="panel-header">
                  <div>
                    <h3 class="panel-title">系统状态</h3>
                    <p class="panel-subtitle">每 5 秒自动刷新</p>
                  </div>
                  <span class="section-pill">{{ systemHealth }}</span>
                </div>
                <div class="status-stack">
                  <div v-for="metric in systemMetrics" :key="metric.label" class="status-row">
                    <div class="status-head"><span>{{ metric.label }}</span><strong>{{ metric.value }}%</strong></div>
                    <div class="status-bar"><span :style="{ width: `${metric.value}%` }"></span></div>
                  </div>
                </div>
                <div class="status-note">最近刷新：{{ lastRefreshLabel }}。系统健康度会随监控数据自动变化。</div>
              </article>
            </aside>
          </section>

          <section class="docs-section">
            <div class="docs-header">
              <div>
                <h3>文档站点推荐</h3>
                <p>项目文档、接口说明和发布记录可以在这里统一管理。</p>
              </div>
            </div>
            <div class="docs-grid">
              <article v-for="item in docsGenerators" :key="item.name" class="doc-card">
                <div class="doc-card-head">
                  <h4>{{ item.name }}</h4>
                  <span class="doc-tag">{{ item.tag }}</span>
                </div>
                <p class="doc-desc">{{ item.description }}</p>
                <div class="doc-meta">{{ item.note }}</div>
                <el-button class="doc-link" type="primary" @click="openExternal(item.url)">
                  <el-icon><Link /></el-icon>打开链接
                </el-button>
              </article>
            </div>
          </section>
        </template>

        <section v-else class="module-shell">
          <div class="module-shell-header">
            <div>
              <p class="eyebrow">Management Module</p>
              <h2>{{ sectionMeta.title }}</h2>
              <p>{{ sectionMeta.description }}</p>
            </div>
            <span class="section-pill">{{ sectionMeta.pill }}</span>
          </div>
          <div class="module-shell-body">
            <UsersView v-if="activeMenu === 'users'" :users="users" />
            <RecordsView v-if="activeMenu === 'records'" />
            <TestingView v-if="activeMenu === 'testing'" />
            <LogsView v-if="activeMenu === 'logs'" />
            <MonitoringView v-if="activeMenu === 'monitoring'" />
            <SettingsView
              v-if="activeMenu === 'settings'"
              :settings="settings"
              @save="handleSaveSettings"
              @reset="handleResetSettings"
            />
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  Bell,
  Calendar,
  Connection,
  DataBoard,
  Document,
  DocumentCopy,
  Download,
  Link,
  Monitor,
  Search,
  Setting,
  SwitchButton,
  Tools,
  User
} from '@element-plus/icons-vue'
import UsersView from './admin/UsersView.vue'
import RecordsView from './admin/RecordsView.vue'
import TestingView from './admin/TestingView.vue'
import LogsView from './admin/LogsView.vue'
import MonitoringView from './admin/MonitoringView.vue'
import SettingsView from './admin/SettingsView.vue'
import { adminSettingsStorageKey } from '@/shared/constants/adminDashboard'

const router = useRouter()
const activeMenu = ref('dashboard')
const searchTerm = ref('')
const lastRefreshLabel = ref('刚刚')

const stats = reactive({ totalUsers: 3, totalRecords: 3, todayRecords: 1, onlineUsers: 2 })
const systemStatus = reactive({ cpu: 45, memory: 62, disk: 38 })
const users = ref([
  { id: 1, username: 'admin', email: 'admin@example.com', role: '管理员', status: 'active', lastLogin: '2024-01-15 10:30' },
  { id: 2, username: 'doctor1', email: 'doctor1@example.com', role: '医生', status: 'active', lastLogin: '2024-01-15 09:15' },
  { id: 3, username: 'nurse1', email: 'nurse1@example.com', role: '护士', status: 'active', lastLogin: '2024-01-15 08:45' }
])
const defaultSettings = {
  systemName: '病案管理系统',
  maxFileSize: 10,
  sessionTimeout: 30,
  logLevel: 'info',
  swaggerUrl: '/swagger-ui/index.html'
}
const settings = reactive({ ...defaultSettings })
const settingsStorageKey = adminSettingsStorageKey

const dashboardCards = computed(() => [
  { key: 'users', label: '总用户数', value: stats.totalUsers, note: '管理人员与临床账号总览', trend: '+12% vs 上周', icon: User, iconClass: 'primary', toneClass: 'accent-blue' },
  { key: 'records', label: '总病案数', value: stats.totalRecords, note: '当前已归档的病例记录', trend: '+8% vs 上周', icon: Document, iconClass: 'secondary', toneClass: 'accent-violet' },
  { key: 'today', label: '今日新增', value: stats.todayRecords, note: '今日录入和更新的记录', trend: '实时更新', icon: Calendar, iconClass: 'tertiary', toneClass: 'accent-gold' },
  { key: 'online', label: '在线用户', value: stats.onlineUsers, note: '当前活跃会话数量', trend: '系统正常', icon: Connection, iconClass: 'primary', toneClass: 'accent-green' }
])

const dashboardTasks = [
  { title: '放射报告复核：Case #8821', description: '患者：Jonathan Meyers · 2 小时内截止', badge: '紧急', mark: 'CR', markClass: 'critical', badgeClass: 'critical' },
  { title: '更新护理计划：Case #8790', description: '患者：Elena Rodriguez · 需要随访', badge: '稳定', mark: 'ST', markClass: 'stable', badgeClass: 'stable' },
  { title: '用药核对：Case #8812', description: '患者：Marcus Wu · 术后审计', badge: '待处理', mark: 'PD', markClass: 'pending', badgeClass: 'pending' }
]

const weeklyTrend = [
  { day: 'Mon', admissions: 25, discharges: 18 },
  { day: 'Tue', admissions: 35, discharges: 28 },
  { day: 'Wed', admissions: 30, discharges: 24 },
  { day: 'Thu', admissions: 60, discharges: 42 },
  { day: 'Fri', admissions: 40, discharges: 30 },
  { day: 'Sat', admissions: 32, discharges: 22 },
  { day: 'Sun', admissions: 18, discharges: 16 }
]

const recentActivities = ref([
  { title: '实验室结果已发布', description: '患者 #9012 的检验结果已同步到临床模块。', time: '14:32 · 今天', level: 'primary' },
  { title: '病例 #8820 已出院', description: '医生已完成最后的出院流程，护理说明已发送。', time: '11:15 · 今天', level: 'muted' },
  { title: '新增转诊记录', description: '内科转入一例新病例，优先级已设为“紧急审查”。', time: '09:04 · 今天', level: 'primary' },
  { title: '系统审计完成', description: '周度合规报告已生成，所有记录均通过校验。', time: '昨天', level: 'muted' },
  { title: '药品同步完成', description: '患者 #7741 的远程问诊记录已完成剂量调整审批。', time: '昨天', level: 'primary' }
])

const docsGenerators = [
  { name: 'VitePress', tag: '项目文档', description: '适合项目说明、接口文档和发布记录，支持 Markdown 直接生成静态站点。', note: '默认入口为 /docs/index.html。', url: '/docs/index.html' },
  { name: 'Docusaurus', tag: '成熟社区', description: '文档、博客和版本管理能力完善，适合中大型团队知识库。', note: '适合需要更强内容组织能力的团队。', url: 'https://docusaurus.io/' },
  { name: 'Astro Starlight', tag: '内容体验', description: '风格简洁，阅读体验优秀，适合追求高可读性和易维护性的站点。', note: '适合展示型文档与产品说明。', url: 'https://starlight.astro.build/' }
]

const sectionMetaMap = {
  users: { title: '用户管理', description: '管理账户权限、状态和最近登录信息。', pill: '管理模块' },
  records: { title: '病案管理', description: '查看、编辑与维护病案记录。', pill: '业务模块' },
  testing: { title: '系统测试', description: '执行接口请求和功能验证，检查系统可用性。', pill: '测试模块' },
  logs: { title: '系统日志', description: '追踪关键操作、告警与后台事件。', pill: '审计模块' },
  monitoring: { title: '系统监控', description: '查看 CPU、内存、磁盘和网络的运行状态。', pill: '监控模块' },
  monitoring: { title: '系统监控', description: '查看 CPU、内存、磁盘和网络的运行状态。', pill: '监控模块' },
  settings: { title: '系统设置', description: '配置系统参数、安全策略和通知规则。', pill: '配置模块' }
}

const sectionMeta = computed(() => sectionMetaMap[activeMenu.value] || sectionMetaMap.users)
const systemMetrics = computed(() => [
  { label: 'CPU 使用率', value: systemStatus.cpu },
  { label: '内存使用率', value: systemStatus.memory },
  { label: '磁盘使用率', value: systemStatus.disk }
])
const systemHealth = computed(() => {
  const average = (systemStatus.cpu + systemStatus.memory + systemStatus.disk) / 3
  if (average < 55) return '稳定'
  if (average < 75) return '关注'
  return '高负载'
})

const swaggerUrl = computed(() => (settings.swaggerUrl || '').trim())
const normalizeUrl = (value) => {
  const raw = (value || '').trim()
  if (!raw) return ''
  try {
    return new URL(raw, window.location.origin).toString()
  } catch {
    return raw
  }
}

let statusTimer = null
const handleMenuSelect = (index) => { activeMenu.value = index }
const openExternal = (url) => { if (url) window.open(url, '_blank', 'noopener,noreferrer') }
const openSwagger = () => {
  const url = normalizeUrl(swaggerUrl.value)
  if (!url) {
    ElMessage.warning('请先在系统设置中配置 Swagger 地址')
    return
  }
  openExternal(url)
}
const handleSaveSettings = (updatedSettings = settings) => {
  localStorage.setItem(settingsStorageKey, JSON.stringify(updatedSettings))
  ElMessage.success('系统设置已保存')
}
const handleResetSettings = () => {
  Object.assign(settings, defaultSettings)
  localStorage.removeItem(settingsStorageKey)
  ElMessage.success('已恢复默认设置')
}
const handleLogout = () => {
  ElMessageBox.confirm('确认要退出登录吗？', '确认退出', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    localStorage.removeItem('token')
    router.push('/login')
    ElMessage.success('已退出登录')
  }).catch(() => {})
}

onMounted(() => {
  try {
    const storedSettings = localStorage.getItem(settingsStorageKey)
    if (storedSettings) {
      const parsedSettings = JSON.parse(storedSettings)
      Object.assign(settings, defaultSettings, parsedSettings)
    }
  } catch {
    Object.assign(settings, defaultSettings)
  }

  statusTimer = setInterval(() => {
    systemStatus.cpu = Math.floor(Math.random() * 100)
    systemStatus.memory = Math.floor(Math.random() * 100)
    systemStatus.disk = Math.floor(Math.random() * 100)
    lastRefreshLabel.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  }, 5000)
})

onUnmounted(() => {
  if (statusTimer) clearInterval(statusTimer)
})
</script>

<style scoped>
.admin-dashboard{min-height:100vh;background:radial-gradient(circle at top right,rgba(26,86,219,.08),transparent 28%),linear-gradient(160deg,#f8fbff 0%,#fbfdff 42%,#eef4ff 100%);color:#191c1d;font-family:Inter,"Segoe UI",Arial,sans-serif}
.admin-sidebar{position:fixed;inset:0 auto 0 0;width:256px;display:flex;flex-direction:column;background:rgba(248,249,250,.92);backdrop-filter:blur(14px);border-right:1px solid rgba(195,197,215,.18);z-index:50}
.brand-block{padding:32px 24px 20px}.brand-eyebrow{margin:0;font-size:12px;font-weight:800;letter-spacing:.14em;text-transform:uppercase;color:#003fb1}.brand-title{margin:6px 0 0;font-family:Manrope,Inter,sans-serif;font-size:24px;font-weight:800;letter-spacing:-.04em;color:#003fb1}.brand-subtitle{margin:6px 0 0;font-size:12px;color:#737686}
.sidebar-menu{flex:1;border-right:none;background:transparent;padding:0 16px 16px}
.sidebar-menu :deep(.el-menu-item){height:48px;line-height:48px;margin-bottom:8px;border-radius:14px;color:#6b7280;transition:background-color .2s ease,color .2s ease,transform .15s ease}
.sidebar-menu :deep(.el-menu-item:hover){background:rgba(0,63,177,.08);color:#1d4ed8}.sidebar-menu :deep(.el-menu-item.is-active){background:rgba(219,225,255,.55);color:#003fb1;font-weight:700;border-right:4px solid #003fb1}.sidebar-menu :deep(.el-menu-item .el-icon){font-size:18px}
.profile-card{margin:0 24px 24px;padding:14px;border-radius:16px;display:flex;align-items:center;gap:12px;background:#eef2f7}.profile-avatar{width:44px;height:44px;border-radius:12px;display:grid;place-items:center;color:#fff;background:linear-gradient(135deg,#003fb1,#1a56db);box-shadow:0 10px 18px rgba(0,63,177,.18)}.profile-name{margin:0;font-size:14px;font-weight:800;color:#191c1d}.profile-role{margin:4px 0 0;font-size:12px;color:#737686}
.admin-main{margin-left:256px;min-height:100vh}.topbar{position:sticky;top:0;z-index:40;display:flex;justify-content:space-between;align-items:center;gap:16px;padding:18px 32px;background:rgba(255,255,255,.78);backdrop-filter:blur(12px);border-bottom:1px solid rgba(195,197,215,.16);box-shadow:0 12px 32px rgba(25,28,29,.06)}
.search-shell{position:relative;flex:1;max-width:560px}.search-shell input{width:100%;border:none;outline:none;border-radius:999px;background:#f3f4f5;padding:12px 18px 12px 44px;font-size:14px;color:#191c1d;transition:box-shadow .2s ease,background-color .2s ease}.search-shell input:focus{background:#fff;box-shadow:0 0 0 2px rgba(19,83,216,.16)}.search-icon{position:absolute;left:14px;top:50%;transform:translateY(-50%);color:#8b95a7}
.topbar-actions{display:flex;align-items:center;gap:10px}.icon-button{width:40px;height:40px;border:none;border-radius:12px;display:grid;place-items:center;background:#003fb1;color:#fff;cursor:pointer;transition:background-color .2s ease,color .2s ease}.icon-button:hover{background:#1a56db;color:#fff}.topbar-divider{width:1px;height:32px;background:#e2e8f0;margin:0 4px}.topbar-copy{text-align:right;display:flex;flex-direction:column;align-items:flex-end}.topbar-role{font-size:13px;font-weight:700;color:#191c1d}.topbar-version{margin-top:2px;font-size:10px;text-transform:uppercase;letter-spacing:.14em;color:#737686}.logout-btn{border:none;border-radius:12px;background:#003fb1;box-shadow:none}
.page-canvas{display:flex;flex-direction:column;gap:24px;padding:28px 32px 40px}.hero-block{display:flex;justify-content:space-between;align-items:flex-end;gap:24px}.hero-block h2{margin:0;font-family:Manrope,Inter,sans-serif;font-size:44px;line-height:1;font-weight:800;letter-spacing:-.04em;color:#191c1d}.hero-block p{margin:12px 0 0;font-size:18px;line-height:1.6;color:#434654}.hero-block p span{color:#003fb1;font-weight:700}.eyebrow{margin:0 0 8px;font-size:11px;font-weight:800;letter-spacing:.18em;text-transform:uppercase;color:#737686}
.hero-tools{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.segmented{display:flex;padding:4px;border-radius:14px;background:#f3f4f5}.segmented button{border:none;border-radius:10px;background:#e7e8e9;padding:10px 16px;font-size:12px;font-weight:700;color:#191c1d;cursor:pointer}.segmented button.active{background:#003fb1;color:#fff;box-shadow:none}.export-btn{border:none;border-radius:12px;background:#003fb1;box-shadow:none}.api-access-panel{display:flex;justify-content:space-between;align-items:center;gap:16px;padding:22px 24px;border-radius:18px;background:#fff;border:1px solid rgba(195,197,215,.18);box-shadow:0 4px 20px rgba(0,0,0,.02)}.api-access-copy h3{margin:0;font-family:Manrope,Inter,sans-serif;font-size:24px;font-weight:800;letter-spacing:-.03em;color:#191c1d}.api-access-copy p{margin:8px 0 0;font-size:13px;color:#5f6b84;word-break:break-all}.swagger-btn{border:none;border-radius:12px;background:#003fb1;box-shadow:none}.swagger-btn:hover{background:#1a56db}.swagger-btn.is-disabled{background:#b5c4ff;color:#fff;box-shadow:none}
.kpi-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:18px}.kpi-card{position:relative;overflow:hidden;border-radius:18px;padding:24px;background:#fff;border:1px solid rgba(195,197,215,.22);box-shadow:0 4px 20px rgba(0,0,0,.02)}.kpi-card::before{content:"";position:absolute;inset:0 auto auto 0;width:100%;height:4px;background:linear-gradient(90deg,#003fb1,#1a56db)}.kpi-card.accent-violet::before{background:linear-gradient(90deg,#4b5c92,#b5c4ff)}.kpi-card.accent-gold::before{background:linear-gradient(90deg,#d97706,#f59e0b)}.kpi-card.accent-green::before{background:linear-gradient(90deg,#0f9d58,#34c759)}.kpi-top{display:flex;justify-content:space-between;align-items:flex-start;gap:12px;margin-bottom:18px}.kpi-icon{width:42px;height:42px;border-radius:12px;display:grid;place-items:center}.kpi-icon.primary{background:rgba(0,63,177,.08);color:#003fb1}.kpi-icon.secondary{background:rgba(75,92,146,.08);color:#4b5c92}.kpi-icon.tertiary{background:rgba(173,59,0,.1);color:#852b00}.kpi-chip{padding:5px 10px;border-radius:999px;background:#dbe1ff;color:#003fb1;font-size:11px;font-weight:800;letter-spacing:.08em;text-transform:uppercase}.kpi-label{margin:0;font-size:12px;font-weight:800;letter-spacing:.12em;text-transform:uppercase;color:#434654}.kpi-value{margin-top:8px;font-family:Manrope,Inter,sans-serif;font-size:36px;line-height:1;font-weight:800;color:#191c1d}.kpi-note{margin:10px 0 0;font-size:13px;color:#5f6b84}
.dashboard-grid{display:grid;grid-template-columns:minmax(0,1.5fr) minmax(300px,.9fr);gap:24px;align-items:start}.left-column,.right-column{display:flex;flex-direction:column;gap:24px}.panel{border-radius:18px;padding:28px;background:#fff;border:1px solid rgba(195,197,215,.18);box-shadow:0 4px 20px rgba(0,0,0,.02)}.panel-header{display:flex;justify-content:space-between;align-items:flex-start;gap:16px;margin-bottom:24px}.panel-title{margin:0;font-family:Manrope,Inter,sans-serif;font-size:24px;font-weight:800;letter-spacing:-.03em;color:#191c1d}.panel-subtitle{margin:4px 0 0;font-size:13px;color:#5f6b84}.panel-action{border:none;background:#e7e8e9;display:inline-flex;align-items:center;gap:6px;font-size:12px;font-weight:800;color:#191c1d;cursor:pointer;padding:10px 14px;border-radius:12px}.panel-action:hover{background:#d9dadb}
.task-list{display:flex;flex-direction:column;gap:12px}.task-row{display:flex;justify-content:space-between;align-items:center;gap:16px;padding:16px;border-radius:16px;background:linear-gradient(180deg,#fbfdff 0%,#f4f8ff 100%);transition:transform .2s ease,background-color .2s ease}.task-row:hover{transform:translateY(-1px);background:#fff}.task-left{display:flex;align-items:center;gap:14px;min-width:0}.task-mark{width:40px;height:40px;border-radius:999px;display:grid;place-items:center;flex-shrink:0;font-size:12px;font-weight:800;letter-spacing:.08em}.task-mark.critical{background:rgba(173,59,0,.12);color:#ad3b00}.task-mark.stable{background:rgba(0,63,177,.1);color:#003fb1}.task-mark.pending{background:rgba(255,181,154,.22);color:#852a00}.task-title{margin:0;font-size:15px;font-weight:800;color:#191c1d}.task-desc{margin:6px 0 0;font-size:12px;color:#434654}.task-badge{flex-shrink:0;padding:6px 10px;border-radius:999px;font-size:10px;font-weight:800;letter-spacing:.12em;text-transform:uppercase}.task-badge.critical{background:#ffdad6;color:#93000a}.task-badge.stable{background:#dbe1ff;color:#003fb1}.task-badge.pending{background:#ffdbcf;color:#802a00}
.trend-legend{display:flex;gap:14px;flex-wrap:wrap;align-items:center;font-size:12px;font-weight:700;color:#5f6b84}.trend-legend span{display:inline-flex;align-items:center;gap:6px}.legend-dot{width:10px;height:10px;border-radius:999px}.legend-dot.admissions{background:#003fb1}.legend-dot.discharges{background:#b5c4ff}.trend-chart{position:relative;height:290px;border-radius:16px;overflow:hidden;background:#f8f9fa}.trend-chart::before{content:"";position:absolute;inset:0;background:linear-gradient(180deg,rgba(0,63,177,.08),transparent 60%);pointer-events:none}.trend-grid{position:absolute;inset:24px 18px 48px;display:flex;flex-direction:column;justify-content:space-between;pointer-events:none}.trend-grid span{border-top:1px solid rgba(115,118,134,.08)}.trend-bars{position:absolute;inset:24px 18px 48px;display:grid;grid-template-columns:repeat(7,minmax(0,1fr));gap:12px;align-items:end}.trend-slot{position:relative;height:100%;display:flex;align-items:flex-end;justify-content:center;gap:6px}.trend-admissions,.trend-discharges{width:14px;border-radius:999px 999px 0 0}.trend-admissions{background:linear-gradient(180deg,#003fb1,#1a56db);opacity:.85}.trend-discharges{background:linear-gradient(180deg,#dbe1ff,#b5c4ff)}.trend-labels{position:absolute;left:18px;right:18px;bottom:16px;display:grid;grid-template-columns:repeat(7,minmax(0,1fr));font-size:10px;font-weight:800;letter-spacing:.14em;text-transform:uppercase;color:#737686}
.timeline{position:relative;display:flex;flex-direction:column;gap:22px;padding-left:12px}.timeline-track{position:absolute;left:11px;top:6px;bottom:6px;width:2px;background:#d9dadb}.timeline-item{position:relative;padding-left:28px}.timeline-dot{position:absolute;left:1px;top:4px;width:22px;height:22px;border-radius:50%;border:4px solid #fff;box-shadow:0 0 0 1px rgba(0,0,0,.02)}.timeline-dot.primary{background:#003fb1}.timeline-dot.muted{background:#d9dadb}.timeline-meta{margin:0;font-size:10px;font-weight:800;letter-spacing:.14em;text-transform:uppercase}.timeline-meta.primary{color:#003fb1}.timeline-meta.muted{color:#737686}.timeline-title{margin:6px 0 0;font-size:14px;font-weight:800;color:#191c1d}.timeline-desc{margin:4px 0 0;font-size:12px;line-height:1.6;color:#434654}.ghost-button{width:100%;margin-top:24px;border:none;border-radius:14px;background:#e7e8e9;padding:14px 18px;font-size:11px;font-weight:800;letter-spacing:.18em;text-transform:uppercase;color:#191c1d;cursor:pointer;transition:background-color .2s ease}.ghost-button:hover{background:#d9dadb}
.status-stack{display:flex;flex-direction:column;gap:18px}.status-row{display:flex;flex-direction:column;gap:8px}.status-head{display:flex;justify-content:space-between;align-items:center;gap:10px;font-size:13px;color:#191c1d;font-weight:700}.status-head span{color:#5f6b84;font-weight:600}.status-bar{height:8px;border-radius:999px;overflow:hidden;background:#e7e8e9}.status-bar span{display:block;height:100%;border-radius:999px;background:linear-gradient(90deg,#003fb1,#1a56db)}.status-note{margin-top:20px;font-size:12px;line-height:1.6;color:#737686}
.docs-section{display:flex;flex-direction:column;gap:16px;padding-top:4px;border-top:1px solid rgba(195,197,215,.24)}.docs-header h3{margin:0;font-family:Manrope,Inter,sans-serif;font-size:22px;font-weight:800;letter-spacing:-.03em;color:#191c1d}.docs-header p{margin:6px 0 0;font-size:13px;color:#5f6b84}.docs-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:16px}.doc-card{border-radius:16px;padding:18px;background:linear-gradient(180deg,#fbfdff 0%,#f4f8ff 100%);border:1px solid rgba(195,197,215,.18)}.doc-card-head{display:flex;justify-content:space-between;align-items:center;gap:12px}.doc-card h4{margin:0;font-size:15px;font-weight:800;color:#191c1d}.doc-tag{flex-shrink:0;padding:5px 10px;border-radius:999px;background:#dbe1ff;color:#003fb1;font-size:11px;font-weight:800;letter-spacing:.12em;text-transform:uppercase;white-space:nowrap}.doc-desc{margin:12px 0 0;font-size:13px;line-height:1.6;color:#434654}.doc-meta{margin-top:10px;font-size:12px;color:#737686}.doc-link{margin-top:14px;background:#003fb1;border:none;box-shadow:none;color:#fff}.doc-link:hover{background:#1a56db;color:#fff}
.section-pill{flex-shrink:0;align-self:flex-start;padding:8px 12px;border-radius:999px;background:#dbe1ff;color:#003fb1;font-size:11px;font-weight:800;letter-spacing:.14em;text-transform:uppercase}.module-shell{display:flex;flex-direction:column;gap:18px}.module-shell-header{display:flex;justify-content:space-between;align-items:flex-end;gap:16px;padding:24px 28px;border-radius:18px;background:rgba(255,255,255,.88);border:1px solid rgba(195,197,215,.18);box-shadow:0 4px 20px rgba(0,0,0,.02)}.module-shell-header h2{margin:0;font-family:Manrope,Inter,sans-serif;font-size:30px;font-weight:800;letter-spacing:-.03em;color:#191c1d}.module-shell-header p{margin:8px 0 0;font-size:13px;color:#5f6b84}.module-shell-body{padding:24px 28px 0}
@media (max-width:1200px){.kpi-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.dashboard-grid{grid-template-columns:1fr}}
@media (max-width:900px){.admin-sidebar{position:relative;width:100%;height:auto;inset:auto;border-right:none;border-bottom:1px solid rgba(195,197,215,.18)}.admin-main{margin-left:0}.topbar{position:relative;flex-direction:column;align-items:stretch}.topbar-actions{justify-content:space-between;flex-wrap:wrap}.page-canvas{padding:20px}.hero-block{flex-direction:column;align-items:flex-start}}
@media (max-width:640px){.kpi-grid{grid-template-columns:1fr}.panel,.module-shell-header{padding:20px}.module-shell-body{padding:18px 20px 0}.hero-block h2{font-size:32px}.hero-block p{font-size:15px}.topbar{padding:16px 20px}.topbar-copy{align-items:flex-start;text-align:left}.topbar-divider{display:none}.logout-btn{width:100%;justify-content:center}.docs-header{flex-direction:column;align-items:flex-start}}
</style>
