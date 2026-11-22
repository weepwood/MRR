<script setup lang="ts">
import type { ElInput } from 'element-plus'
import { onBeforeUnmount, ref, useTemplateRef, watch } from 'vue'
import { ACTION_DIALOG_EXTRA, ACTION_DIALOG_Z_INDEX, cancelActionDialogs, currentActionDialog, finishActionDialog } from './index'

const visible = ref(false)
const input = ref('')
const inputError = ref('')
const inputControl = useTemplateRef<InstanceType<typeof ElInput>>('inputControl')
let confirmedValue: string | undefined

watch(currentActionDialog, (request) => {
  if (request) {
    input.value = ''
    inputError.value = ''
    confirmedValue = undefined
    visible.value = true
  }
}, { immediate: true })

function focusInput() {
  inputControl.value?.focus()
}

function confirm() {
  if (!visible.value) { return }
  const result = currentActionDialog.value?.input?.inputValidator?.(input.value)
  if (typeof result === 'string') {
    inputError.value = result
    focusInput()
    return
  }
  confirmedValue = input.value
  visible.value = false
}

function confirmFromInput(event: Event) {
  if (event instanceof KeyboardEvent && !event.isComposing && currentActionDialog.value?.input?.inputType !== 'textarea') {
    event.preventDefault()
    confirm()
  }
}

function chooseExtra() {
  if (!visible.value || !currentActionDialog.value?.options.extraButtonText) { return }
  confirmedValue = ACTION_DIALOG_EXTRA
  visible.value = false
}

function handleClosed() {
  if (currentActionDialog.value) {
    finishActionDialog(currentActionDialog.value, confirmedValue)
  }
}

onBeforeUnmount(cancelActionDialogs)
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="currentActionDialog?.title"
    :close-on-click-modal="currentActionDialog?.options.closeOnClickModal ?? true"
    :close-on-press-escape="currentActionDialog?.options.closeOnPressEscape ?? true"
    :width="currentActionDialog?.options.details ? 'min(560px, calc(100vw - 32px))' : 'min(480px, calc(100vw - 32px))'"
    :z-index="ACTION_DIALOG_Z_INDEX"
    align-center
    append-to-body
    destroy-on-close
    @opened="focusInput"
    @close="visible = false"
    @closed="handleClosed"
  >
    <template v-if="currentActionDialog">
      <el-alert
        v-if="currentActionDialog.options.type"
        :title="currentActionDialog.message"
        :type="currentActionDialog.options.type"
        :closable="false"
        show-icon
      />
      <p v-else class="action-dialog-message">
        {{ currentActionDialog.message }}
      </p>
      <div v-if="currentActionDialog.options.details?.length" class="action-dialog-details">
        <p v-if="currentActionDialog.options.detailsCaption" class="action-dialog-details-caption">
          {{ currentActionDialog.options.detailsCaption }}
        </p>
        <ul class="action-dialog-details-list">
          <li
            v-for="(detail, index) in currentActionDialog.options.details"
            :key="`${detail.label}-${index}`"
            class="action-dialog-detail"
          >
            <span class="action-dialog-detail-label">{{ detail.label }}</span>
            <span class="action-dialog-detail-values">
              <template v-if="detail.before !== undefined">
                <span class="action-dialog-detail-before" :class="{ empty: !detail.before }">
                  {{ detail.before || '空' }}
                </span>
                <FaIcon name="i-lucide:arrow-right" class="action-dialog-detail-arrow" aria-hidden="true" />
              </template>
              <span class="action-dialog-detail-after" :class="{ empty: !detail.after }">
                {{ detail.after || '空' }}
              </span>
            </span>
          </li>
        </ul>
      </div>
      <div v-if="currentActionDialog.input" class="action-dialog-input">
        <el-input
          ref="inputControl"
          v-model="input"
          :type="currentActionDialog.input.inputType ?? 'text'"
          :placeholder="currentActionDialog.input.inputPlaceholder"
          :aria-label="currentActionDialog.message"
          :aria-invalid="Boolean(inputError)"
          @input="inputError = ''"
          @keydown.enter="confirmFromInput"
        />
        <p v-if="inputError" class="action-dialog-error" role="alert">
          {{ inputError }}
        </p>
      </div>
    </template>
    <template #footer>
      <el-button v-if="currentActionDialog?.options.extraButtonText" @click="chooseExtra">
        {{ currentActionDialog.options.extraButtonText }}
      </el-button>
      <el-button @click="visible = false">
        {{ currentActionDialog?.options.cancelButtonText ?? '取消' }}
      </el-button>
      <el-button type="primary" @click="confirm">
        {{ currentActionDialog?.options.confirmButtonText ?? '确定' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.action-dialog-message,
.action-dialog-error {
  margin: 0;
}

.action-dialog-input {
  margin-top: var(--mrr-space-3);
}

.action-dialog-details {
  margin-top: var(--mrr-space-3);
}

.action-dialog-details-caption {
  margin: 0 0 var(--mrr-space-2);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.action-dialog-details-list {
  display: grid;
  max-height: 44vh;
  padding: 0;
  margin: 0;
  overflow-y: auto;
  list-style: none;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.action-dialog-detail {
  display: grid;
  gap: 4px;
  padding: var(--mrr-space-2) var(--mrr-space-3);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.action-dialog-detail:last-child {
  border-bottom: 0;
}

.action-dialog-detail-label {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.action-dialog-detail-values {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: baseline;
  font-size: 13px;
  line-height: 1.5;
}

.action-dialog-detail-before,
.action-dialog-detail-after {
  overflow-wrap: anywhere;
}

.action-dialog-detail-before {
  color: var(--el-text-color-secondary);
}

.action-dialog-detail-after {
  font-weight: 500;
  color: var(--el-color-primary);
}

.action-dialog-detail-before.empty,
.action-dialog-detail-after.empty {
  font-style: italic;
  font-weight: 400;
  color: var(--el-text-color-placeholder);
}

.action-dialog-detail-arrow {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.action-dialog-error {
  margin-top: var(--mrr-space-1);
  color: var(--el-color-danger);
}
</style>
