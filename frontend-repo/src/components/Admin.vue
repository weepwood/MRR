<template>
  <div class="admin-page">
    <div class="hero-glow glow-left"></div>
    <div class="hero-glow glow-right"></div>

    <main class="admin-shell pmr-fade-up">
      <header class="hero-card">
        <div class="hero-main">
          <p class="hero-tag">Dashboard Entry</p>
          <h1>病案翻拍后台管理</h1>
          <p class="hero-subtitle">
            统一入口，快速进入系统管理、数据统计和文档中心。
          </p>
        </div>
        <el-button class="hero-logout" type="danger" plain @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
          退出登录
        </el-button>
      </header>

      <section class="overview-grid">
        <article v-for="item in overviewCards" :key="item.title" class="overview-item">
          <p>{{ item.title }}</p>
          <strong>{{ item.value }}</strong>
          <span>{{ item.note }}</span>
        </article>
      </section>

      <section class="feature-grid">
        <el-card
          v-for="item in featureCards"
          :key="item.title"
          class="feature-card"
          shadow="never"
          @click="handleFeatureClick(item)"
        >
          <div class="feature-icon" :class="item.tone">
            <el-icon>
              <component :is="item.icon" />
            </el-icon>
          </div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.description }}</p>
          <div class="feature-footer">
            <span>{{ item.badge }}</span>
          </div>
        </el-card>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  DataBoard,
  Document,
  Reading,
  Setting,
  SwitchButton,
  TrendCharts,
  User,
} from '@element-plus/icons-vue'

const router = useRouter()

const overviewCards = [
  { title: '当前环境', value: 'Production', note: '运行状态稳定' },
  { title: '今日任务', value: '6 项', note: '2 项待处理' },
  { title: '系统版本', value: 'v2.0', note: '设计已升级' },
]

const featureCards = [
  {
    title: '管理面板',
    description: '进入完整后台控制台，查看仪表盘、用户、日志与系统监控。',
    badge: '推荐入口',
    tone: 'tone-blue',
    icon: DataBoard,
    action: () => router.push('/admin-dashboard'),
  },
  {
    title: '病案统计',
    description: '查看病案趋势统计与图表分析，快速掌握业务变化。',
    badge: '数据分析',
    tone: 'tone-green',
    icon: TrendCharts,
    action: () => router.push('/admin/statistics'),
  },
  {
    title: '文档中心',
    description: '打开项目文档站，查看规范、部署说明与版本记录。',
    badge: '团队协作',
    tone: 'tone-orange',
    icon: Reading,
    action: () => window.open('/docs/index.html', '_blank', 'noopener,noreferrer'),
  },
  {
    title: '用户管理',
    description: '维护用户角色与权限策略，支持后续扩展审批流程。',
    badge: '规划中',
    tone: 'tone-pink',
    icon: User,
  },
  {
    title: '病案管理',
    description: '统一查看病案内容、元数据和处理状态，便于问题追踪。',
    badge: '规划中',
    tone: 'tone-cyan',
    icon: Document,
  },
  {
    title: '系统设置',
    description: '管理日志级别、会话时长和系统参数，保障平台稳定。',
    badge: '规划中',
    tone: 'tone-slate',
    icon: Setting,
  },
]

const handleFeatureClick = (item) => {
  if (typeof item.action === 'function') {
    item.action()
    return
  }
  ElMessage.info(`${item.title}功能即将开放`)
}

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '退出确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      localStorage.removeItem('token')
      router.push('/login')
      ElMessage.success('已退出登录')
    })
    .catch(() => {})
}
</script>

<style scoped>
.admin-page {
  position: relative;
  min-height: 100vh;
  padding: 28px;
  overflow: hidden;
}

.hero-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(2px);
  pointer-events: none;
}

.glow-left {
  width: 540px;
  height: 540px;
  left: -170px;
  top: -220px;
  background: radial-gradient(circle at center, rgba(47, 111, 255, 0.2), transparent 68%);
}

.glow-right {
  width: 480px;
  height: 480px;
  right: -150px;
  bottom: -220px;
  background: radial-gradient(circle at center, rgba(20, 184, 166, 0.23), transparent 70%);
}

.admin-shell {
  position: relative;
  z-index: 1;
  width: min(1180px, 100%);
  margin: 0 auto;
  display: grid;
  gap: 18px;
}

.hero-card {
  padding: 26px 28px;
  border-radius: 22px;
  border: 1px solid rgba(255, 255, 255, 0.65);
  background: linear-gradient(120deg, rgba(31, 84, 214, 0.95) 0%, rgba(47, 111, 255, 0.92) 56%, rgba(20, 184, 166, 0.86) 100%);
  box-shadow: var(--pmr-shadow-surface-lg);
  color: #fff;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.hero-tag {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  opacity: 0.85;
}

.hero-main h1 {
  margin: 8px 0 10px;
  font-size: 34px;
  line-height: 1.2;
}

.hero-subtitle {
  margin: 0;
  max-width: 560px;
  font-size: 15px;
  line-height: 1.7;
  opacity: 0.9;
}

.hero-logout {
  margin-top: 4px;
  border-color: rgba(255, 255, 255, 0.4);
  color: #ffffff;
  background: rgba(255, 255, 255, 0.14);
  backdrop-filter: blur(6px);
}

.hero-logout:hover {
  background: rgba(255, 255, 255, 0.22);
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.overview-item {
  border-radius: 16px;
  border: 1px solid #dce8fb;
  background: rgba(255, 255, 255, 0.86);
  padding: 16px 18px;
  box-shadow: 0 8px 20px rgba(24, 65, 134, 0.1);
}

.overview-item p {
  margin: 0;
  color: #5f7090;
  font-size: 13px;
}

.overview-item strong {
  display: block;
  margin-top: 6px;
  color: #1f2b42;
  font-size: 28px;
  line-height: 1.2;
}

.overview-item span {
  display: block;
  margin-top: 4px;
  color: #7386a8;
  font-size: 12px;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.feature-card {
  cursor: pointer;
  transition: transform var(--pmr-motion-duration-normal) var(--pmr-motion-ease-standard), box-shadow var(--pmr-motion-duration-normal) var(--pmr-motion-ease-standard), border-color var(--pmr-motion-duration-normal) var(--pmr-motion-ease-standard);
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

@media (max-width: 1040px) {
  .feature-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .admin-page {
    padding: 16px;
  }

  .hero-card {
    padding: 20px;
    flex-direction: column;
    align-items: stretch;
  }

  .hero-main h1 {
    font-size: 28px;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }

  .feature-grid {
    grid-template-columns: 1fr;
  }
}
</style>
