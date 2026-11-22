<script setup lang="ts">
import type { QueryDiagnostic } from '@/api/modules/query-diagnostics'
import axios from 'axios'
import { computed, onBeforeUnmount, onDeactivated, ref, useTemplateRef, watch } from 'vue'
import { diagnoseQuery } from '@/api/modules/query-diagnostics'
import AppEmpty from '@/components/AppEmpty/index.vue'
import { ACTION_DIALOG_Z_INDEX } from '@/ui/components/ActionDialog'
import useAuth from '@/utils/composables/useAuth'

defineOptions({ name: 'ScanQueryDiagnostics' })

const props = defineProps<{ bah: string, sjh: string, requestId: string }>()
const visible = defineModel<boolean>({ default: false })

const user = useUserStore()
const { auth } = useAuth()
const canReadLog = computed(() => auth('log:read') && /^[a-f0-9]{16}$/i.test(props.requestId))

const loading = ref(false)
const problem = ref<{ message: string, retryable: boolean } | null>(null)
const report = ref<QueryDiagnostic | null>(null)
const reportBody = useTemplateRef<HTMLElement>('reportBody')
let activeRequest: AbortController | undefined

/** 探测步骤的 code 是运维口径，不给业务同事看英文；这里逐条翻成人话。 */
const STEP_TITLES: Record<string, string> = {
  INPUT: '查询条件',
  REQUEST_RECEIVED: '原请求访问日志',
  DATABASE: '数据库连接',
  ARCHIVE_EXACT: '主档精确匹配',
  ARCHIVE_COMPAT: '主档兼容匹配',
  SCAN_EXACT: '扫描记录精确匹配',
  SCAN_COMPAT: '扫描记录兼容匹配',
  CACHE: '查询缓存',
  CLIENT: '客户端核对',
}

type StatusTone = 'success' | 'warning' | 'danger' | 'info'

/** 状态决定颜色、图标和标签文案，三处必须一起改，所以放在一张表里。 */
const STATUS_META: Record<string, { label: string, tone: StatusTone, icon: string }> = {
  OK: { label: '通过', tone: 'success', icon: 'i-lucide:circle-check' },
  AMBIGUOUS: { label: '多个候选', tone: 'warning', icon: 'i-lucide:triangle-alert' },
  NEEDS_SJH: { label: '需补上架号', tone: 'warning', icon: 'i-lucide:triangle-alert' },
  NOT_CHECKED: { label: '未检查', tone: 'info', icon: 'i-lucide:info' },
  NO_SAMPLE: { label: '无匹配样本', tone: 'warning', icon: 'i-lucide:search-x' },
  MISMATCH: { label: '与预期不一致', tone: 'warning', icon: 'i-lucide:triangle-alert' },
  UNKNOWN: { label: '探测失败', tone: 'danger', icon: 'i-lucide:circle-alert' },
  INFO: { label: '提示', tone: 'info', icon: 'i-lucide:info' },
  CLIENT_CONFIRMATION_REQUIRED: { label: '需人工确认', tone: 'warning', icon: 'i-lucide:info' },
}

const SAMPLE_COLUMNS = [
  { key: 'id', label: '记录 ID' },
  { key: 'archive_id', label: '关联主档 ID' },
  { key: 'uploadflag', label: '上传状态值' },
  { key: 'access_time', label: '访问时间' },
  { key: 'response_status', label: 'HTTP 状态' },
  { key: 'execute_time', label: '响应耗时（ms）' },
  { key: 'expected_schema', label: '默认 Schema 符合预期' },
]

interface StepView {
  code: string
  title: string
  status: string
  statusLabel: string
  tone: StatusTone
  icon: string
  message: string
  durationMs: number
  facts: { label: string, value: string }[]
  columns: typeof SAMPLE_COLUMNS
  samples: Record<string, unknown>[]
  sampleCount: number
}

function formatCell(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  if (typeof value === 'boolean') {
    return value ? '是' : '否'
  }
  return typeof value === 'number' || typeof value === 'string' ? String(value) : '—'
}

