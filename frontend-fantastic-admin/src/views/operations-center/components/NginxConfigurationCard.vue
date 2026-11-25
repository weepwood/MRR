<script setup lang="ts">
import type { NginxConfigCheck, NginxConfigStatus } from '@/api/modules/operations'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { checkNginxConfig, getNginxConfigStatus } from '@/api/modules/operations'
import AppError from '@/components/AppError/index.vue'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { formatDateTime } from '@/utils/display-format'
import { hasPermission } from '@/utils/session'

const status = ref<NginxConfigStatus>()
const result = ref<NginxConfigCheck>()
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
  UNAVAILABLE: '无法执行校验',
}

function clear() {
  generation++
  controller?.abort()
  controller = undefined
  status.value = undefined
  result.value = undefined
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
      <el-button v-if="canManage" :loading="checking" :disabled="busy || status?.state !== 'AVAILABLE'" @click="check">
        校验配置语法
      </el-button>
    </div>
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
