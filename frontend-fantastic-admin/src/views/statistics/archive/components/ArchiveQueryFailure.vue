<script setup lang="ts">
import ScanQueryDiagnostics from '@/components/ScanQueryDiagnostics/index.vue'
import useAuth from '@/utils/composables/useAuth'
import { copyText as copyToClipboard } from '@/utils/copy-text'

defineOptions({ name: 'ArchiveQueryFailure' })

const props = defineProps<{
  /** 失败原因文案，直接来自后端响应，保持原话不加工。 */
  message: string
  /** 后端 reasonCode，决定卡片语气与下一步建议。 */
  reason?: string
  requestId?: string
  query: { bah: string, sjh: string } | null
}>()

const emit = defineEmits<{ retry: [] }>()

const { auth } = useAuth()
const canDiagnose = computed(() => auth('system:manage') && auth('record:read'))
const diagnosticsVisible = ref(false)

type Tone = 'info' | 'warning' | 'danger'

/**
 * 每种失败都给一句「接下来怎么办」。只说「查不到」等于把问题推回给用户，
 * 而这三类原因的处理动作完全不同：补上架号 / 补充查询条件重试 / 找管理员。
 */
const REASON_META: Record<string, { tone: Tone, icon: string, hint: string }> = {
  NO_MATCHING_IMAGES: {
    tone: 'info',
    icon: 'i-lucide:file-search',
    hint: '未找到匹配的扫描影像。请核对原始编号，或确认是否已经扫描上架。',
  },
  NEEDS_SJH: {
    tone: 'warning',
    icon: 'i-lucide:triangle-alert',
    hint: '该病案号由多个上架号共用，需要补充上架号或直接用上架号查询。',
  },
  AMBIGUOUS_CODE: {
    tone: 'warning',
    icon: 'i-lucide:triangle-alert',
    hint: '这条编号对应多条病案，系统不会自动挑一条。请补充上架号后再查。',
  },
  INVALID_INPUT: {
    tone: 'warning',
    icon: 'i-lucide:info',
    hint: '请核对身份证号格式，或按病案号、上架号的原始编号重新查询。',
  },
}

const FALLBACK_META = {
  tone: 'danger' as Tone,
  icon: 'i-lucide:circle-alert',
  hint: '服务暂时不可用或响应异常。可以点「重新查询」重试；持续失败时请把追踪编号提供给管理员。',
}

const meta = computed(() => REASON_META[props.reason || ''] || {
  ...FALLBACK_META,
  hint: props.query ? FALLBACK_META.hint : '请核对查询条件后再次提交。',
})

function selectTrace(event: FocusEvent) {
  const input = event.target as HTMLInputElement | null
  input?.select()
}

async function copyTrace() {
  if (!props.requestId) {
    return
  }
  await copyToClipboard(props.requestId, { success: '追踪编号已复制' })
}
</script>

<template>
  <section class="query-failure" :data-tone="meta.tone" role="status">
    <div class="query-failure-head">
      <span class="query-failure-icon" aria-hidden="true">
        <FaIcon :name="meta.icon" />
      </span>
      <div class="query-failure-text">
        <p class="query-failure-title">
          {{ props.message }}
        </p>
        <p class="query-failure-hint">
          {{ meta.hint }}
        </p>
      </div>
    </div>

    <div v-if="props.requestId" class="query-failure-trace">
      <span class="trace-label">追踪编号</span>
      <div class="trace-field">
        <input
          :value="props.requestId"
          readonly
          aria-label="本次请求追踪编号"
          class="trace-input"
          @focus="selectTrace"
        >
        <button
          type="button"
          class="trace-copy"
          aria-label="复制追踪编号"
          title="复制追踪编号"
          @click="copyTrace"
        >
          <FaIcon name="i-lucide:copy" />
        </button>
      </div>
    </div>

    <div v-if="props.query" class="query-failure-actions">
      <el-button v-if="canDiagnose" type="primary" plain @click="diagnosticsVisible = true">
        排查本次查询
      </el-button>
      <el-button @click="emit('retry')">
        重新查询
      </el-button>
    </div>

    <ScanQueryDiagnostics
      v-if="canDiagnose && props.query"
      v-model="diagnosticsVisible"
      :bah="props.query.bah"
      :sjh="props.query.sjh"
      :request-id="props.requestId || ''"
    />
  </section>
</template>

<style scoped>
.query-failure {
  display: grid;
  gap: 10px;
  padding: 12px;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: var(--mrr-radius-card);
}

.query-failure[data-tone="warning"] {
  border-color: var(--el-color-warning-light-7);
}

.query-failure[data-tone="danger"] {
  border-color: var(--el-color-danger-light-7);
}

.query-failure-head {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.query-failure-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 26px;
  height: 26px;
  font-size: 15px;
  color: var(--el-color-info);
  background: var(--el-color-info-light-9);
  border-radius: var(--mrr-radius-pill);
}

.query-failure[data-tone="warning"] .query-failure-icon {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.query-failure[data-tone="danger"] .query-failure-icon {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

.query-failure-text {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.query-failure-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  color: var(--text-primary);
  overflow-wrap: anywhere;
}

.query-failure-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-tertiary);
  overflow-wrap: anywhere;
}

.query-failure-trace {
  display: grid;
  gap: 4px;
}

.trace-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.trace-field {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 0 4px 0 8px;
  background: var(--el-fill-color-light);
  border: 1px solid transparent;
  border-radius: var(--mrr-radius-sm);
}

.trace-field:focus-within {
  outline: 2px solid var(--mrr-ring);
  outline-offset: 1px;
  border-color: var(--el-color-primary-light-5);
}

.trace-input {
  flex: 1;
  min-width: 0;
  height: var(--mrr-input-height);
  padding: 0;
  font-family: var(--mrr-font-mono);
  font-size: 12px;
  color: var(--text-primary);
  background: transparent;
  border: 0;
}

.trace-input:focus {
  outline: none;
}

.trace-copy {
  display: grid;
  flex: none;
  place-items: center;
  width: 24px;
  height: 24px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--mrr-radius-sm);
  transition: color var(--mrr-motion-feedback) ease, background-color var(--mrr-motion-feedback) ease;
}

.trace-copy:hover {
  color: var(--el-color-primary);
  background: var(--el-fill-color);
}

.trace-copy:focus-visible {
  outline: 2px solid var(--mrr-ring);
  outline-offset: 1px;
}

.query-failure-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.query-failure-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
