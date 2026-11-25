<script setup lang="ts">
import type { NginxLogTail, NginxRuntimeStatus } from '@/api/modules/operations'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getNginxLogTail, getNginxRuntime } from '@/api/modules/operations'
import AppError from '@/components/AppError/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { formatDateTime } from '@/utils/display-format'
import { hasPermission } from '@/utils/session'

const canRead = computed(() => hasPermission('system:read'))
const canLogs = computed(() => canRead.value && hasPermission('log:read'))
const runtime = ref<NginxRuntimeStatus>()
const tail = ref<NginxLogTail>()
const kind = ref<'access' | 'error'>('access')
const busy = ref(false)
const failed = ref(false)
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
  BUSY: '日志读取忙',
}
let generation = 0
let controller: AbortController | undefined
function clear() {
  generation++
  controller?.abort()
  controller = undefined
  runtime.value = undefined
  tail.value = undefined
  busy.value = false
  failed.value = false
}
async function load(mode: 'runtime' | 'logs') {
  if (!canRead.value || (mode === 'logs' && !canLogs.value) || busy.value) { return }
  const current = ++generation
  controller?.abort()
  controller = new AbortController()
  busy.value = true
  failed.value = false
  if (mode === 'logs') {
    tail.value = undefined
  }
  else { runtime.value = undefined }
  try {
    if (mode === 'runtime') {
      const result = await getNginxRuntime(controller.signal)
      if (current === generation) { runtime.value = result }
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
watch([canRead, canLogs], clear, { flush: 'sync' })
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
.runtime-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  margin-bottom: var(--mrr-space-2);
}
</style>
