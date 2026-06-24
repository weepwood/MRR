<route lang="yaml">
meta:
  title: 主页
  icon: ant-design:home-twotone
</route>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { getDashboardData } from '@/api/modules/statistics'
import { getSystemHealth } from '@/api/modules/system'
import { searchImageAuditLogs } from '@/api/modules/logs'
import { hasPermission } from '@/utils/session'
import type { DashboardData, LogRecord } from '@/api/types'

defineOptions({ name: 'HomePage' })

const router = useRouter()
const userStore = useUserStore()

const dashboard = ref<DashboardData>({})
const health = ref<Record<string, any>>({})
const auditLogs = ref<LogRecord[]>([])
const loading = ref(true)

const canViewStats = computed(() => hasPermission('statistics:read'))
const canViewHealth = computed(() => hasPermission('system:read'))
const canViewAudit = computed(() => hasPermission('log:read'))

const statsCards = computed(() => [
  { label: '总记录数', value: dashboard.value.overview?.totalRecords ?? '-', icon: 'i-ant-design:file-text-twotone', color: '#409eff' },
  { label: '总页数', value: dashboard.value.overview?.totalPages ?? '-', icon: 'i-ant-design:book-twotone', color: '#67c23a' },
  { label: '病案数', value: dashboard.value.uniqueBAHCount ?? '-', icon: 'i-ant-design:team-twotone', color: '#e6a23c' },
  { label: '影像类型', value: dashboard.value.overview?.byType?.length ?? '-', icon: 'i-ant-design:appstore-twotone', color: '#909399' },
])

const quickActions = [
  { label: '记录管理', icon: 'i-ant-design:database-twotone', path: '/records', color: '#409eff', perm: 'record:read' },
  { label: '统计分析', icon: 'i-ant-design:bar-chart-twotone', path: '/statistics', color: '#67c23a', perm: 'statistics:read' },
  { label: 'OSS 迁移', icon: 'i-ant-design:cloud-upload-twotone', path: '/oss-migration', color: '#e6a23c', perm: 'record:read' },
  { label: '影像档案袋', icon: 'i-ant-design:folder-open-twotone', path: '/archive', color: '#909399', perm: 'record:read' },
  { label: '系统监控', icon: 'i-ant-design:dashboard-twotone', path: '/monitoring', color: '#f56c6c', perm: 'system:read' },
  { label: '测试中心', icon: 'i-ant-design:experiment-twotone', path: '/testing', color: '#8b5cf6', perm: 'test:read' },
]

const visibleQuickActions = computed(() => quickActions.filter(a => hasPermission(a.perm)))

const trendMax = computed(() => {
  if (!dashboard.value.recentTrend?.length) return 100
  return Math.max(...dashboard.value.recentTrend.map(d => d.recordCount ?? 0), 10)
})

const dbStatus = computed(() => {
  const db = health.value?.components?.database
  return db?.status === 'UP' ? '正常' : db?.status === 'DOWN' ? '异常' : '未知'
})
const dbOk = computed(() => dbStatus.value === '正常')
const memUsage = computed(() => health.value?.components?.memory?.usagePercent ?? '-')
const memOk = computed(() => health.value?.components?.memory?.status === 'UP')

async function loadData() {
  loading.value = true
  try {
    const tasks: Promise<any>[] = []
    const keys: ('dash' | 'health' | 'audit')[] = []
    if (canViewStats.value) {
      tasks.push(getDashboardData())
      keys.push('dash')
    }
    if (canViewHealth.value) {
      tasks.push(getSystemHealth())
      keys.push('health')
    }
    if (canViewAudit.value) {
      tasks.push(searchImageAuditLogs({ page: 1, size: 10 }))
      keys.push('audit')
    }
    const results = await Promise.allSettled(tasks)
    results.forEach((res, i) => {
      if (res.status !== 'fulfilled') { return }
      const key = keys[i]
      if (key === 'dash') { dashboard.value = (res.value as any)?.data ?? {} }
      if (key === 'health') { health.value = (res.value as any)?.data ?? {} }
      if (key === 'audit') {
        const payload = res.value as any
        auditLogs.value = payload?.data?.list ?? payload?.list ?? payload?.data ?? []
      }
    })
  } finally {
    loading.value = false
  }
}

function actionLabel(action?: string) {
  const labels: Record<string, string> = {
    VIEW_IMAGE: '查看图片',
    VIEW_OSS_IMAGE: '查看 OSS 图片',
    DOWNLOAD: '下载压缩包',
    LIST: '查询列表',
  }
  return labels[action ?? ''] ?? action ?? '其他'
}

function navigate(path: string) {
  router.push(path)
}