/**
 * details 里混着两种东西：探测样本（表格）和零散结论（键值对）。
 * 旧版把整块 JSON 原样倒出来，业务同事看不懂；这里拆成「事实行 + 样本表」。
 */
function buildStep(raw: QueryDiagnostic['steps'][number]): StepView {
  const meta = STATUS_META[raw.status] ?? { label: raw.status || '未知', tone: 'info' as StatusTone, icon: 'i-lucide:info' }
  const details = raw.details || {}
  const facts: { label: string, value: string }[] = []
  if (typeof details.hit === 'boolean') {
    facts.push({ label: '缓存', value: details.hit ? '命中' : '未命中' })
  }
  if (typeof details.sampledCount === 'number') {
    facts.push({ label: '样本', value: `${details.sampledCount} 条${details.truncated ? '（达到采样上限）' : ''}` })
  }
  const samples = Array.isArray(details.samples)
    ? details.samples.filter((row): row is Record<string, unknown> => !!row && typeof row === 'object')
    : []
  const columns = SAMPLE_COLUMNS.filter(column => samples.some(row => column.key in row))

  return {
    code: raw.code,
    title: STEP_TITLES[raw.code] || raw.code,
    status: raw.status,
    statusLabel: meta.label,
    tone: meta.tone,
    icon: meta.icon,
    message: raw.message || '',
    durationMs: Number(raw.durationMs) || 0,
    facts,
    columns,
    samples: samples.slice(0, 5),
    sampleCount: samples.length,
  }
}

const steps = computed<StepView[]>(() => (report.value?.steps || []).map(buildStep))
const attentionSteps = computed(() => steps.value
  .map((step, index) => ({ step, index }))
  .filter(({ step }) => step.tone === 'warning' || step.tone === 'danger')
  .sort((a, b) => Number(b.step.tone === 'danger') - Number(a.step.tone === 'danger')))

const summary = computed(() => {
  const list = steps.value
  return {
    ok: list.filter(step => step.status === 'OK').length,
    attention: attentionSteps.value.length,
    skipped: list.filter(step => step.status === 'NOT_CHECKED').length,
    totalMs: list.reduce((sum, step) => sum + step.durationMs, 0),
    count: list.length,
  }
})

const queryLabel = computed(() => {
  const parts: string[] = []
  if (props.bah) {
    parts.push(`病案号 ${props.bah}`)
  }
  if (props.sjh) {
    parts.push(`上架号 ${props.sjh}`)
  }
  return parts.join(' · ') || '未提供查询条件'
})

function jumpToStep(index: number) {
  const heading = reportBody.value?.querySelector<HTMLElement>(`[data-step-index="${index}"] .step-title`)
  heading?.focus({ preventScroll: true })
  heading?.scrollIntoView({ block: 'start' })
}

function describeFailure(error: unknown) {
  const status = axios.isAxiosError(error)
    ? error.response?.status
    : Number((error as { code?: number } | null)?.code)
  switch (status) {
    case 400: return { message: '诊断条件无效，请更正查询条件后重试。', retryable: false }
    case 401: return { message: '登录已失效，请重新登录后再诊断。', retryable: false }
    case 403: return { message: '当前账号没有病案查询诊断权限。', retryable: false }
    case 404: return { message: '当前环境未提供病案查询诊断，请联系管理员。', retryable: false }
    default: return { message: '诊断服务暂时不可用，可以重新探测。', retryable: true }
  }
}

function resetReport() {
  const request = activeRequest
  activeRequest = undefined
  request?.abort()
  loading.value = false
  problem.value = null
  report.value = null
}

async function run() {
  if (!visible.value || activeRequest) {
    return
  }
  const request = new AbortController()
  activeRequest = request
  loading.value = true
  problem.value = null
  report.value = null
  if (reportBody.value) {
    reportBody.value.scrollTop = 0
  }
  try {
    const response = await diagnoseQuery(
      { bah: props.bah, sjh: props.sjh, requestId: props.requestId },
      request.signal,
      user.token,
    )
    if (activeRequest !== request) {
      return
    }
    const data = response?.data
    if (!Array.isArray(data?.steps) || !data.steps.every(step =>
      step && typeof step.code === 'string' && typeof step.status === 'string' && typeof step.message === 'string',
    )) {
      problem.value = { message: '诊断返回格式异常，可以重新探测。', retryable: true }
      return
    }
    report.value = data
  }
  catch (error) {
    if (activeRequest === request) {
      problem.value = describeFailure(error)
    }
  }
  finally {
    if (activeRequest === request) {
      activeRequest = undefined
      loading.value = false
    }
  }
}

