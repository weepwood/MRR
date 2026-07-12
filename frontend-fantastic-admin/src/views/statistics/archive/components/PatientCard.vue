<script setup lang="ts">
import type { PatientInfo } from '../types'
import { User } from '@element-plus/icons-vue'

defineOptions({ name: 'PatientCard' })

const props = defineProps<{
  patient?: PatientInfo
  loading?: boolean
}>()

const fields = computed(() => [
  { label: '姓名', value: props.patient?.name || '-' },
  { label: '病案号', value: props.patient?.bah || '-' },
  { label: '科室', value: props.patient?.department || '-' },
  { label: '入院时间', value: props.patient?.admissionTime || '-' },
])
</script>

<template>
  <section v-if="props.patient || loading" v-loading="loading" class="patient-card">
    <div class="patient-card-header">
      <el-icon><User /></el-icon>
      <span>患者信息</span>
    </div>
    <div class="patient-card-body">
      <div v-for="field in fields" :key="field.label" class="patient-field">
        <span class="field-label">{{ field.label }}</span>
        <span class="field-value">{{ field.value }}</span>
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

.patient-card-header {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary);
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
}

.field-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.field-value {
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
