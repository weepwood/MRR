<script setup lang="ts">
import type {
  NginxConfigTest,
  NginxLogTail,
  NginxManagedFile,
  NginxManagedStatus,
} from '@/api/modules/nginx-management'
import { ElMessage } from 'element-plus'
import { Activity, FileCheck2, FileCode2, FileText, RefreshCw, ServerCog, ShieldCheck } from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'
import {
  getNginxManagedFiles,
  getNginxManagedLogs,
  getNginxManagedStatus,
  testNginxManagedConfig,
} from '@/api/modules/nginx-management'
import { hasPermission } from '@/utils/session'

defineOptions({ name: 'NginxManagementPage' })

type Tab = 'config' | 'logs'
const canManage = computed(() => hasPermission('system:manage'))
const canViewLogs = computed(() => hasPermission('system:read') && hasPermission('log:read'))
const status = ref<NginxManagedStatus>()
const statusLoading = ref(false)
const filesLoading = ref(false)
const logsLoading = ref(false)
const checking = ref(false)
const files = ref<NginxManagedFile[]>([])
const lastTest = ref<NginxConfigTest>()
const logTail = ref<NginxLogTail>()
const logType = ref<'access' | 'error'>('access')
const logLineCount = ref(100)
const keyword = ref('')
const activeTab = ref<Tab>('config')

const ready = computed(() => status.value?.enabled && status.value?.supported && status.value?.installed)
const statusLabel = computed(() => {
  if (!status.value?.enabled) {
    return '未启用'
  }
  if (!status.value.supported) {
    return '平台不支持'
  }
  if (!status.value.installed) {
    return '未安装'
  }
  return ({
    RUNNING: '运行中',
    STOPPED: '已停止',
    OTHER: '状态异常',
    UNAVAILABLE: '无法读取',
  })[status.value.serviceState]
})
const statusType = computed(() => status.value?.serviceState === 'RUNNING' && ready.value
  ? 'success'
  : status.value?.enabled && status.value?.supported ? 'warning' : 'info')
const visibleLogs = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return (logTail.value?.lines ?? []).filter(line => !q || line.toLowerCase().includes(q))
})

function fmtTime(value?: string | null) {
  if (!value) {
    return '-'
  }
  const timestamp = new Date(value)
  return Number.isNaN(timestamp.getTime()) ? '-' : timestamp.toLocaleString('zh-CN')
}

function fmtSize(value?: number | null) {
  if (value == null) {
    return '-'
  }
  if (value < 1024) {
    return `${value} B`
  }
  if (value < 1024 ** 2) {
    return `${(value / 1024).toFixed(1)} KB`
  }
  return `${(value / 1024 ** 2).toFixed(1)} MB`
}