watch(visible, (open) => {
  if (open) {
    void run()
  }
  else {
    resetReport()
  }
}, { flush: 'sync' })

function dismiss() {
  visible.value = false
  resetReport()
}

watch([() => props.bah, () => props.sjh, () => props.requestId, () => user.token], dismiss)
onDeactivated(dismiss)
onBeforeUnmount(resetReport)
</script>

<template>
  <el-dialog
    v-model="visible"
    title="病案查询诊断"
    width="min(760px, calc(100% - 24px))"
    class="query-diagnostics"
    modal-class="query-diagnostics-overlay"
    align-center
    :z-index="ACTION_DIALOG_Z_INDEX"
    append-to-body
    destroy-on-close
    @close="dismiss"
  >
    <div ref="reportBody" class="diagnostics-body" role="region" aria-label="诊断报告" tabindex="0">
      <div class="diagnostics-meta">
        <div class="diagnostics-query">
          <span class="meta-label">查询对象</span>
          <span class="meta-value">{{ queryLabel }}</span>
        </div>
        <div v-if="props.requestId" class="diagnostics-query">
          <span class="meta-label">追踪编号</span>
          <code class="meta-code">{{ props.requestId }}</code>
        </div>
        <router-link
          v-if="canReadLog && (!problem || problem.retryable)"
          class="diagnostics-log-link"
          :to="{ path: '/logs', query: { requestId: props.requestId } }"
        >
          查看原请求访问日志
        </router-link>
      </div>

      <p class="diagnostics-hint">
        对本次失败查询使用的编号进行当前时刻的只读探测。原请求可通过访问日志追溯。
      </p>
      <p v-if="loading" class="diagnostics-hint" role="status">
        正在探测…
      </p>
      <el-alert v-else-if="problem" :title="problem.message" type="error" :closable="false" show-icon />
      <div v-if="steps.length" class="diagnostics-summary">
        <span class="summary-pill" data-tone="success">{{ summary.ok }} 项通过</span>
        <span v-if="summary.attention" class="summary-pill" data-tone="warning">{{ summary.attention }} 项需关注</span>
        <span v-if="summary.skipped" class="summary-pill" data-tone="info">{{ summary.skipped }} 项未检查</span>
        <span class="summary-total">共 {{ summary.count }} 项 · 探测累计耗时 {{ summary.totalMs }} ms</span>
        <el-tag v-if="report?.readOnly" size="small" type="info" effect="plain">
          只读探测
        </el-tag>
      </div>

      <section v-if="attentionSteps.length" class="diagnostics-attention">
        <h3>优先核对</h3>
        <ul>
          <li v-for="{ step, index } in attentionSteps.slice(0, 3)" :key="index">
            <button type="button" @click="jumpToStep(index)">
              {{ step.title }} · {{ step.statusLabel }}
            </button>
          </li>
        </ul>
      </section>
      <p v-if="report?.scope && steps.length" class="diagnostics-hint">
        {{ report.scope }}
      </p>

      <ol v-if="steps.length" class="diagnostics-steps">
        <li v-for="(step, index) in steps" :key="step.code" class="diagnostics-step" :data-tone="step.tone" :data-step-index="index">
          <span class="step-dot" aria-hidden="true"><FaIcon :name="step.icon" /></span>
          <div class="step-body">
            <div class="step-head">
              <h4 class="step-title" tabindex="-1">
                {{ step.title }}
              </h4>
              <code class="step-code">{{ step.code }}</code>
              <span class="step-status">{{ step.statusLabel }}</span>
              <span v-if="step.durationMs > 0" class="step-duration">{{ step.durationMs }} ms</span>
            </div>
            <p v-if="step.message" class="step-message">
              {{ step.message }}
            </p>

            <dl v-if="step.facts.length" class="step-facts">
              <template v-for="fact in step.facts" :key="fact.label">
                <dt>{{ fact.label }}</dt>
                <dd>{{ fact.value }}</dd>
              </template>
            </dl>

            <div v-if="step.columns.length" class="step-samples">
              <table class="step-sample-table">
                <thead>
                  <tr>
                    <th v-for="column in step.columns" :key="column.key" scope="col">
                      {{ column.label }}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, rowIndex) in step.samples" :key="rowIndex">
                    <td v-for="column in step.columns" :key="column.key">
                      {{ formatCell(row[column.key]) }}
                    </td>
                  </tr>
                </tbody>
              </table>
              <div class="step-sample-list">
                <dl v-for="(row, rowIndex) in step.samples" :key="rowIndex">
                  <template v-for="column in step.columns" :key="column.key">
                    <dt>{{ column.label }}</dt>
                    <dd>{{ formatCell(row[column.key]) }}</dd>
                  </template>
                </dl>
              </div>
              <p v-if="step.sampleCount > step.samples.length" class="samples-note">
                展示前 {{ step.samples.length }} 条，共采样 {{ step.sampleCount }} 条
              </p>
            </div>
          </div>
        </li>
      </ol>

      <AppEmpty v-else-if="report" description="本次没有可展示的探测步骤" />
    </div>

    <template #footer>
      <div class="diagnostics-footer">
        <el-button v-if="!problem || problem.retryable" :loading="loading" @click="run">
          重新探测
        </el-button>
        <el-button type="primary" @click="visible = false">
          关闭
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
:global(.query-diagnostics-overlay) {
  --el-transition-duration: var(--mrr-motion-overlay);
}

