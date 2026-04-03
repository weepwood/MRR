<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCurrentUser, getUserDisplayName, getUserRoleName, isAdminUser } from '@/utils/session'
import { applyMagneticEffect } from '@/utils/animations'
import gsap from 'gsap'

// 导入图标
import { User, Key, Tools, TrendCharts, Document, Monitor, Setting, Reading } from '@element-plus/icons-vue'

const router = useRouter()

const currentUser = computed(() => getCurrentUser())
const currentUserName = computed(() => getUserDisplayName() || currentUser.value?.username || '')
const currentRoleName = computed(() => getUserRoleName() || currentUser.value?.roleCode || '')
const isAdmin = computed(() => isAdminUser())
const accessSummary = computed(() => (isAdmin.value ? '拥有完整后台权限' : '拥有受限后台权限'))

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
    action: () => router.push('/users')
  },
  {
    title: '权限管理',
    description: '查看角色权限矩阵和当前账号可见范围。',
    badge: '权限视图',
    tone: 'tone-green',
    icon: Key,
    action: () => router.push('/permissions')
  },
  {
    title: '测试中心',
    description: '进入接口冒烟、压力测试和日志清理页面。',
    badge: '测试入口',
    tone: 'tone-cyan',
    icon: Tools,
    action: () => router.push('/testing')
  },
  {
    title: '日志管理',
    description: '查看系统日志、请求明细和审计记录。',
    badge: '日志入口',
    tone: 'tone-slate',
    icon: Document,
    action: () => router.push('/logs')
  },
  {
    title: '监控中心',
    description: '查看 CPU、内存、磁盘和系统运行状态。',
    badge: '监控入口',
    tone: 'tone-blue',
    icon: Monitor,
    action: () => router.push('/monitoring')
  },
  {
    title: '病案管理',
    description: '进入病案列表、编辑和批量操作。',
    badge: '业务入口',
    tone: 'tone-orange',
    icon: Document,
    action: () => router.push('/records')
  },
  {
    title: '统计分析',
    description: '查看病案统计、趋势图表和业务分布。',
    badge: '数据分析',
    tone: 'tone-blue',
    icon: TrendCharts,
    action: () => router.push('/statistics')
  },
  {
    title: '系统设置',
    description: '管理基础参数、日志与安全策略。',
    badge: '系统参数',
    tone: 'tone-slate',
    icon: Setting,
    action: () => router.push('/settings')
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

const handleFeatureClick = (item: any) => {
  if (typeof item.action === 'function') {
    item.action()
    return
  }
  ElMessage.info(`${item.title} 功能即将上线`)
}

const heroRef = ref<HTMLElement | null>(null)
const kpiGridRef = ref<HTMLElement | null>(null)
const featureGridRef = ref<HTMLElement | null>(null)

// 初始化动画
onMounted(() => {
  nextTick(() => {
    // 为英雄区块添加简单的淡入动画
    if (heroRef.value) {
      gsap.fromTo(heroRef.value, 
        { opacity: 0, y: 20 }, 
        { opacity: 1, y: 0, duration: 0.5 }
      )
    }
    
    // 为KPI卡片添加简单延迟动画
    const kpiItems = kpiGridRef.value?.querySelectorAll('.kpi-card')
    if (kpiItems) {
      gsap.fromTo(kpiItems, 
        { opacity: 0, y: 15 }, 
        { opacity: 1, y: 0, duration: 0.4, stagger: 0.05, delay: 0.2 }
      )
    }
    
    // 为功能卡片添加简单延迟动画和磁吸效果
    const featureItems = featureGridRef.value?.querySelectorAll('.feature-card')
    if (featureItems) {
      gsap.fromTo(featureItems, 
        { opacity: 0, y: 15 }, 
        { opacity: 1, y: 0, duration: 0.4, stagger: 0.05, delay: 0.3 }
      )

      // 为功能卡片应用磁吸效果
      featureItems.forEach(item => applyMagneticEffect(item, 0.03))
    }
  })
})

function openDocs() {
  window.open('/docs/index.html', '_blank', 'noopener,noreferrer')
}


</script>

<template>
  <div class="dashboard">
    <section ref="heroRef" class="hero-block">
      <div class="hero-copy">
        <p class="eyebrow">Clinical Sanctuary</p>
        <h2>欢迎进入后台管理中心</h2>
        <p>
          当前账号{{ accessSummary }}。你可以在这里直接进入用户、权限、测试、日志、监控、
          病案、统计和系统设置页面，所有功能都嵌入在同一个后台壳中。
        </p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" @click="router.push('/users')">
          <el-icon><User /></el-icon>
          用户管理
        </el-button>
        <el-button @click="router.push('/permissions')">
          <el-icon><Key /></el-icon>
          权限管理
        </el-button>
      </div>
    </section>

    <section ref="kpiGridRef" class="kpi-grid">
      <article
        v-for="card in dashboardCards"
        :key="card.label"
        class="kpi-card"
        :class="card.toneClass"
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

    <section ref="featureGridRef" class="feature-grid">
      <article
        v-for="item in featureCards"
        :key="item.title"
        class="feature-card magnetic-card"
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
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 32px;
  min-height: calc(100vh - var(--g-header-actual-height) - var(--g-tabbar-actual-height) - var(--g-toolbar-actual-height));
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
  gap: 24px;
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
  gap: 24px;
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
    transform 0.3s cubic-bezier(0.25, 0.8, 0.25, 1),
    box-shadow 0.3s cubic-bezier(0.25, 0.8, 0.25, 1),
    border-color 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

.feature-card:hover {
  transform: translateY(-5px) scale(1.02);
  box-shadow: 0 20px 40px rgba(24, 65, 134, 0.15);
  border-color: rgba(0, 63, 177, 0.3);
  z-index: 5;
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

@media (max-width: 720px) {
  .dashboard {
    padding: 20px;
  }

  .hero-block {
    grid-template-columns: 1fr;
  }

  .kpi-grid,
  .feature-grid {
    grid-template-columns: 1fr;
  }

  .hero-actions {
    width: 100%;
    justify-content: stretch;
  }

  .hero-actions :deep(.el-button) {
    width: 100%;
    justify-content: center;
  }
}
</style>
