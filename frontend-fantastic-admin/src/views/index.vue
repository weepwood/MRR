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
  {
    label: '总记录数',
    value: dashboard.value.overview?.totalRecords ?? '-',
    icon: 'i-ant-design:file-text-twotone',
    tone: '',
    note: '当前已纳入系统的影像记录',
  },
  {
    label: '总页数',
    value: dashboard.value.overview?.totalPages ?? '-',
    icon: 'i-ant-design:book-twotone',
    tone: 'mrr-metric-card--violet',
    note: '全部病案影像累计页数',
  },
  {
    label: '病案数',
    value: dashboard.value.uniqueBAHCount ?? '-',
    icon: 'i-ant-design:team-twotone',
    tone: 'mrr-metric-card--green',
    note: '按病案号去重后的数量',
  },
  {
    label: '影像类型',
    value: dashboard.value.overview?.byType?.length ?? '-',
    icon: 'i-ant-design:appstore-twotone',
    tone: 'mrr-metric-card--amber',
    note: '当前统计口径中的类型数量',
  },
])

const quickActions = [
  { label: '记录管理', icon: 'i-ant-design:database-twotone', path: '/records', color: '#409eff', perm: 'record:read' },
  { label: '统计分析', icon: 'i-ant-design:bar-chart-twotone', path: '/statistics', color: '#67c23a', perm: 'statistics:read' },
  { label: 'OSS 迁移', icon: 'i-ant-design:cloud-upload-twotone', path: '/oss-migration', color: '#e6a23c', perm: 'record:read' },
  { label: '影像档案袋', icon: 'i-ant-design:folder-open-twotone', path: '/archive', color: '#909399', perm: 'record:read' },
  { label: '系统监控', icon: 'i-ant-design:dashboard-twotone', path: '/monitoring', color: '#f56c6c', perm: 'system:read' },
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
  }
  finally {
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
  <div class="home-page mrr-page">
    <header class="home-header mrr-page-header">
      <div class="mrr-page-heading">
        <p class="eyebrow">
          Medical Record Repository
        </p>
        <h2 class="mrr-page-title">
          欢迎回来{{ userStore.profile?.displayName ? `，${userStore.profile.displayName}` : '' }}
        </h2>
        <p class="mrr-page-description">
          病案影像系统运行状况、核心数据和最近访问活动总览。
        </p>
      </div>
      <div class="mrr-page-actions">
        <el-button type="primary" :loading="loading" @click="loadData">
          <template #icon>
            <i class="i-ant-design:reload-twotone" />
          </template>
          刷新数据
        </el-button>
      </div>
    </header>

    <!-- 统计卡片 -->
    <section v-if="canViewStats" class="mrr-metric-grid">
      <el-card
        v-for="card in statsCards"
        :key="card.label"
        shadow="never"
        class="mrr-metric-card"
        :class="card.tone"
      >
        <div class="mrr-metric-card__icon">
          <i :class="card.icon" />
        </div>
        <div class="mrr-metric-card__body">
          <span class="mrr-metric-card__label">{{ card.label }}</span>
          <strong class="mrr-metric-card__value">{{ card.value }}</strong>
          <p class="mrr-metric-card__note">
            {{ card.note }}
          </p>
        </div>
      </el-card>
    </section>

    <el-row v-if="canViewStats || canViewHealth" :gutter="16" class="dashboard-grid">
      <!-- 近 30 天趋势 -->
      <el-col v-if="canViewStats" :xs="24" :lg="canViewHealth ? 16 : 24">
        <el-card shadow="never" class="dashboard-panel mrr-panel mrr-panel--flat">
          <template #header>
            <div class="card-header">
              <div>
                <strong class="panel-title">近 30 天记录趋势</strong>
                <p class="panel-description">每日新增影像记录数量</p>
              </div>
              <el-button text size="small" @click="goStatistics">
                查看详情
              </el-button>
            </div>
          </template>
          <div v-if="dashboard.recentTrend?.length" class="trend-chart">
            <div
              v-for="(d, i) in dashboard.recentTrend"
              :key="i"
              class="trend-bar"
              :style="{ height: `${Math.max(4, (d.recordCount ?? 0) / trendMax * 160)}px` }"
              :title="`${d.date}: ${d.recordCount} 条`"
            >
              <span class="trend-val">{{ d.recordCount }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无趋势数据" />
        </el-card>
      </el-col>

      <!-- 系统状态 + 快捷入口 -->
      <el-col v-if="canViewHealth" :xs="24" :lg="canViewStats ? 8 : 24">
        <div class="side-panels">
          <el-card shadow="never" class="mrr-panel mrr-panel--flat">
            <template #header>
              <div>
                <strong class="panel-title">系统状态</strong>
                <p class="panel-description">关键依赖实时健康状态</p>
              </div>
            </template>
            <div class="health-items">
              <div class="health-item">
                <span class="health-label"><i class="mrr-status-dot" :class="dbOk ? 'status-success' : 'status-danger'" />数据库</span>
                <el-tag :type="dbOk ? 'success' : 'danger'" size="small">
                  {{ dbStatus }}
                </el-tag>
              </div>
              <div class="health-item">
                <span class="health-label"><i class="mrr-status-dot" :class="memOk ? 'status-success' : 'status-warning'" />内存</span>
                <el-tag :type="memOk ? 'success' : 'warning'" size="small">
                  {{ memUsage }}
                </el-tag>
              </div>
            </div>
          </el-card>

          <el-card shadow="never" class="mrr-panel mrr-panel--flat">
            <template #header>
              <div>
                <strong class="panel-title">快捷入口</strong>
                <p class="panel-description">常用管理功能</p>
              </div>
            </template>
            <div class="quick-grid">
              <button
                v-for="action in visibleQuickActions"
                :key="action.label"
                type="button"
                class="quick-item"
                @click="navigate(action.path)"
              >
                <i :class="action.icon" :style="{ color: action.color }" />
                <span>{{ action.label }}</span>
              </button>
            </div>
          </el-card>
        </div>
      </el-col>
    </el-row>

    <!-- 用户病案访问情况 -->
    <el-card v-if="canViewAudit" shadow="never" class="mrr-panel mrr-panel--flat">
      <template #header>
        <div class="card-header">
          <div>
            <strong class="panel-title">用户病案访问情况</strong>
            <p class="panel-description">最近十条影像访问与下载记录</p>
          </div>
          <el-button text size="small" @click="router.push('/audit-images')">
            查看全部
          </el-button>
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
  min-width: 0;
}

.home-header {
  padding-block: 4px 8px;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 11px;
  font-weight: 700;
  color: var(--color-primary);
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

.dashboard-grid {
  row-gap: 16px;
}

.dashboard-panel {
  height: 100%;
}

.side-panels {
  display: grid;
  gap: 16px;
}

.card-header {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.panel-title {
  display: block;
  font-size: 14px;
  font-weight: 680;
  line-height: 1.4;
  color: var(--text-primary);
}

.panel-description {
  margin: 3px 0 0;
  font-size: 11px;
  line-height: 1.45;
  color: var(--text-tertiary);
}

/* 趋势图 */
.trend-chart {
  position: relative;
  display: flex;
  gap: 4px;
  align-items: flex-end;
  height: 190px;
  padding: 20px 8px 0;
  background-image: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent 39px,
    color-mix(in srgb, var(--divider) 70%, transparent) 40px
  );
  border-bottom: 1px solid var(--divider);
}

.trend-bar {
  position: relative;
  flex: 1;
  min-width: 4px;
  cursor: pointer;
  background: linear-gradient(180deg, color-mix(in srgb, var(--color-primary) 70%, white), var(--color-primary));
  border-radius: 4px 4px 1px 1px;
  box-shadow: 0 4px 10px color-mix(in srgb, var(--color-primary) 14%, transparent);
  transition: filter 160ms ease, transform 160ms ease;
}

.trend-bar:hover {
  filter: saturate(1.15);
  transform: translateY(-2px);
}

.trend-val {
  position: absolute;
  top: -17px;
  left: 50%;
  font-size: 10px;
  color: var(--text-secondary);
  white-space: nowrap;
  opacity: 0;
  transform: translateX(-50%);
  transition: opacity 160ms ease;
}

.trend-bar:hover .trend-val {
  opacity: 1;
}

/* 系统状态 */
.health-items {
  display: grid;
  gap: 4px;
}

.health-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 42px;
  padding: 8px 10px;
  background: var(--mrr-shell-surface-soft);
  border: 1px solid var(--mrr-shell-border);
  border-radius: 9px;
}

.health-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.status-success {
  color: var(--color-success);
}

.status-warning {
  color: var(--color-warning);
}

.status-danger {
  color: var(--color-danger);
}

/* 快捷入口 */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.quick-item {
  display: flex;
  flex-direction: column;
  gap: 7px;
  align-items: center;
  justify-content: center;
  min-height: 74px;
  padding: 10px 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  cursor: pointer;
  background: var(--mrr-shell-surface-soft);
  border: 1px solid var(--mrr-shell-border);
  border-radius: 10px;
  transition: border-color 160ms ease, box-shadow 160ms ease, color 160ms ease, transform 160ms ease;
}

.quick-item:hover,
.quick-item:focus-visible {
  color: var(--text-primary);
  border-color: color-mix(in srgb, var(--color-primary) 24%, var(--mrr-shell-border));
  box-shadow: var(--mrr-shell-shadow-sm);
  outline: none;
  transform: translateY(-1px);
}

.quick-item i {
  font-size: 22px;
}

/* 访问记录 */
.audit-table {
  overflow-x: auto;
}

.audit-row {
  display: grid;
  grid-template-columns: 100px 110px minmax(220px, 1fr) 120px 150px;
  gap: 8px;
  align-items: center;
  min-width: 760px;
  padding: 10px 6px;
  font-size: 13px;
  border-bottom: 1px solid var(--mrr-shell-border);
}

.audit-row:not(.audit-header):hover {
  background: color-mix(in srgb, var(--color-primary) 3%, var(--surface));
}

.audit-row.audit-header {
  padding: 6px 6px 10px;
  font-size: 11px;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  background: var(--mrr-shell-surface-soft);
  border-bottom-color: var(--mrr-shell-border-strong);
}

.audit-row:last-child {
  border-bottom: none;
}

.col-target {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (width <= 600px) {
  .quick-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .trend-chart {
    gap: 2px;
    padding-inline: 2px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .trend-bar,
  .trend-val,
  .quick-item {
    transition: none;
  }

  .trend-bar:hover,
  .quick-item:hover,
  .quick-item:focus-visible {
    transform: none;
  }
}
</style>