:global(.query-diagnostics-overlay.dialog-fade-enter-active),
:global(.query-diagnostics-overlay.dialog-fade-leave-active),
:global(.query-diagnostics-overlay.dialog-fade-enter-active .el-overlay-dialog),
:global(.query-diagnostics-overlay.dialog-fade-leave-active .el-overlay-dialog) {
  animation-timing-function: var(--mrr-ease-out);
}

:global(.el-dialog.query-diagnostics) {
  box-sizing: border-box;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  height: min(42rem, calc(100vh - 24px));
}

:global(.query-diagnostics > .el-dialog__body) {
  display: flex;
  min-width: 0;
  min-height: 0;
  padding: 0;
  overflow: hidden;
}

:global(.query-diagnostics > .el-dialog__footer) {
  padding: 12px 0 0;
}

.diagnostics-body {
  display: grid;
  flex: 1;
  gap: var(--mrr-space-3);
  align-content: start;
  min-width: 0;
  min-height: 0;
  padding: 2px var(--mrr-space-2) 12px 2px;
  overflow: auto;
  scroll-behavior: auto;
  overscroll-behavior: contain;
}

.diagnostics-body:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: -2px;
}

.diagnostics-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2) var(--mrr-space-4);
}

.diagnostics-query {
  display: flex;
  gap: var(--mrr-space-2);
  align-items: baseline;
  min-width: 0;
}

.meta-label {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 700;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.meta-value,
.meta-code {
  min-width: 0;
  overflow-wrap: anywhere;
}

.meta-value {
  font-size: 13px;
  color: var(--text-primary);
}

.meta-code {
  padding: 1px var(--mrr-space-2);
  font-family: var(--mrr-font-mono);
  font-size: 12px;
  color: var(--text-primary);
  background: var(--el-fill-color-light);
  border-radius: var(--mrr-radius-sm);
}

.diagnostics-summary {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2) 8px;
  align-items: center;
}

.summary-pill {
  padding: 1px 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-info);
  background: var(--el-color-info-light-9);
  border-radius: var(--mrr-radius-pill);
}

