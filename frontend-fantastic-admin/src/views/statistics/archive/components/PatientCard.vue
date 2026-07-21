<script setup lang="ts">
import type { PatientInfo } from '../types'

defineOptions({ name: 'PatientCard' })

const props = defineProps<{
  patient?: PatientInfo
  sjh?: string
  loading?: boolean
}>()

const showShelfNumber = ref(false)

const fields = computed(() => [
  { key: 'name', label: '姓名', value: props.patient?.name || '-' },
  {
    key: 'record-number',
    label: showShelfNumber.value ? '上架号' : '病案号',
    value: showShelfNumber.value ? props.sjh || '-' : props.patient?.bah || '-',
    switchable: true,
  },
  { key: 'department', label: '科室', value: props.patient?.department || '-' },
  { key: 'admission-time', label: '入院时间', value: props.patient?.admissionTime || '-' },
])

function toggleRecordNumber() {
  showShelfNumber.value = !showShelfNumber.value
}
</script>

<template>
  <section v-if="props.patient || loading" v-loading="loading" class="patient-card">
    <div class="patient-card-body">
      <div v-for="field in fields" :key="field.key" class="patient-field">
        <button
          v-if="field.switchable"
          type="button"
          class="field-label field-label--switchable"
          :title="`点击切换为${showShelfNumber ? '病案号' : '上架号'}`"
          @click="toggleRecordNumber"
        >
          {{ field.label }}
          <FaIcon
            name="i-ri:repeat-line"
            class="field-switch-icon"
            :class="{ 'is-switched': showShelfNumber }"
          />
        </button>
        <span v-else class="field-label">{{ field.label }}</span>
        <span
          :key="field.switchable ? (showShelfNumber ? 'sjh' : 'bah') : field.key"
          class="field-value"
          :class="{ 'mrr-content-enter': field.switchable }"
          :title="field.value"
        >
          {{ field.value }}
        </span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.patient-card {
  padding: 14px;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 12px;
}

.patient-card-body {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 14px;
}

.patient-field {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.field-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.field-label--switchable {
  display: inline-flex;
  gap: 3px;
  align-items: center;
  width: max-content;
  padding: 0;
  cursor: pointer;
  background: transparent;
  border: 0;
  transition: color var(--mrr-motion-fast) ease;
}

.field-label--switchable:hover,
.field-label--switchable:focus-visible {
  color: var(--el-color-primary);
}

.field-label--switchable:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: 2px;
}

.field-switch-icon {
  transform-origin: center;
  transition: transform var(--mrr-motion-normal) var(--mrr-ease-out);
}

.field-switch-icon.is-switched {
  transform: rotate(180deg);
}

.field-label--switchable:active .field-switch-icon {
  transform: scale(0.9) rotate(180deg);
}

.field-label--switchable:active .field-switch-icon:not(.is-switched) {
  transform: scale(0.9);
}

.field-value {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
}

@media (prefers-reduced-motion: reduce) {
  .field-label--switchable,
  .field-switch-icon {
    transition: none;
  }

  .field-switch-icon.is-switched,
  .field-label--switchable:active .field-switch-icon,
  .field-label--switchable:active .field-switch-icon:not(.is-switched) {
    transform: none;
  }
}
</style>
