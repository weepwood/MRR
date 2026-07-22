<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import type { PatientRecord, PatientUpdatePayload } from '@/api/modules/patients'
import { ElMessage } from 'element-plus'
import { reactive, ref, watch } from 'vue'
import { updatePatient } from '@/api/modules/patients'

interface PatientEditForm {
  bah: string
  name: string
  idCard: string
  ruyuan: string
  admissiontime: string
  department: string
  bingqu: string
  chuangwei: string
}

const props = defineProps<{
  modelValue: boolean
  patient?: PatientRecord
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'saved': [patient: PatientRecord]
}>()

const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive<PatientEditForm>({
  bah: '',
  name: '',
  idCard: '',
  ruyuan: '',
  admissiontime: '',
  department: '',
  bingqu: '',
  chuangwei: '',
})

const rules: FormRules<PatientEditForm> = {
  bah: [
    { required: true, message: '请输入病案号', trigger: 'blur' },
    { max: 2000, message: '病案号长度不能超过 2000 个字符', trigger: 'blur' },
  ],
  admissiontime: [
    {
      pattern: /^$|^\d{4}-\d{2}-\d{2} \d{2}:\d{2}(?::\d{2})?$/,
      message: '入院时间格式应为 YYYY-MM-DD HH:mm:ss',
      trigger: 'blur',
    },
  ],
}

function fillForm(patient?: PatientRecord) {
  form.bah = patient?.bah ?? ''
  form.name = patient?.name ?? ''
  form.idCard = patient?.idCard ?? ''
  form.ruyuan = patient?.ruyuan ?? ''
  form.admissiontime = patient?.admissiontime ?? ''
  form.department = patient?.department ?? ''
  form.bingqu = patient?.bingqu ?? ''
  form.chuangwei = patient?.chuangwei ?? ''
  formRef.value?.clearValidate()
}

function normalizeOptional(value: string) {
  const normalized = value.trim()
  return normalized || null
}

function closeDialog() {
  emit('update:modelValue', false)
}

async function submit() {
  if (!props.patient?.id) {
    ElMessage.error('患者记录缺少 ID，无法保存')
    return
  }
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  const payload: PatientUpdatePayload = {
    bah: form.bah.trim(),
    name: normalizeOptional(form.name),
    idCard: normalizeOptional(form.idCard),
    ruyuan: normalizeOptional(form.ruyuan),
    admissiontime: normalizeOptional(form.admissiontime),
    department: normalizeOptional(form.department),
    bingqu: normalizeOptional(form.bingqu),
    chuangwei: normalizeOptional(form.chuangwei),
  }

  saving.value = true
  try {
    const response = await updatePatient(props.patient.id, payload)
    if (!response.data) {
      return
    }
    ElMessage.success('患者信息保存成功')
    emit('saved', response.data)
    closeDialog()
  }
  finally {
    saving.value = false
  }
}

watch(
  () => [props.modelValue, props.patient] as const,
  ([visible]) => {
    if (visible) {
      fillForm(props.patient)
    }
  },
  { immediate: true },
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="编辑患者信息"
    width="min(720px, 92vw)"
    destroy-on-close
    :close-on-click-modal="false"
    @close="closeDialog"
  >
    <el-alert
      title="保存后会立即更新患者列表和统计结果"
      type="info"
      show-icon
      :closable="false"
      class="edit-alert"
    />

    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
      <div class="form-grid">
        <el-form-item label="病案号" prop="bah">
          <el-input v-model="form.bah" clearable autocomplete="off" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" clearable autocomplete="off" />
        </el-form-item>
        <el-form-item label="身份证号" prop="idCard" class="full-width">
          <el-input v-model="form.idCard" clearable autocomplete="off" />
        </el-form-item>
        <el-form-item label="入院日期" prop="ruyuan">
          <el-date-picker
            v-model="form.ruyuan"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择入院日期"
            clearable
            class="field-control"
          />
        </el-form-item>
        <el-form-item label="入院时间" prop="admissiontime">
          <el-date-picker
            v-model="form.admissiontime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择入院时间"
            clearable
            class="field-control"
          />
        </el-form-item>
        <el-form-item label="科室" prop="department">
          <el-input v-model="form.department" clearable autocomplete="off" />
        </el-form-item>
        <el-form-item label="病区" prop="bingqu">
          <el-input v-model="form.bingqu" clearable autocomplete="off" />
        </el-form-item>
        <el-form-item label="床位" prop="chuangwei">
          <el-input v-model="form.chuangwei" clearable autocomplete="off" />
        </el-form-item>
      </div>
    </el-form>

    <template #footer>
      <el-button :disabled="saving" @click="closeDialog">
        取消
      </el-button>
      <el-button type="primary" :loading="saving" @click="submit">
        保存修改
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.edit-alert {
  margin-bottom: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 18px;
}

.full-width {
  grid-column: 1 / -1;
}

.field-control {
  width: 100%;
}

@media (width <= 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .full-width {
    grid-column: auto;
  }
}
</style>
