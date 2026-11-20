<script setup lang="ts">
import type { QueryDiagnostic } from '@/api/modules/query-diagnostics'
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { diagnoseQuery } from '@/api/modules/query-diagnostics'
import { ACTION_DIALOG_Z_INDEX } from '@/ui/components/ActionDialog'
import useAuth from '@/utils/composables/useAuth'

defineOptions({ name: 'ScanQueryDiagnostics' })

const props = defineProps<{ bah: string, sjh: string, requestId: string }>()
const visible = defineModel<boolean>({ default: false })

const { auth } = useAuth()
const canReadLog = computed(() => auth('log:read'))

const loading = ref(false)
const failed = ref(false)
const report = ref<QueryDiagnostic | null>(null)

/** 探测步骤的 code 是运维口径，不给业务同事看英文；这里逐条翻成人话。 */
const STEP_TITLES: Record<string, string> = {
  INPUT: '查询条件',
  REQUEST_RECEIVED: '原请求到达',
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
  UNKNOWN: { label: '探测失败', tone: 'danger', icon: 'i-lucide:circle-alert' },
  INFO: { label: '提示', tone: 'info', icon: 'i-lucide:info' },
  CLIENT_CONFIRMATION_REQUIRED: { label: '需人工确认', tone: 'warning', icon: 'i-lucide:info' },
}

interface DiagnosticStep {
  code: string
  status: string
  message: string
  durationMs: number
  details: Record<string, unknown>
}

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
  columns: string[]
  samples: Record<string, unknown>[]
}

