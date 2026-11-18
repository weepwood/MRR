<script setup lang="ts">
import type { ElInput } from 'element-plus'
import { onBeforeUnmount, ref, useTemplateRef, watch } from 'vue'
import { cancelActionDialogs, currentActionDialog, finishActionDialog } from './index'

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
    width="min(480px, calc(100vw - 32px))"
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

.action-dialog-error {
  margin-top: var(--mrr-space-1);
  color: var(--el-color-danger);
}
</style>
