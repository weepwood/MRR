<script setup lang="ts">
import type { QueryDiagnostic } from '@/api/modules/query-diagnostics'
import { ElMessage } from 'element-plus'
import { computed, ref } from 'vue'
import { diagnoseQuery } from '@/api/modules/query-diagnostics'
import useAuth from '@/utils/composables/useAuth'

const props = defineProps<{ bah: string, sjh: string, requestId: string }>()
const { auth } = useAuth()
const canDiagnose = computed(() => auth('system:manage') && auth('record:read'))
const loading = ref(false)
const visible = ref(false)
const report = ref<QueryDiagnostic | null>(null)
const error = ref('')
async function copyRequestId() {
  try { await navigator.clipboard.writeText(props.requestId); ElMessage.success('追踪编号已复制') }
  catch { ElMessage.info('复制失败，请选择追踪编号后按 Ctrl+C') }
}
async function run() {
  visible.value = true
  loading.value = true
  error.value = ''
  report.value = null
  try { report.value = (await diagnoseQuery({ bah: props.bah, sjh: props.sjh, requestId: props.requestId })).data || null }
  catch (exception) { error.value = exception instanceof Error ? exception.message : '诊断失败，请检查运维权限和服务状态' }
  finally { loading.value = false }
}
</script>

<template>
  <div class="query-diagnostics">
    <label v-if="requestId">追踪编号（可选择复制）<input :value="requestId" readonly aria-label="本次请求追踪编号" @focus="($event.target as HTMLInputElement).select()"></label>
    <el-button v-if="requestId" size="small" @click="copyRequestId">
      复制追踪编号
    </el-button>
    <el-button v-if="canDiagnose" :loading="loading" size="small" @click="run">
      排查本次查询
    </el-button>
    <el-drawer v-model="visible" title="病案查询诊断" size="65%">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <p>原请求追踪编号：{{ requestId || '未获取' }}。以下为当前数据库只读探测，可能与原请求时刻不同。</p>
      <p>{{ report?.scope }}</p>
      <ol v-loading="loading" class="diagnostic-steps">
        <li v-for="step in report?.steps" :key="step.code">
          <strong>{{ step.code }} · {{ step.status }} · {{ step.durationMs }} ms</strong>
          <p>{{ step.message }}</p><pre>{{ JSON.stringify(step.details, null, 2) }}</pre>
        </li>
      </ol>
      <router-link v-if="auth('log:read') && requestId" :to="{ path: '/logs', query: { requestId } }">
        按追踪编号查看访问日志
      </router-link>
    </el-drawer>
  </div>
</template>

<style scoped>
.query-diagnostics { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.query-diagnostics label { display: grid; gap: 4px; font-size: 12px; color: var(--el-text-color-secondary); }
.query-diagnostics input { padding: 6px; color: var(--el-text-color-primary); background: var(--el-fill-color-light); border: 1px solid var(--el-border-color); border-radius: 4px; }
.diagnostic-steps { padding-left: 24px; }
.diagnostic-steps li { margin-bottom: 16px; }
.diagnostic-steps pre { overflow-wrap: anywhere; white-space: pre-wrap; }
</style>
