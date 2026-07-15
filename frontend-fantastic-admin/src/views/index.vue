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
import { getImageAuditAnalytics, searchImageAuditLogs } from '@/api/modules/logs'
import { hasPermission } from '@/utils/session'
import type { DashboardData, ImageAuditCountItem, LogRecord } from '@/api/types'

defineOptions({ name: 'HomePage' })

const router = useRouter()
const userStore = useUserStore()

const dashboard = ref<DashboardData>({})
const health = ref<Record<string, any>>({})
const auditLogs = ref<LogRecord[]>([])
const topUsers = ref<ImageAuditCountItem[]>([])
const timelineVisible = ref(false)
const timelineLoading = ref(false)
const timelineUsername = ref('')
const userTimeline = ref<LogRecord[]>([])
const expandedTimelineGroups = ref<string[]>([])
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
  { label: '影像档案袋', icon: 'i-ant-design:folder-open-twotone', path: '/archive/embed', color: '#909399', perm: 'record:read' },
  { label: '系统监控', icon: 'i-ant-design:dashboard-twotone', path: '/monitoring', color: '#f56c6c', perm: 'system:read' },

]

const visibleQuickActions = computed(() => quickActions.filter(a => hasPermission(a.perm)))

const dbStatus = computed(() => {
  const db = health.value?.components?.database
  return db?.status === 'UP' ? '正常' : db?.status === 'DOWN' ? '异常' : '未知'
})
const dbOk = computed(() => dbStatus.value === '正常')
const memUsage = computed(() => health.value?.components?.memory?.usagePercent ?? '-')
const memOk = computed(() => health.value?.components?.memory?.status === 'UP')
const timelineGroups = computed(() => {
  const groups = new Map<string, LogRecord[]>()
  userTimeline.value.forEach((log) => {
    const key = log.auditAction || 'OTHER'
    const logs = groups.get(key) ?? []
    logs.push(log)
    groups.set(key, logs)
  })
  return Array.from(groups, ([key, logs]) => ({ key, label: actionLabel(key), logs }))
})

function formatDateTime(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function getRecentAuditRange() {
  const end = new Date()
  const start = new Date(end)
  start.setDate(start.getDate() - 30)
  return { startTime: formatDateTime(start), endTime: formatDateTime(end) }
}

async function loadData() {
  loading.value = true
  try {
    const tasks: Promise<any>[] = []
    const keys: ('dash' | 'health' | 'audit' | 'topUsers')[] = []
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
      tasks.push(getImageAuditAnalytics(getRecentAuditRange()))
      keys.push('topUsers')
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
      if (key === 'topUsers') {
        topUsers.value = (res.value as any)?.data?.topUsers ?? []
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

async function showUserTimeline(user: ImageAuditCountItem) {
  timelineUsername.value = user.label
  timelineVisible.value = true
  timelineLoading.value = true
  userTimeline.value = []
  expandedTimelineGroups.value = []
  try {
    const { startTime, endTime } = getRecentAuditRange()
    const response = await searchImageAuditLogs({
      username: user.label,
      startTime,
      endTime,
      page: 1,
      size: 100,
    })
    userTimeline.value = (response as any)?.data?.list ?? []
  }
  finally {
    timelineLoading.value = false
  }
}

function formatLogTime(value?: string) {
  return value ? String(value).slice(0, 19).replace('T', ' ') : '-'
}

function toggleTimelineGroup(key: string) {
  expandedTimelineGroups.value = expandedTimelineGroups.value.includes(key)
    ? expandedTimelineGroups.value.filter(item => item !== key)
    : [...expandedTimelineGroups.value, key]
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
      <!-- 高频访问用户 -->
      <el-col v-if="canViewAudit" :span="canViewHealth ? 16 : 24">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>近 30 天高频访问用户</span>
              <el-button text size="small" @click="router.push('/audit-images')">查看审计</el-button>
            </div>
          </template>
          <div v-if="topUsers.length" class="top-users">
            <div
              v-for="(user, i) in topUsers"
              :key="user.label"
              class="top-user"
              role="button"
              tabindex="0"
              @click="showUserTimeline(user)"
              @keydown.enter="showUserTimeline(user)"
            >
              <span class="top-user-rank">{{ i + 1 }}</span>
              <span class="top-user-name">{{ user.label || '-' }}</span>
              <span class="top-user-count">{{ user.count }} 次访问</span>
              <i class="i-ant-design:right-outlined top-user-arrow" />
            </div>
          </div>
          <el-empty v-else description="近 30 天暂无访问用户数据" />
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

    <el-dialog v-model="timelineVisible" :title="`${timelineUsername} 的操作时间线`" width="min(720px, 92vw)">
      <el-skeleton v-if="timelineLoading" :rows="5" animated />
      <el-empty v-else-if="!userTimeline.length" description="该用户近 30 天暂无操作记录" />
      <div v-else class="user-timeline">
        <div v-for="group in timelineGroups" :key="group.key" class="timeline-group">
          <button
            class="timeline-group-header"
            :aria-expanded="expandedTimelineGroups.includes(group.key)"
            @click="toggleTimelineGroup(group.key)"
          >
            <i :class="expandedTimelineGroups.includes(group.key) ? 'i-ant-design:down-outlined' : 'i-ant-design:right-outlined'" />
            <span>{{ group.label }}</span>
            <el-tag size="small" type="info">{{ group.logs.length }} 次</el-tag>
          </button>
          <div v-if="expandedTimelineGroups.includes(group.key)" class="timeline-group-children">
            <div v-for="(log, index) in group.logs" :key="log.id ?? index" class="timeline-entry">
              <div>
                <span class="timeline-time">{{ formatLogTime(log.accessTime) }}</span>
                <span class="timeline-target">{{ log.auditTarget || log.requestUri || '-' }}</span>
              </div>
              <span class="timeline-meta">{{ log.clientIp || '-' }} · {{ log.responseStatus ?? '-' }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
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

/* 高频访问用户 */
.top-users {
  display: grid;
  gap: 8px;
}

.top-user {
  display: grid;
  grid-template-columns: 28px 1fr auto 18px;
  gap: 10px;
  align-items: center;
  padding: 11px 12px;
  cursor: pointer;
  border: 1px solid var(--divider);
  border-radius: 10px;
  transition: background .2s, border-color .2s;
}

.top-user:hover,
.top-user:focus-visible {
  background: var(--surface-alt);
  border-color: var(--el-color-primary-light-5);
  outline: none;
}

.top-user-rank {
  color: var(--el-color-primary);
  font-weight: 800;
  text-align: center;
}

.top-user-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.top-user-count,
.timeline-meta {
  color: var(--text-secondary);
  font-size: 12px;
}

.top-user-arrow {
  color: var(--text-secondary);
}

.user-timeline {
  max-height: 55vh;
  padding: 8px 12px 8px 4px;
  overflow-y: auto;
}

.timeline-group {
  border: 1px solid var(--divider);
  border-radius: 10px;
}

.timeline-group + .timeline-group {
  margin-top: 8px;
}

.timeline-group-header {
  display: grid;
  grid-template-columns: 18px 1fr auto;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 11px 12px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.timeline-group-header:hover {
  background: var(--surface-alt);
}

.timeline-entry {
  display: grid;
  gap: 7px;
  padding: 10px 12px 10px 34px;
  border-top: 1px solid var(--divider);
}

.timeline-time {
  margin-right: 10px;
  color: var(--text-secondary);
  font-size: 12px;
}

.timeline-target {
  word-break: break-all;
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
