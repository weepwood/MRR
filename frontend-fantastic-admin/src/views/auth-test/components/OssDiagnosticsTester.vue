<script setup lang="ts">
import type { AuthTestHistoryEvent } from '../types'
import type { OssCheckId, OssCheckSummary } from './oss-checks'
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { executeAuthTestRequest } from '@/api/modules/auth-test'
import { useUserStore } from '@/store/modules/user'
import { OSS_CHECKS, summarizeOssCheck } from './oss-checks'

const emit = defineEmits<{ record: [event: AuthTestHistoryEvent] }>()
const user = useUserStore()
const busy = ref(false)
const results = reactive<Partial<Record<OssCheckId, OssCheckSummary & { status: number, durationMs: number }>>>({})
const running = ref<OssCheckId>()
let generation = 0
const labels = { passed: '通过', warning: '需关注', failed: '未通过' } as const
const completed = computed(() => Object.keys(results).length)

async function run(ids: readonly OssCheckId[]) {
  if (busy.value) { return }
  busy.value = true
  const current = ++generation
  try {
    for (const id of ids) {
      if (current !== generation) { break }
      const check = OSS_CHECKS.find(item => item.id === id)!
      running.value = id
      delete results[id]
      let status = 0
      let durationMs = 0
      let summary: OssCheckSummary
      const started = performance.now()
      try {
        const response = await executeAuthTestRequest({ method: 'GET', path: check.path, token: user.token })
        status = response.status
        durationMs = response.durationMs
        summary = summarizeOssCheck(id, response)
      }
      catch {
        durationMs = Math.round(performance.now() - started)
        summary = { outcome: 'failed', summary: '网络请求未完成，请检查网络和后端服务后重试。' }
      }
      if (current !== generation) { break }
      results[id] = { ...summary, status, durationMs }
      emit('record', { name: check.name, method: 'GET', path: check.path, status, durationMs, outcome: summary.outcome })
    }
  }
  finally {
    if (current === generation) {
      busy.value = false
      running.value = undefined
    }
  }
}
function clear() {
  if (busy.value) { return }
  for (const check of OSS_CHECKS) { delete results[check.id] }
}
onBeforeUnmount(() => { generation++ })
</script>

<template>
  <el-card shadow="never" class="oss-check-panel">
    <template #header>
      <div class="panel-header">
        <div>
          <strong>OSS 只读检查</strong>
          <p>使用当前登录会话。后端仍校验 record:read 权限；结果仅保存在当前标签页内存。</p>
        </div>
        <div class="panel-actions">
          <el-button :disabled="busy || completed === 0" @click="clear">
            清空检查结果
          </el-button>
          <el-button type="primary" :loading="busy" @click="run(OSS_CHECKS.map(item => item.id))">
            运行全部 OSS 检查
          </el-button>
        </div>
      </div>
    </template>
    <el-alert title="仅执行有限只读请求；不会上传、删除文件或启动迁移。检查通过仅表示对应接口本次可用。" type="info" :closable="false" show-icon />
    <section v-for="check in OSS_CHECKS" :key="check.id" class="check-row" :data-testid="`oss-check-${check.id}`">
      <div class="check-copy">
        <h3>{{ check.name }}</h3>
        <p>{{ check.description }}</p>
        <code>GET {{ check.path }}</code>
        <div class="check-result" role="status" aria-live="polite">
          <el-tag v-if="running === check.id" type="info">
            检查中
          </el-tag>
          <template v-else-if="results[check.id]">
            <strong class="outcome" :data-outcome="results[check.id]!.outcome">
              {{ labels[results[check.id]!.outcome] }}
            </strong>
            <span>HTTP {{ results[check.id]!.status || '未收到响应' }} · {{ results[check.id]!.durationMs }} ms</span>
            <p>{{ results[check.id]!.summary }}</p>
          </template>
          <span v-else>尚未执行</span>
        </div>
      </div>
      <el-button :disabled="busy" @click="run([check.id])">
        执行检查
      </el-button>
    </section>
  </el-card>
</template>

<style scoped>
.panel-header,
.panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.panel-header > div {
  min-width: 0;
}

.panel-header p,
.check-copy p {
  margin: 6px 0;
  line-height: 1.6;
  color: var(--mrr-muted-foreground);
}

.panel-actions .el-button + .el-button {
  margin-left: 0;
}

.check-row {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px 0;
  border-bottom: 1px solid var(--mrr-border);
}

.check-row:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.check-copy {
  min-width: 0;
  overflow-wrap: anywhere;
}

.check-copy h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.check-copy code {
  font-size: 12px;
  color: var(--mrr-muted-foreground);
  overflow-wrap: anywhere;
}

.check-result {
  margin-top: 10px;
  font-size: 13px;
}

.check-result > span,
.check-result > strong {
  margin-right: 8px;
}

.outcome[data-outcome="passed"] {
  color: var(--mrr-tone-success);
}

.outcome[data-outcome="warning"] {
  color: var(--mrr-tone-warning);
}

.outcome[data-outcome="failed"] {
  color: var(--mrr-tone-danger);
}

@media (max-width: 600px) {
  .check-row {
    flex-direction: column;
    gap: 12px;
  }

  .panel-actions {
    justify-content: flex-start;
    width: 100%;
  }
}
</style>