function goStatistics() {
  router.push('/statistics')
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="home-page">
    <div class="home-header">
      <div>
        <p class="eyebrow">Medical Record Repository</p>
        <h2>欢迎回来{{ userStore.profile?.displayName ? `，${userStore.profile.displayName}` : '' }}</h2>
        <p class="subtitle">病案影像系统运行状况总览</p>
      </div>
      <el-button type="primary" :loading="loading" @click="loadData">
        <template #icon><i class="i-ant-design:reload-twotone" /></template>
        刷新数据
      </el-button>
    </div>

    <!-- 统计卡片 -->
    <el-row v-if="canViewStats" :gutter="16">
      <el-col v-for="card in statsCards" :key="card.label" :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-inner">
            <div class="stat-icon" :style="{ background: card.color + '18', color: card.color }">
              <i :class="card.icon" />
            </div>
            <div class="stat-info">
              <p class="stat-value">{{ card.value }}</p>
              <p class="stat-label">{{ card.label }}</p>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row v-if="canViewStats || canViewHealth" :gutter="16">
      <!-- 近 30 天趋势 -->
      <el-col v-if="canViewStats" :span="canViewHealth ? 16 : 24">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>近 30 天记录趋势</span>
              <el-button text size="small" @click="goStatistics">查看详情</el-button>
            </div>
          </template>
          <div v-if="dashboard.recentTrend?.length" class="trend-chart">
            <div
              v-for="(d, i) in dashboard.recentTrend"
              :key="i"
              class="trend-bar"
              :style="{ height: Math.max(4, (d.recordCount ?? 0) / trendMax * 160) + 'px' }"
              :title="`${d.date}: ${d.recordCount} 条`"
            >
              <span class="trend-val">{{ d.recordCount }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无趋势数据" />
        </el-card>
      </el-col>

      <!-- 系统状态 + 快捷入口 -->
      <el-col v-if="canViewHealth" :span="canViewStats ? 8 : 24">
        <el-card shadow="never" class="mb-4">
          <template #header>
            <span>系统状态</span>
          </template>
          <div class="health-items">
            <div class="health-item">
              <span class="health-label">数据库</span>
              <el-tag :type="dbOk ? 'success' : 'danger'" size="small">{{ dbStatus }}</el-tag>
            </div>
            <div class="health-item">
              <span class="health-label">内存</span>
              <el-tag :type="memOk ? 'success' : 'warning'" size="small">{{ memUsage }}</el-tag>
            </div>
          </div>
        </el-card>

        <el-card shadow="never">
          <template #header>
            <span>快捷入口</span>
          </template>
          <div class="quick-grid">
            <button
              v-for="action in visibleQuickActions"
              :key="action.label"
              class="quick-item"
              @click="navigate(action.path)"
            >
              <i :class="action.icon" :style="{ color: action.color }" />
              <span>{{ action.label }}</span>
            </button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 用户病案访问情况 -->
    <el-card v-if="canViewAudit" shadow="never">
      <template #header>
        <div class="card-header">
          <span>用户病案访问情况</span>
          <el-button text size="small" @click="router.push('/audit-images')">查看全部</el-button>
        </div>
      </template>
      <div v-if="auditLogs.length" class="audit-table">
        <div class="audit-row audit-header">
          <span class="col-user">用户</span>
          <span class="col-action">操作</span>
          <span class="col-target">目标</span>
          <span class="col-ip">IP</span>
          <span class="col-time">时间</span>
        </div>
        <div v-for="(log, i) in auditLogs" :key="i" class="audit-row">
          <span class="col-user">{{ log.username || '-' }}</span>
          <span class="col-action">
            <el-tag :type="log.auditAction === 'DOWNLOAD' ? 'warning' : log.auditAction === 'VIEW_IMAGE' || log.auditAction === 'VIEW_OSS_IMAGE' ? 'success' : 'info'" size="small">
              {{ actionLabel(log.auditAction) }}
            </el-tag>
          </span>
          <span class="col-target font-mono text-sm">{{ log.auditTarget || log.requestUri || '-' }}</span>
          <span class="col-ip text-sm color-#64748b">{{ log.clientIp || '-' }}</span>
          <span class="col-time text-sm color-#64748b">{{ log.accessTime ? String(log.accessTime).slice(0, 19).replace('T', ' ') : '-' }}</span>
        </div>
      </div>
      <el-empty v-else description="暂无访问记录" />
    </el-card>
  </div>
</template>

<style scoped>
.home-page {
  display: grid;
  gap: 16px;
}

.home-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

h2 {
  margin: 0;
  font-size: 24px;
}

.subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}

/* 统计卡片 */
.stat-card {
  margin-bottom: 0;
}

.stat-inner {
  display: flex;
  gap: 14px;
  align-items: center;
}

.stat-icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  font-size: 22px;
  border-radius: 12px;
}

.stat-info {
  min-width: 0;
}

.stat-value {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  line-height: 1.2;
}

.stat-label {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--text-secondary);
}

/* 卡片头部 */
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 趋势图 */
.trend-chart {
  display: flex;
  gap: 3px;
  align-items: flex-end;
  height: 170px;
  padding-top: 8px;
}

.trend-bar {
  position: relative;
  flex: 1;
  min-width: 4px;
  cursor: pointer;
  background: linear-gradient(180deg, #409eff, #79bbff);
  border-radius: 3px 3px 0 0;
  transition: opacity 0.2s;
}

.trend-bar:hover {
  opacity: 0.8;
}

.trend-val {
  position: absolute;
  top: -16px;
  left: 50%;
  font-size: 10px;
  color: var(--text-secondary);
  white-space: nowrap;
  transform: translateX(-50%);
}

/* 系统状态 */
.health-items {
  display: grid;
  gap: 12px;
}

.health-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.health-label {
  font-size: 13px;
  color: var(--text-secondary);
}

/* 快捷入口 */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.quick-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  padding: 12px 4px;
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 10px;
  transition: all 0.2s;
}

.quick-item:hover {
  background: var(--surface-alt);
  border-color: var(--divider);
}

.quick-item i {
  font-size: 22px;
}

/* 访问记录 */
.audit-table {
  display: grid;
  gap: 0;
}

.audit-row {
  display: grid;
  grid-template-columns: 100px 110px 1fr 120px 150px;
  gap: 8px;
  align-items: center;
  padding: 8px 4px;
  font-size: 13px;
  border-bottom: 1px solid var(--surface-alt);
}

.audit-row.audit-header {
  padding: 4px 4px 8px;
  font-size: 11px;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  border-bottom: 2px solid var(--divider);
}

.audit-row:last-child {
  border-bottom: none;
}

.col-target {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
