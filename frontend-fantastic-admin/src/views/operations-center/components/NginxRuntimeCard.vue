<script setup lang="ts">
import type { NginxLogTail, NginxRuntimeStatus, WindowsGatewayStatus } from '@/api/modules/operations'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getNginxLogTail, getNginxRuntime, observeWindowsGateway } from '@/api/modules/operations'
import AppError from '@/components/AppError/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { formatDateTime } from '@/utils/display-format'
import { hasPermission } from '@/utils/session'

const canRead = computed(() => hasPermission('system:read'))
const canManage = computed(() => canRead.value && hasPermission('system:manage'))
const gateway = ref<WindowsGatewayStatus>()
const canLogs = computed(() => canRead.value && hasPermission('log:read'))
const runtime = ref<NginxRuntimeStatus>()
const tail = ref<NginxLogTail>()
const kind = ref<'access' | 'error'>('access')
const busy = ref(false)
const failed = ref(false)
const serviceLabels: Record<string, string> = {
  RUNNING: '运行中',
  STOPPED: '已停止',
  PAUSED: '已暂停',
  START_PENDING: '正在启动',
  STOP_PENDING: '正在停止',
  CONTINUE_PENDING: '正在继续',
  PAUSE_PENDING: '正在暂停',
  NOT_FOUND: '未找到固定服务',
  UNAVAILABLE: '无法核对',
}
const listenerLabels: Record<string, string> = {
  UNVERIFIED: '未核实本地进程身份',
  UNAVAILABLE: 'TCP 监听查询不可用',
  TRUNCATED: '监听数量超限，未返回部分结果',
  IDENTITY_CHANGED: '查询期间进程身份变化，请重新核对',
}
const labels: Record<string, string> = {
  DISABLED: '未启用',
  UNSUPPORTED_PLATFORM: '仅支持 Windows 本地网关',
  NOT_CONFIGURED: '未配置安装目录',
  PID_NOT_RUNNING: 'PID 对应进程未运行',
  PID_MISMATCH: 'PID 对应进程不是固定安装程序',
  PID_UNVERIFIABLE: 'PID 文件不可核对',
  PROCESS_UNVERIFIABLE: '无法确认本地进程',
  PROCESS_MATCHED: 'PID 对应本地安装程序正在运行',
  AVAILABLE: '已读取脱敏日志',
  LOG_UNAVAILABLE: '固定日志文件不可读',
  BUSY: '查询忙',
  TIMEOUT: '查询超时',
  INTERRUPTED: '查询中断',
  UNAVAILABLE: '查询不可用',
}
let generation = 0
let controller: AbortController | undefined
function clear() {
  generation++
  controller?.abort()
  controller = undefined
  runtime.value = undefined
  gateway.value = undefined
  tail.value = undefined
  busy.value = false
  failed.value = false
}
async function load(mode: 'runtime' | 'logs' | 'gateway') {
  if (!canRead.value || (mode === 'logs' && !canLogs.value) || (mode === 'gateway' && !canManage.value) || busy.value) { return }
  const current = ++generation
  controller?.abort()
  controller = new AbortController()
  busy.value = true
  failed.value = false
  if (mode === 'logs') {
    tail.value = undefined
  }
  else if (mode === 'gateway') { gateway.value = undefined }
  else { runtime.value = undefined }
  try {
    if (mode === 'runtime') {
      const result = await getNginxRuntime(controller.signal)
      if (current === generation) { runtime.value = result }
    }
    else if (mode === 'gateway') {
      const result = await observeWindowsGateway(controller.signal)
      if (current !== generation) { return }
      if (!Array.isArray(result.ports) || result.ports.length > 32 || result.ports.some(port => !Number.isInteger(port) || port < 1 || port > 65535)
        || (result.listenerState !== 'OBSERVED' && result.ports.length)) { throw new Error('Invalid gateway observation') }
      gateway.value = result
    }
    else {
      const result = await getNginxLogTail(kind.value, controller.signal)
      if (current !== generation) { return }
      if (result.kind !== kind.value || result.entries.length > 50) { throw new Error('Invalid log projection') }
      tail.value = result
    }
  }
  catch {
    if (current === generation) { failed.value = true }
  }
  finally {
    if (current === generation) { busy.value = false }
  }
}
watch([canRead, canLogs, canManage], clear, { flush: 'sync' })
watch(kind, clear, { flush: 'sync' })
watch(() => useUserStore().token, clear, { flush: 'sync' })
onBeforeUnmount(clear)
</script>

