<template>
  <div class="admin-entry">
    <div class="entry-glow glow-a"></div>
    <div class="entry-glow glow-b"></div>

    <main class="entry-shell pmr-fade-up">
      <header class="hero-card">
        <div>
          <p class="eyebrow">Dashboard Entry</p>
          <h1>病案管理后台</h1>
          <p class="hero-copy">
            这里是后台入口页，你可以快速跳转到工作台、用户管理、病案记录和统计中心。
          </p>
        </div>

        <el-button type="danger" plain class="logout-btn" @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
          退出登录
        </el-button>
      </header>

      <section class="status-card">
        <div class="profile-block">
          <p class="label">当前账号</p>
          <strong>{{ currentUserName }}</strong>
          <span>{{ currentRoleName }}</span>
        </div>
        <div class="status-copy">
          <p>权限状态</p>
          <strong>{{ isAdmin ? '管理员' : '受限账号' }}</strong>
          <span>登录后 token 与用户资料会一起保存在本地。</span>
        </div>
      </section>

      <section class="quick-grid">
        <article v-for="item in quickLinks" :key="item.title" class="quick-card" @click="goTo(item.path)">
          <div class="quick-icon" :class="item.tone">
            <el-icon>
              <component :is="item.icon" />
            </el-icon>
          </div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.description }}</p>
          <span>{{ item.path }}</span>
        </article>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { DataBoard, Document, Setting, SwitchButton, TrendCharts, User } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { clearSession, getCurrentUser, getUserDisplayName, getUserRoleName, isAdminUser } from '@/utils/session.js'

const router = useRouter()

const currentUser = computed(() => getCurrentUser())
const currentUserName = computed(() => getUserDisplayName() || currentUser.value?.username || '未登录')
const currentRoleName = computed(() => getUserRoleName() || currentUser.value?.roleCode || '未分配角色')
const isAdmin = computed(() => isAdminUser())

const quickLinks = [
  {
    title: '后台工作台',
    description: '查看系统概览、任务与运行状态。',
    path: '/admin-dashboard',
    icon: DataBoard,
    tone: 'tone-blue'
  },
  {
    title: '用户管理',
    description: '维护账号、角色和权限配置。',
    path: '/admin/users',
    icon: User,
    tone: 'tone-green'
  },
  {
    title: '病案管理',
    description: '进入病案列表、编辑和批量操作。',
    path: '/admin/crud',
    icon: Document,
    tone: 'tone-orange'
  },
  {
    title: '统计分析',
    description: '查看病案统计和趋势图表。',
    path: '/admin/statistics',
    icon: TrendCharts,
    tone: 'tone-cyan'
  },
  {
    title: '系统设置',
    description: '管理基础参数、日志与安全策略。',
    path: '/admin-dashboard',
    icon: Setting,
    tone: 'tone-slate'
  }
]

const goTo = (path) => {
  router.push(path)
}

const handleLogout = () => {
  ElMessageBox.confirm('确认退出登录吗？', '退出登录', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => {
      clearSession()
      router.push('/login')
    })
    .catch(() => {})
}
</script>

<style scoped>
.admin-entry {
  position: relative;
  min-height: 100vh;
  padding: 28px;
  overflow: hidden;
  background: radial-gradient(circle at top right, rgba(26, 86, 219, 0.08), transparent 28%),
    linear-gradient(160deg, #f8fbff 0%, #fbfdff 42%, #eef4ff 100%);
}

.entry-glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.glow-a {
  width: 520px;
  height: 520px;
  left: -180px;
  top: -220px;
  background: radial-gradient(circle at center, rgba(47, 111, 255, 0.18), transparent 68%);
}

.glow-b {
  width: 460px;
  height: 460px;
  right: -160px;
  bottom: -200px;
  background: radial-gradient(circle at center, rgba(20, 184, 166, 0.2), transparent 70%);
}

.entry-shell {
  position: relative;
  z-index: 1;
  width: min(1180px, 100%);
  margin: 0 auto;
  display: grid;
  gap: 18px;
}

.hero-card,
.status-card,
.quick-card {
  border: 1px solid rgba(195, 197, 215, 0.2);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(12px);
  box-shadow: 0 12px 30px rgba(24, 65, 134, 0.08);
}

.hero-card {
  padding: 28px;
  border-radius: 24px;
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
}

.eyebrow {
  margin: 0;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #003fb1;
}

.hero-card h1 {
  margin: 10px 0 10px;
  font-size: 36px;
  line-height: 1.2;
  color: #1f2b42;
}

.hero-copy {
  margin: 0;
  max-width: 640px;
  font-size: 15px;
  line-height: 1.7;
  color: #566887;
}

.logout-btn {
  flex-shrink: 0;
}

.status-card {
  border-radius: 20px;
  padding: 18px 20px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.profile-block .label,
.status-copy p {
  margin: 0;
  color: #7386a8;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.profile-block strong,
.status-copy strong {
  display: block;
  margin-top: 6px;
  font-size: 24px;
  color: #1f2b42;
}

.profile-block span,
.status-copy span {
  display: block;
  margin-top: 4px;
  color: #7386a8;
  font-size: 13px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.quick-card {
  border-radius: 18px;
  padding: 18px;
  cursor: pointer;
}

.quick-icon {
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

.tone-cyan {
  color: #0e7490;
  background: rgba(34, 211, 238, 0.18);
}

.tone-slate {
  color: #475569;
  background: rgba(148, 163, 184, 0.2);
}

.quick-card h3 {
  margin: 14px 0 8px;
  font-size: 18px;
  color: #1f2b42;
}

.quick-card p {
  margin: 0;
  color: #566887;
  line-height: 1.6;
  min-height: 52px;
  font-size: 14px;
}

.quick-card span {
  display: inline-block;
  margin-top: 14px;
  font-size: 12px;
  color: #7386a8;
}

@media (max-width: 1040px) {
  .quick-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .admin-entry {
    padding: 16px;
  }

  .hero-card {
    flex-direction: column;
  }

  .status-card,
  .quick-grid {
    grid-template-columns: 1fr;
  }

  .hero-card h1 {
    font-size: 30px;
  }
}
</style>