.summary-pill[data-tone="success"] {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.summary-pill[data-tone="warning"] {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.summary-total {
  font-size: 12px;
  color: var(--text-tertiary);
}

.diagnostics-attention h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}

.diagnostics-attention ul {
  display: grid;
  gap: 2px;
  padding: 0;
  margin: 4px 0 0;
  list-style: none;
}

.diagnostics-attention button {
  min-height: var(--mrr-control-height, 32px);
  padding: 2px 4px;
  color: var(--el-color-primary);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--mrr-radius-sm);
}

.diagnostics-attention button:hover {
  background: var(--el-fill-color-light);
}

.diagnostics-attention button:focus-visible,
.step-title:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.diagnostics-steps {
  display: grid;
  gap: 2px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.diagnostics-step {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: var(--mrr-space-3);
  padding: var(--mrr-space-3) 0;
  border-top: 1px solid var(--divider);
}

.diagnostics-step:first-child {
  border-top: 0;
}

.step-dot {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  margin-top: 1px;
  font-size: 14px;
  color: var(--el-color-info);
  background: var(--el-color-info-light-9);
  border-radius: var(--mrr-radius-pill);
}

.diagnostics-step[data-tone="success"] .step-dot {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.diagnostics-step[data-tone="warning"] .step-dot {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.diagnostics-step[data-tone="danger"] .step-dot {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

.step-body {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.step-head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
}

.step-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  scroll-margin-top: 8px;
}

.step-code {
  font-family: var(--mrr-font-mono);
  font-size: 11px;
  color: var(--text-tertiary);
}

.step-status {
  padding: 0 var(--mrr-space-2);
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  color: var(--el-color-info);
  background: var(--el-color-info-light-9);
  border-radius: var(--mrr-radius-pill);
}

.diagnostics-step[data-tone="success"] .step-status {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.diagnostics-step[data-tone="warning"] .step-status {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.diagnostics-step[data-tone="danger"] .step-status {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

.step-duration {
  margin-left: auto;
  font-family: var(--mrr-font-mono);
  font-size: 11px;
  color: var(--text-tertiary);
}

.step-message {
  margin: 0;
  font-size: 12px;
  line-height: 1.65;
  color: var(--text-secondary);
}

.step-facts {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 12px;
  margin: 0;
  font-size: 12px;
}

.step-facts dt {
  color: var(--text-tertiary);
}

.step-facts dd {
  margin: 0 8px 0 0;
  font-family: var(--mrr-font-mono);
  color: var(--text-primary);
}

.step-samples {
  margin-top: 2px;
  overflow-x: auto;
}

.step-sample-table {
  width: 100%;
  font-size: 12px;
  table-layout: fixed;
  border-collapse: collapse;
}

.step-samples th,
.step-samples td {
  padding: 4px 8px;
  text-align: left;
  overflow-wrap: anywhere;
  border-bottom: 1px solid var(--divider);
}

.step-samples th {
  font-weight: 600;
  color: var(--text-tertiary);
}

.step-samples td {
  font-family: var(--mrr-font-mono);
  color: var(--text-primary);
}

.step-sample-list {
  display: none;
}

.samples-note {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--text-tertiary);
}

.diagnostics-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
}

.diagnostics-log-link {
  font-size: 13px;
  color: var(--el-color-primary);
}

.diagnostics-hint {
  margin: 0;
  font-size: 12px;
  color: var(--text-tertiary);
}

@media (max-width: 560px) {
  .diagnostics-step {
    grid-template-columns: 20px minmax(0, 1fr);
    gap: 8px;
  }

  .step-dot {
    width: 20px;
    height: 20px;
  }

  .step-sample-table {
    display: none;
  }

  .step-sample-list {
    display: grid;
    gap: var(--mrr-space-2);
    font-size: 12px;
  }

  .step-sample-list dl {
    display: grid;
    grid-template-columns: minmax(0, 2fr) minmax(0, 3fr);
    gap: 4px 8px;
    padding: var(--mrr-space-2);
    margin: 0;
    border: 1px solid var(--divider);
    border-radius: var(--mrr-radius-sm);
  }

  .step-sample-list dt {
    color: var(--text-tertiary);
  }

  .step-sample-list dd {
    margin: 0;
    overflow-wrap: anywhere;
  }
}
</style>