<template>
  <MrrSectionCard v-if="canRead" title="Nginx 运行观察与脱敏日志" description="仅按需观察固定安装 PID 与日志，不启动、重载或探测网络端口。进程匹配不代表服务健康。">
    <div class="runtime-actions">
      <el-button :loading="busy" @click="load('runtime')">
        核对本地进程
      </el-button>
      <el-tag v-if="runtime" type="info">
        {{ labels[runtime.state] ?? '未知状态' }}
      </el-tag>
      <span v-if="runtime">{{ formatDateTime(runtime.observedAt) }}</span>
    </div>
    <template v-if="canManage">
      <div class="runtime-actions">
        <el-button :loading="busy" @click="load('gateway')">
          核对 Windows 服务与 TCP 监听
        </el-button>
      </div>
      <div v-if="gateway" role="status" class="gateway-observation">
        <p v-if="gateway.state !== 'OBSERVED'">
          {{ labels[gateway.state] ?? '查询不可用' }}
        </p>
        <template v-else>
          <p>MRR-Gateway 服务：{{ serviceLabels[gateway.serviceState] ?? '未知状态' }}</p>
          <p>已核对 Nginx 进程的本地 TCP 监听：{{ gateway.listenerState === 'OBSERVED' ? (gateway.ports.length ? gateway.ports.join('、') : '本次未观察到监听') : (listenerLabels[gateway.listenerState] ?? '未知状态') }}</p>
        </template>
        <p>观察时间 {{ formatDateTime(gateway.observedAt) }}。服务状态与进程监听为独立观察，不证明两者属于同一启动链或服务可访问。</p>
      </div>
    </template>
    <template v-if="canLogs">
      <div class="runtime-actions">
        <el-radio-group v-model="kind" :disabled="busy" aria-label="Nginx 日志类型">
          <el-radio-button value="access">
            访问摘要
          </el-radio-button>
          <el-radio-button value="error">
            错误级别
          </el-radio-button>
        </el-radio-group>
        <el-button :loading="busy" @click="load('logs')">
          读取脱敏日志
        </el-button>
      </div>
      <p v-if="tail">
        {{ labels[tail.state] ?? '未知状态' }} · {{ formatDateTime(tail.observedAt) }}
      </p>
      <template v-if="tail?.state === 'AVAILABLE'">
        <p>最多读取末尾 64 KiB、最近 50 条完整行；{{ tail.truncated ? '已截断；' : '' }}无法解析 {{ tail.omitted }} 条。错误时间为服务器本地时间，不含时区。仅显示白名单字段，不显示请求路径、IP、请求头及错误正文。</p>
        <el-table :data="tail.entries" max-height="360" empty-text="暂无可展示的日志摘要">
          <el-table-column prop="time" label="日志时间" min-width="190" />
          <el-table-column v-if="kind === 'access'" prop="method" label="方法" width="100" />
          <el-table-column v-if="kind === 'access'" prop="status" label="状态码" width="100" />
          <el-table-column v-if="kind === 'access'" prop="requestSeconds" label="耗时（秒）" min-width="100" />
          <el-table-column v-if="kind === 'error'" prop="severity" label="级别" width="110" />
        </el-table>
      </template>
    </template>
    <AppError v-if="failed" message="运行观察或日志读取失败，请重新查询" />
  </MrrSectionCard>
</template>

<style scoped>
.gateway-observation {
  padding: var(--mrr-space-2);
  margin-bottom: var(--mrr-space-2);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
}

.runtime-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  margin-bottom: var(--mrr-space-2);
}
</style>