function formatCell(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  if (typeof value === 'boolean') {
    return value ? '是' : '否'
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

/**
 * details 里混着两种东西：探测样本（表格）和零散结论（键值对）。
 * 旧版把整块 JSON 原样倒出来，业务同事看不懂；这里拆成「事实行 + 样本表」。
 */
function buildStep(raw: DiagnosticStep): StepView {
  const meta = STATUS_META[raw.status] ?? { label: raw.status || '未知', tone: 'info' as StatusTone, icon: 'i-lucide:info' }
  const details = raw.details || {}
  const facts: { label: string, value: string }[] = []
  const rest: Record<string, unknown> = { ...details }
  delete rest.samples
  delete rest.sampledCount
  delete rest.truncated

  if (typeof details.hit === 'boolean') {
    facts.push({ label: '缓存', value: details.hit ? '命中' : '未命中' })
    delete rest.hit
  }
  if (typeof details.sampledCount === 'number') {
    facts.push({ label: '样本', value: `${details.sampledCount} 条${details.truncated ? '（已截断）' : ''}` })
  }
  for (const [key, value] of Object.entries(rest)) {
    facts.push({ label: key, value: formatCell(value) })
  }

  const samples = Array.isArray(details.samples) ? (details.samples as Record<string, unknown>[]) : []
  const columns: string[] = []
  for (const row of samples) {
    for (const key of Object.keys(row || {})) {
      if (!columns.includes(key)) {
        columns.push(key)
      }
    }
  }

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
    samples,
  }
}

const steps = computed<StepView[]>(() => (report.value?.steps || []).map(buildStep))

const summary = computed(() => {
  const list = steps.value
  return {
    ok: list.filter(step => step.status === 'OK').length,
    attention: list.filter(step => step.tone === 'warning' || step.tone === 'danger').length,
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

async function run() {
  loading.value = true
  failed.value = false
  report.value = null
  try {
    report.value = (await diagnoseQuery({ bah: props.bah, sjh: props.sjh, requestId: props.requestId })).data || null
  }
  catch {
    failed.value = true
    ElMessage.error('诊断失败，请检查运维权限和服务状态')
  }
  finally {
    loading.value = false
  }
}

// 弹窗即排查：点「排查本次查询」才发起只读探测，打开后直接给结果。
watch(visible, (open) => {
  if (open) {
    void run()
  }
})
</script>

<template>
  <el-dialog
    v-model="visible"
    title="病案查询诊断"
    width="min(760px, calc(100% - 24px))"
    top="min(12vh, 5rem)"
    class="query-diagnostics"
    :z-index="ACTION_DIALOG_Z_INDEX"
    append-to-body
    destroy-on-close
  >
    <div v-loading="loading" class="diagnostics-body">
      <div class="diagnostics-meta">
        <div class="diagnostics-query">
          <span class="meta-label">查询对象</span>
          <span class="meta-value">{{ queryLabel }}</span>
        </div>
        <div v-if="props.requestId" class="diagnostics-query">
          <span class="meta-label">追踪编号</span>
          <code class="meta-code">{{ props.requestId }}</code>
        </div>
      </div>

      <div class="diagnostics-summary">
        <span class="summary-pill" data-tone="success">{{ summary.ok }} 项通过</span>
        <span v-if="summary.attention" class="summary-pill" data-tone="warning">{{ summary.attention }} 项需关注</span>
        <span v-if="summary.skipped" class="summary-pill" data-tone="info">{{ summary.skipped }} 项未检查</span>
        <span class="summary-total">共 {{ summary.count }} 项 · 耗时 {{ summary.totalMs }} ms</span>
        <el-tag v-if="report?.readOnly" size="small" type="info" effect="plain">
          只读探测
        </el-tag>
      </div>

      <ol v-if="steps.length" class="diagnostics-steps">
        <li v-for="step in steps" :key="step.code" class="diagnostics-step" :data-tone="step.tone">
          <span class="step-dot" aria-hidden="true"><FaIcon :name="step.icon" /></span>
          <div class="step-body">
            <div class="step-head">
              <h4 class="step-title">
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

            <div v-if="step.samples.length" class="step-samples">
              <table>
                <thead>
                  <tr>
                    <th v-for="column in step.columns" :key="column">
                      {{ column }}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, rowIndex) in step.samples.slice(0, 5)" :key="rowIndex">
                    <td v-for="column in step.columns" :key="column">
                      {{ formatCell(row?.[column]) }}
                    </td>
                  </tr>
                </tbody>
              </table>
              <p v-if="step.samples.length > 5" class="samples-note">
                仅显示前 5 条，共 {{ step.samples.length }} 条
              </p>
            </div>
          </div>
        </li>
      </ol>

      <el-empty v-else-if="!loading && failed" description="诊断请求失败，可稍后重试">
        <el-button size="small" @click="run">
          重新诊断
        </el-button>
      </el-empty>
      <el-empty v-else-if="!loading" description="本次没有可展示的探测步骤" />
    </div>

    <template #footer>
      <div class="diagnostics-footer">
        <router-link
          v-if="canReadLog && props.requestId"
          class="diagnostics-log-link"
          :to="{ path: '/logs', query: { requestId: props.requestId } }"
        >
          按追踪编号查看访问日志
        </router-link>
        <span v-else class="diagnostics-hint">只读探测最多采样 51 条，不代表全量统计。</span>
        <div class="diagnostics-footer-actions">
          <el-button :loading="loading" @click="run">
            重新探测
          </el-button>
          <el-button type="primary" @click="visible = false">
            关闭
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
/*
 * 九步探测展开后轻松超过一屏，弹窗必须自己滚，否则底部按钮被推到视口外，
 * 高分辨率笔记本上要拖整页才能关掉。表头以外的内容都在这里滚。
 */
.diagnostics-body {
  display: grid;
  gap: 14px;
  min-height: 180px;
  max-height: min(60vh, 560px);
  padding-right: 4px;
  overflow-y: auto;
}

.diagnostics-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
}

.diagnostics-query {
  display: flex;
  gap: 6px;
  align-items: baseline;
  min-width: 0;
}

.meta-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.meta-value {
  font-size: 13px;
  color: var(--text-primary);
}

.meta-code {
  padding: 1px 6px;
  font-family: var(--mrr-font-mono);
  font-size: 12px;
  color: var(--text-primary);
  background: var(--el-fill-color-light);
  border-radius: var(--mrr-radius-sm);
}

.diagnostics-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 8px;
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
  gap: 10px;
  padding: 10px 0;
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
  gap: 6px;
  align-items: center;
}

.step-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.step-code {
  font-family: var(--mrr-font-mono);
  font-size: 11px;
  color: var(--text-tertiary);
}

.step-status {
  padding: 0 6px;
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

.step-samples table {
  width: auto;
  min-width: 50%;
  font-size: 12px;
  border-collapse: collapse;
}

.step-samples th,
.step-samples td {
  padding: 4px 8px;
  text-align: left;
  white-space: nowrap;
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
  justify-content: space-between;
}

.diagnostics-log-link {
  font-size: 13px;
  color: var(--el-color-primary);
}

.diagnostics-hint {
  font-size: 12px;
  color: var(--text-tertiary);
}

.diagnostics-footer-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

@media (max-width: 720px) {
  .diagnostics-footer {
    justify-content: flex-start;
  }

  .diagnostics-footer-actions {
    margin-left: 0;
  }
}
</style>
