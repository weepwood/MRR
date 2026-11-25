<script setup lang="ts">
import type { NginxConfigCheck, NginxConfigStatus, NginxListenerHints, NginxVersion } from '@/api/modules/operations'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { checkNginxConfig, getNginxConfigStatus, getNginxListenerHints, readNginxVersion } from '@/api/modules/operations'
import AppError from '@/components/AppError/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { formatDateTime } from '@/utils/display-format'
import { hasPermission } from '@/utils/session'

const status = ref<NginxConfigStatus>()
const result = ref<NginxConfigCheck>()
const version = ref<NginxVersion>()
const hints = ref<NginxListenerHints>()
const busy = ref(false)
const failed = ref(false)
const checking = ref(false)
let generation = 0
let controller: AbortController | undefined
const canRead = computed(() => hasPermission('system:read'))
const canManage = computed(() => canRead.value && hasPermission('system:manage'))
const statusLabels: Record<string, string> = {
  DISABLED: '配置检查未启用',
  UNSUPPORTED_PLATFORM: '仅支持 Windows 本地网关',
  NOT_CONFIGURED: '未配置本地安装目录',
  INSTALLATION_UNAVAILABLE: '固定安装文件不可用',
  AVAILABLE: '安装文件可读，尚不代表网关健康',
  BUSY: '已有校验在执行',
  PASSED: '配置语法校验通过',
  FAILED: '配置语法校验失败',
  TIMEOUT: '校验超时',
  INTERRUPTED: '校验中断',
  UNAVAILABLE: '无法读取或执行检查',
  OUTPUT_UNRECOGNIZED: '安装程序版本输出无法确认',
  CONFIG_TOO_LARGE: '配置超出文本核对上限',
  CONFIG_TOO_COMPLEX: '配置提示数量超限',
  TEXT_HINTS: '已读取配置文本提示',
}

function clear() {
  generation++
  controller?.abort()
  controller = undefined
  status.value = undefined
  result.value = undefined
  version.value = undefined
  hints.value = undefined
  busy.value = false
  checking.value = false
  failed.value = false
}

async function load() {
  if (!canRead.value || checking.value) { return }
  clear()
  const current = generation
  controller = new AbortController()
  busy.value = true
  try {
    const next = await getNginxConfigStatus(controller.signal)
    if (current === generation) { status.value = next }
  }
  catch {
    if (current === generation) { failed.value = true }
  }
  finally {
    if (current === generation) { busy.value = false }
  }
}

async function check() {
  if (!canManage.value || status.value?.state !== 'AVAILABLE' || checking.value || busy.value) { return }
  const current = generation
  controller = new AbortController()
  checking.value = true
  result.value = undefined
  failed.value = false
  try {
    const next = await checkNginxConfig(controller.signal)
    if (current === generation) { result.value = next }
  }
  catch {
    if (current === generation) { failed.value = true }
  }
  finally {
    if (current === generation) { checking.value = false }
  }
}

async function observe(mode: 'version' | 'hints') {
  if (!canRead.value || (mode === 'version' && !canManage.value) || busy.value || checking.value || status.value?.state !== 'AVAILABLE') { return }
  const current = ++generation
  controller?.abort()
  controller = new AbortController()
  checking.value = true
  failed.value = false
  if (mode === 'version') {
    version.value = undefined
  }
  else { hints.value = undefined }
  try {
    if (mode === 'version') {
      const next = await readNginxVersion(controller.signal)
      if (current !== generation) { return }
      if (next.state === 'AVAILABLE' && !/^\d{1,4}\.\d{1,4}\.\d{1,4}$/.test(next.version ?? '')) { throw new Error('Invalid version') }
      version.value = next
    }
    else {
      const next = await getNginxListenerHints(controller.signal)
      if (current !== generation) { return }
      if (next.ports.length > 32 || next.ports.some(port => !Number.isInteger(port) || port < 1 || port > 65535)) { throw new Error('Invalid ports') }
      hints.value = next
    }
  }
  catch {
    if (current === generation) { failed.value = true }
  }
  finally {
    if (current === generation) { checking.value = false }
  }
}

watch([canRead, canManage], () => {
  clear()
  if (canRead.value) { void load() }
}, { immediate: true, flush: 'sync' })
watch(() => useUserStore().token, clear, { flush: 'sync' })
onBeforeUnmount(clear)
</script>

<template>
  <MrrSectionCard v-if="canRead" title="Nginx 配置检查" description="仅固定本地安装路径与语法校验；不修改配置、不重载或启停服务。">
    <div class="nginx-actions">
      <el-tag v-if="status" :type="status.state === 'AVAILABLE' ? 'success' : 'info'">
        {{ statusLabels[status.state] }}
      </el-tag>
      <el-button :loading="busy" :disabled="checking" @click="load">
        刷新配置状态
      </el-button>
      <el-button v-if="canManage" :loading="checking" :disabled="busy || checking || status?.state !== 'AVAILABLE'" @click="check">
        校验配置语法
      </el-button>
    </div>
    <div class="nginx-actions">
      <el-button v-if="canManage" :disabled="busy || checking || status?.state !== 'AVAILABLE'" @click="observe('version')">
        读取安装版本
      </el-button>
      <el-button :disabled="busy || checking || status?.state !== 'AVAILABLE'" @click="observe('hints')">
        核对监听配置文本
      </el-button>
    </div>
    <p v-if="version">
      {{ version.state === 'AVAILABLE' ? `安装程序版本 Nginx ${version.version}` : (statusLabels[version.state] ?? '版本未知') }} · {{ formatDateTime(version.observedAt) }}。不代表当前运行进程版本。
    </p>
    <p v-if="hints">
      {{ hints.state === 'TEXT_HINTS' ? (hints.ports.length ? `独立数值 listen 文本提示：${hints.ports.join('、')}` : '未匹配独立数值 listen 文本') : (statusLabels[hints.state] ?? '配置文本不可用') }} · {{ formatDateTime(hints.observedAt) }}。不解析 include、地址或变量，不是生效监听配置或端口状态。
    </p>
    <p v-if="status?.configBytes != null">
      配置文件 {{ status.configBytes.toLocaleString('zh-CN') }} 字节 · 修改时间 {{ formatDateTime(status.configModifiedAt) }}
    </p>
    <AppError v-if="failed" message="无法取得配置检查结果，请刷新状态" @retry="load" />
    <el-alert v-if="result" :title="statusLabels[result.status]" :type="result.status === 'PASSED' ? 'success' : 'warning'" :closable="false">
      {{ formatDateTime(result.checkedAt) }}<template v-if="result.exitCode != null">
        · 退出码 {{ result.exitCode }}
      </template>。语法通过不代表服务在线。
    </el-alert>
  </MrrSectionCard>
</template>

<style scoped>
.nginx-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
}
</style>