function message(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

async function loadStatus() {
  statusLoading.value = true
  try {
    status.value = await getNginxManagedStatus()
    if (status.value.enabled && canManage.value) {
      await loadFiles()
    }
  }
  catch (error: unknown) {
    ElMessage.error(message(error, '读取网关状态失败'))
  }
  finally {
    statusLoading.value = false
  }
}

async function loadFiles() {
  if (!canManage.value || !status.value?.enabled) {
    return
  }
  filesLoading.value = true
  try {
    files.value = await getNginxManagedFiles()
  }
  catch (error: unknown) {
    ElMessage.error(message(error, '读取配置文件清单失败'))
  }
  finally {
    filesLoading.value = false
  }
}

async function testConfiguration() {
  if (!ready.value || !canManage.value || checking.value) {
    return
  }
  checking.value = true
  try {
    lastTest.value = await testNginxManagedConfig()
    if (lastTest.value.valid) {
      ElMessage.success('Nginx 配置语法检查通过，未执行重载')
    }
    else {
      ElMessage.warning('Nginx 配置检查失败，未修改运行配置')
    }
  }
  catch (error: unknown) {
    ElMessage.error(message(error, 'Nginx 检查无法执行'))
  }
  finally {
    checking.value = false
  }
}

async function loadLogs() {
  if (!status.value?.enabled || !canViewLogs.value) {
    return
  }
  logsLoading.value = true
  try {
    logTail.value = await getNginxManagedLogs(logType.value, logLineCount.value)
  }
  catch (error: unknown) {
    ElMessage.error(message(error, '获取日志失败'))
  }
  finally {
    logsLoading.value = false
  }
}

watch(activeTab, (tab) => {
  if (tab === 'logs' && !logTail.value) {
    void loadLogs()
  }
})
watch(logType, () => {
  if (activeTab.value === 'logs') {
    void loadLogs()
  }
})
watch(logLineCount, () => {
  if (activeTab.value === 'logs') {
    void loadLogs()
  }
})
onMounted(() => {
  void loadStatus()
})
</script>

<template>
  <div class="nginx-management">
    <header class="page-heading">
      <div class="heading-copy">
        <div class="page-icon"><ServerCog :size="23" :stroke-width="1.7" /></div>
        <div>
          <h1>Nginx 管理</h1>
          <p>MRR 网关运行状态、配置检查与日志诊断</p>
        </div>
      </div>
      <el-button :loading="statusLoading" @click="loadStatus">
        <RefreshCw :size="15" />
        <span>刷新状态</span>
      </el-button>
    </header>

    <el-alert
      v-if="status && !status.enabled"
      type="info"
      :closable="false"
      show-icon
      title="Nginx Web 运维功能默认关闭"
      description="由服务器管理员设置 APP_OPERATIONS_NGINX_ENABLED=true 并重新启动 MRR 后端，才能读取受管网关信息。"
      class="notice"
    />
    <el-alert
      v-else-if="status && !status.supported"
      type="warning"
      :closable="false"
      show-icon
      title="当前不是受支持的 Windows Server 运行环境"
      description="此模块仅面向 MRR Windows 离线包随附的本机 Nginx，不控制其他机器或图片源。"
      class="notice"
    />
    <el-alert
      v-else-if="status && !status.installed"
      type="warning"
      :closable="false"
      show-icon
      title="未检测到受管 Nginx 安装"
      description="请确认本机 Nginx 和配置目录已按 Windows 离线包安装，并核对 APP_OPERATIONS_NGINX_ROOT。"
      class="notice"
    />

    <section v-loading="statusLoading" class="overview-grid">
      <article class="overview-card">
        <div class="card-label"><Activity :size="16" /> 网关状态</div>
        <div class="card-value"><el-tag :type="statusType" effect="light" round>{{ status ? statusLabel : '加载中' }}</el-tag></div>
        <div class="card-foot">{{ status?.serviceName || 'MRR-Gateway' }}</div>
      </article>
      <article class="overview-card">
        <div class="card-label"><ServerCog :size="16" /> Nginx 版本</div>
        <div class="card-value small">{{ status?.version || '-' }}</div>
        <div class="card-foot">Windows 原生服务</div>
      </article>
      <article class="overview-card">
        <div class="card-label"><FileCheck2 :size="16" /> 配置文件</div>
        <div class="card-value small">{{ status?.configurationPresent ? '已检测到' : '未检测到' }}</div>
        <div class="card-foot">上次刷新 {{ fmtTime(status?.lastCheckedAt) }}</div>
      </article>
    </section>

    <div class="workspace">
      <div class="workspace-title">
        <div>
          <h2>网关运维工作台</h2>
          <p>仅提供只读检查。启动、停止、重载与配置修改仍由服务器本地管理中心负责。</p>
        </div>
        <el-tag type="info" effect="plain"><ShieldCheck :size="13" /> 只读模式</el-tag>
      </div>

      <el-tabs v-model="activeTab" class="management-tabs">
        <el-tab-pane label="配置检查" name="config">
          <div class="config-toolbar">
            <div>
              <h3>配置状态</h3>
              <p>检查 Nginx 配置文件语法；通过不代表已经应用或业务请求全部正常。</p>
            </div>
            <el-button type="primary" :disabled="!ready || !canManage" :loading="checking" @click="testConfiguration">
              <FileCheck2 :size="15" />
              <span>运行 nginx -t</span>
            </el-button>
          </div>
          <el-alert
            v-if="!canManage"
            type="info"
            :closable="false"
            title="需要 system:manage 权限才能查看文件清单和执行配置校验"
            class="notice"
          />
          <el-alert
            v-if="lastTest"
            :type="lastTest.valid ? 'success' : 'warning'"
            :closable="false"
            :title="lastTest.message"
            :description="`耗时 ${lastTest.durationMs} ms · 未执行配置写入或重载`"
            class="notice"
          />
          <el-table v-if="canManage" v-loading="filesLoading" :data="files" class="file-table" empty-text="暂无可读取的配置文件信息">
            <el-table-column label="文件" min-width="210">
              <template #default="{ row }">
                <span class="filename"><FileCode2 :size="16" /> {{ row.name }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }"><el-tag :type="row.present ? 'success' : 'info'" size="small">{{ row.present ? '存在' : '缺失' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="大小" width="110">
              <template #default="{ row }">{{ fmtSize(row.bytes) }}</template>
            </el-table-column>
            <el-table-column label="修改时间" min-width="170">
              <template #default="{ row }">{{ fmtTime(row.modifiedAt) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="访问与错误日志" name="logs">
          <div v-if="!canViewLogs" class="empty-state">
            <FileText :size="28" />
            <p>需要同时拥有 system:read 和 log:read 权限</p>
          </div>
          <template v-else>
            <div class="log-toolbar">
              <el-select v-model="logType" class="type-select" aria-label="日志类型">
                <el-option label="访问日志" value="access" />
                <el-option label="错误日志" value="error" />
              </el-select>
              <el-select v-model="logLineCount" class="line-select" aria-label="读取行数">
                <el-option :value="50" label="最近 50 行" />
                <el-option :value="100" label="最近 100 行" />
                <el-option :value="200" label="最近 200 行" />
              </el-select>
              <el-input v-model="keyword" placeholder="仅筛选当前已加载日志" clearable class="search-input" />
              <el-button :loading="logsLoading" :disabled="!status?.enabled" @click="loadLogs">
                <RefreshCw :size="15" />
                <span>查询</span>
              </el-button>
            </div>
            <div v-loading="logsLoading" class="log-surface" role="log" aria-label="Nginx 日志">
              <div v-if="visibleLogs.length" class="log-lines">
                <div v-for="(line, index) in visibleLogs" :key="index" class="log-line">{{ line }}</div>
              </div>
              <div v-else class="log-empty">暂无日志记录</div>
            </div>
            <p class="log-note">
              单次最多读取 200 行 / 128 KB，敏感查询参数会脱敏。
              <span v-if="logTail?.truncated">当前日志已截断，仅展示尾部内容。</span>
            </p>
          </template>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style scoped>
.nginx-management {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
  padding-bottom: 28px;
}

.page-heading,
.heading-copy,
.card-label,
.workspace-title,
.config-toolbar,
.log-toolbar,
.filename {
  display: flex;
  align-items: center;
}

.page-heading,
.workspace-title,
.config-toolbar {
  gap: 16px;
  justify-content: space-between;
}

.heading-copy {
  gap: 14px;
}

.heading-copy h1 {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 650;
  line-height: 1.35;
}

.heading-copy p,
.workspace-title p,
.config-toolbar p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.page-icon {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 44px;
  height: 44px;
  color: var(--el-color-primary);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-light);
  border-radius: 13px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  min-height: 115px;
}

