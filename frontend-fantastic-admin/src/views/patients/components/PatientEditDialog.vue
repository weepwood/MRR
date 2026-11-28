<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import type { PatientRecord, PatientUpdatePayload } from '@/api/modules/patients'
import { ElMessage } from 'element-plus'
import { onScopeDispose, reactive, ref, watch } from 'vue'
import { updatePatient } from '@/api/modules/patients'
import { useUserStore } from '@/store/modules/user'
import useAuth from '@/utils/composables/useAuth'

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
const userStore = useUserStore()
const { auth } = useAuth()
const allowed = () => userStore.isSessionVerified && auth('record:read') && auth('record:edit')
let generation = 0
let editEpoch: number | undefined

function invalidate() {
  generation++
  editEpoch = undefined
  saving.value = false
}
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
  invalidate()
  emit('update:modelValue', false)
}

async function submit() {
  if (saving.value || !props.modelValue || !allowed() || editEpoch !== userStore.sessionEpoch) { return }
  const id = props.patient?.id
  if (!id) {
    ElMessage.error('患者记录缺少 ID，无法保存')
    return
  }
  const token = generation
  const epoch = editEpoch
  const isCurrent = () => generation === token && userStore.sessionEpoch === epoch
    && props.modelValue && props.patient?.id === id && allowed()

  // Lock the entire validation + write sequence, not just the HTTP request.
  saving.value = true
  try {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid || !isCurrent()) { return }

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

    const response = await updatePatient(id, payload)
    if (!isCurrent() || !response.data) { return }
    ElMessage.success('患者信息保存成功')
    emit('saved', response.data)
    closeDialog()
  }
  catch {
    // The existing API layer reports current request failures. Keep the draft for retry.
  }
  finally {
    if (isCurrent()) { saving.value = false }
  }
}

watch(
  () => [props.modelValue, props.patient] as const,
  ([visible, patient]) => {
    invalidate()
    if (visible && allowed()) {
      editEpoch = userStore.sessionEpoch
      fillForm(patient)
    }
    else { fillForm() }
  },
  { immediate: true, flush: 'sync' },
)

watch([() => userStore.sessionEpoch, allowed], () => {
  invalidate()
  fillForm()
}, { flush: 'sync' })

onScopeDispose(invalidate)
</script>

<template>
  <el-dialog
    align-center
    :model-value="modelValue"
    title="编辑患者信息"
    width="min(720px, 92vw)"
    destroy-on-close
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    @close="closeDialog"
  >
    <el-alert
      title="保存后会立即更新患者列表和统计结果"
      type="info"
      show-icon
      :closable="false"
      class="edit-alert"
    />

    <el-form ref="formRef" :model="form" :rules="rules" :disabled="saving" label-position="top" class="mrr-edit-form mrr-edit-form--paired">
      <h3 class="mrr-edit-form__section">
        患者基本信息
      </h3>
      <el-form-item label="病案号" prop="bah">
        <el-input v-model="form.bah" clearable autocomplete="off" />
      </el-form-item>
      <el-form-item label="姓名" prop="name">
        <el-input v-model="form.name" clearable autocomplete="off" />
      </el-form-item>
      <el-form-item label="身份证号" prop="idCard" class="mrr-edit-form__full">
        <el-input v-model="form.idCard" clearable autocomplete="off" />
      </el-form-item>
      <h3 class="mrr-edit-form__section">
        入院信息
      </h3>
      <el-form-item label="入院日期" prop="ruyuan">
        <el-date-picker
          v-model="form.ruyuan"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择入院日期"
          clearable
        />
      </el-form-item>
      <el-form-item label="入院时间" prop="admissiontime">
        <el-date-picker
          v-model="form.admissiontime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="选择入院时间"
          clearable
        />
      </el-form-item>
      <h3 class="mrr-edit-form__section">
        科室与床位
      </h3>
      <el-form-item label="科室" prop="department">
        <el-input v-model="form.department" clearable autocomplete="off" />
      </el-form-item>
      <el-form-item label="病区" prop="bingqu">
        <el-input v-model="form.bingqu" clearable autocomplete="off" />
      </el-form-item>
      <el-form-item label="床位" prop="chuangwei">
        <el-input v-model="form.chuangwei" clearable autocomplete="off" />
      </el-form-item>
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
  margin-bottom: var(--mrr-space-4);
}
</style>
