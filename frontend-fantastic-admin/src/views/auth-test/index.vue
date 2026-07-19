<script setup lang="ts">
import type { AuthTestHistoryEvent, AuthTestHistoryItem } from './types'
import { computed, ref } from 'vue'
import { useUserStore } from '@/store/modules/user'
import ExternalArchiveTicketTester from './components/ExternalArchiveTicketTester.vue'
import JwtLoginTester from './components/JwtLoginTester.vue'
import ProtectedApiTester from './components/ProtectedApiTester.vue'

defineOptions({ name: 'AuthenticationApiTestPage' })

const userStore = useUserStore()
const activeTab = ref('external')
const history = ref<AuthTestHistoryItem[]>([])
let historySequence = 0

const apiBaseUrl = computed(() => import.meta.env.DEV ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL)
const permissionCount = computed(() => userStore.permissions.length)

function record(event: AuthTestHistoryEvent) {
  history.value.unshift({
    ...event,
    id: ++historySequence,
    requestedAt: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
  })
  history.value = history.value.slice(0, 30)
}
</script>

<template>
  <div class="auth-test-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">
          Authentication Playground
        </p>
        <h2>认证与外部访问测试台</h2>
        <p class="subtitle">
          测试内部 JWT、受保护 API 和外部影像票据。外部票据模块会先检查后端配置，再引导生成并部署 HMAC Secret。
        </p>
      </div>
      <el-tag type="warning" effect="plain">
        仅 user:manage 权限可见
      </el-tag>
    </header>

    <section class="metric-grid" aria-label="认证测试环境概览">
      <el-card shadow="never" class="metric-card">
        <span>当前账号</span>
        <strong>{{ userStore.profile.displayName || userStore.profile.username || userStore.account || '未登录' }}</strong>
        <small>{{ userStore.profile.roleName || userStore.profile.roleCode || '无角色信息' }}</small>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>当前权限</span>
        <strong>{{ permissionCount }}</strong>
        <small>后端按数据库当前权限实时校验</small>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>API Base URL</span>
        <strong class="base-url">{{ apiBaseUrl }}</strong>
        <small>测试台不允许请求外部域名</small>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <span>本页请求</span>
        <strong>{{ history.length }}</strong>
        <small>仅保存在页面内存，刷新后清空</small>
      </el-card>
    </section>

    <el-alert
      type="warning"
      show-icon
      :closable="false"
      title="生产 HMAC Secret 不应保存在前端。测试页中的 Secret 只存在于当前页面内存，正式 HIS/EMR 接入必须由外部系统后端签名。"
    />

    <el-tabs v-model="activeTab" class="test-tabs">
      <el-tab-pane name="external" label="外部影像票据">
        <ExternalArchiveTicketTester @record="record" />
      </el-tab-pane>
      <el-tab-pane name="login" label="内部登录与 JWT">
        <JwtLoginTester @record="record" />
      </el-tab-pane>
      <el-tab-pane name="request" label="受保护接口调试">
        <ProtectedApiTester @record="record" />
      </el-tab-pane>
      <el-tab-pane name="history" label="请求记录">
        <el-card shadow="never">
          <template #header>
            <div class="history-header">
              <div>
                <strong>本页请求记录</strong>
                <span>最多保留最近 30 条</span>
              </div>
              <el-button text @click="history = []">
                清空
              </el-button>
            </div>
          </template>
          <el-table :data="history" empty-text="尚未发送测试请求">
            <el-table-column prop="requestedAt" label="时间" width="100" />
            <el-table-column prop="name" label="场景" width="150" />
            <el-table-column prop="method" label="方法" width="90" />
            <el-table-column prop="path" label="路径" min-width="260" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status >= 200 && row.status < 300 ? 'success' : 'danger'" effect="plain">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="耗时" width="110">
              <template #default="{ row }">
                {{ row.durationMs }} ms
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.auth-test-page {
  display: grid;
  gap: 18px;
}

.page-header,
.history-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.eyebrow {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

h2 {
  margin: 0;
  font-size: 24px;
}

.subtitle {
  max-width: 860px;
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-card :deep(.el-card__body) {
  display: grid;
  gap: 5px;
}

.metric-card span,
.metric-card small,
.history-header span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.metric-card strong {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 18px;
  white-space: nowrap;
}

.base-url {
  font-size: 14px !important;
}

.history-header strong,
.history-header span {
  display: block;
}

.test-tabs :deep(.el-tabs__content) {
  overflow: visible;
}

@media (width <= 1100px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 720px) {
  .page-header,
  .history-header {
    flex-direction: column;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