.overview-card,
.workspace {
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 14px;
}

.overview-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  padding: 18px 20px;
}

.card-label {
  gap: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.card-value {
  min-height: 25px;
  font-size: 24px;
  font-weight: 650;
}

.card-value.small {
  font-size: 19px;
  overflow-wrap: anywhere;
}

.card-foot {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.workspace {
  min-width: 0;
  padding: 22px 24px;
}

.workspace-title {
  align-items: flex-start;
  margin-bottom: 16px;
}

.workspace-title h2 {
  margin: 0 0 6px;
  font-size: 17px;
  font-weight: 650;
}

.workspace-title .el-tag {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.config-toolbar {
  align-items: flex-start;
  margin: 10px 0 18px;
}

.config-toolbar h3 {
  margin: 0 0 6px;
  font-size: 15px;
}

.notice {
  margin: 8px 0 16px;
}

.file-table {
  width: 100%;
}

.filename {
  gap: 8px;
  font-weight: 500;
}

.log-toolbar {
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}

.type-select {
  width: 128px;
}

.line-select {
  width: 135px;
}

.search-input {
  width: min(270px, 100%);
}

.log-surface {
  min-height: 230px;
  max-height: 530px;
  overflow: auto;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-light);
  border-radius: 9px;
}

.log-lines {
  padding: 10px 14px;
  font: 12px/1.8 ui-monospace, SFMono-Regular, Consolas, monospace;
}

.log-line {
  padding: 2px 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.log-empty,
.empty-state {
  display: grid;
  gap: 10px;
  place-items: center;
  min-height: 180px;
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}

.log-note {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

@media (max-width: 760px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }

  .page-heading,
  .workspace-title,
  .config-toolbar {
    flex-wrap: wrap;
    align-items: flex-start;
  }

  .workspace {
    padding: 16px;
  }

  .search-input {
    flex: 1 1 190px;
  }
}
</style>
