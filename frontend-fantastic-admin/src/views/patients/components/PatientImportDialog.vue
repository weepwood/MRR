<script setup lang="ts">
import type { UploadFile, UploadFiles, UploadInstance, UploadRawFile, UploadUserFile } from 'element-plus'
import type { PatientImportResult } from '@/api/modules/patients'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, genFileId } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { importPatients } from '@/api/modules/patients'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'imported': [result: PatientImportResult]
}>()

const requiredFields = [
  { name: 'bah', description: '病案号，必填，保留前导零' },
  { name: 'name', description: '患者姓名' },
  { name: 'idcard', description: '身份证号' },
  { name: 'ruyuan', description: '入院日期：YYYY-MM-DD' },
  { name: 'admissiontime', description: '入院时间：YYYY-MM-DD HH:MM' },
  { name: 'department', description: '住院科室' },
  { name: 'bingqu', description: '病区，不能写成 binqu' },
  { name: 'chuangwei', description: '床位' },
]

const uploadRef = ref<UploadInstance>()
const fileList = ref<UploadUserFile[]>([])
const selectedFile = ref<File>()
const validationResult = ref<PatientImportResult>()
const validating = ref(false)
const importing = ref(false)

const dialogVisible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const canCommit = computed(() => {
  const result = validationResult.value
  return !!selectedFile.value && !!result?.canImport && result.dryRun && result.errorRows === 0
})

function resetState() {
  uploadRef.value?.clearFiles()
  fileList.value = []
  selectedFile.value = undefined
  validationResult.value = undefined
  validating.value = false
  importing.value = false
}

watch(() => props.modelValue, (visible) => {
  if (!visible) {
    resetState()
  }
})

function handleFileChange(uploadFile: UploadFile, uploadFiles: UploadFiles) {
  selectedFile.value = uploadFile.raw
  fileList.value = uploadFiles.slice(-1)
  validationResult.value = undefined
}

function handleFileRemove() {
  selectedFile.value = undefined
  validationResult.value = undefined
}

function handleExceed(files: File[]) {
  uploadRef.value?.clearFiles()
  const file = files[0] as UploadRawFile
  file.uid = genFileId()
  uploadRef.value?.handleStart(file)
}

async function validateFile() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择患者数据文件')
    return
  }

  validating.value = true
  try {
    const response = await importPatients(selectedFile.value, true)
    validationResult.value = response.data
    if (response.data?.canImport) {
      ElMessage.success('文件校验通过，可以确认导入')
    }
    else {
      ElMessage.warning('文件存在错误，请修正后重新上传')
    }
  }
  finally {
    validating.value = false
  }
}

async function commitImport() {
  if (!selectedFile.value || !canCommit.value) {
    return
  }

  importing.value = true
  try {
    const response = await importPatients(selectedFile.value, false)
    if (!response.data) {
      return
    }
    validationResult.value = response.data
    ElMessage.success(`导入完成，新增 ${response.data.insertedRows} 条，跳过重复 ${response.data.duplicateRows} 条`)
    emit('imported', response.data)
    dialogVisible.value = false
  }
  finally {
    importing.value = false
  }
}

function downloadTemplate() {
  const header = requiredFields.map(field => field.name).join(',')
  const example = '00789124,示例患者,330000199001011234,2026-07-01,2026-07-01 08:30,内科,一病区,12A'
  const blob = new Blob([`\uFEFF${header}\r\n${example}\r\n`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = '患者导入模板.csv'
  anchor.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="导入患者数据"
    width="min(920px, 92vw)"
    destroy-on-close
    :close-on-click-modal="!validating && !importing"
  >
    <div class="import-dialog">
      <el-alert
        title="系统会先校验文件，不会直接写入数据库"
        type="info"
        :closable="false"
        show-icon
      >
        <template #default>
          字段名必须与模板一致；文件内和数据库内的完全重复记录会被跳过；CSV 支持 UTF-8、UTF-16 和 GB18030 编码。
        </template>
      </el-alert>

      <section class="field-section">
        <div class="section-heading">
          <div>
            <h3>字段要求</h3>
            <p>八个字段均需出现在表头中，可额外保留 brxh、id、keshicode、bingqucode，额外字段不会入库。</p>
          </div>
          <el-button :icon="Download" @click="downloadTemplate">
            下载 CSV 模板
          </el-button>
        </div>
        <div class="field-grid">
          <div v-for="field in requiredFields" :key="field.name" class="field-item">
            <code>{{ field.name }}</code>
            <span>{{ field.description }}</span>
          </div>
        </div>
      </section>

      <el-upload
        ref="uploadRef"
        v-model:file-list="fileList"
        drag
        action="#"
        accept=".csv,.xlsx,.xls"
        :auto-upload="false"
        :limit="1"
        :disabled="validating || importing"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
        :on-exceed="handleExceed"
      >
        <el-icon class="el-icon--upload">
          <UploadFilled />
        </el-icon>
        <div class="el-upload__text">
          拖入患者文件，或<em>点击选择</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持 CSV / XLSX / XLS，单个文件不超过 20 MB，最多 100,000 行。
          </div>
        </template>
      </el-upload>

      <section v-if="validationResult" class="result-section">
        <div class="result-summary">
          <div class="summary-item">
            <span>文件编码</span>
            <strong>{{ validationResult.encoding }}</strong>
          </div>
          <div class="summary-item">
            <span>数据行</span>
            <strong>{{ validationResult.totalRows }}</strong>
          </div>
          <div class="summary-item">
            <span>有效唯一行</span>
            <strong>{{ validationResult.validRows }}</strong>
          </div>
          <div class="summary-item">
            <span>重复行</span>
            <strong>{{ validationResult.duplicateRows }}</strong>
          </div>
          <div class="summary-item" :class="{ danger: validationResult.errorRows > 0 }">
            <span>错误行</span>
            <strong>{{ validationResult.errorRows }}</strong>
          </div>
        </div>

        <el-alert
          v-if="validationResult.canImport"
          title="校验通过"
          type="success"
          :closable="false"
          show-icon
        >
          <template #default>
            确认导入后，系统只写入不存在的记录；重复记录不会再次入库。
          </template>
        </el-alert>
        <el-alert
          v-else
          title="校验未通过"
          type="error"
          :closable="false"
          show-icon
        >
          <template #default>
            请根据下方错误修改源文件，然后重新选择文件并校验。存在任何格式错误时不会写入数据库。
          </template>
        </el-alert>

        <el-table
          v-if="validationResult.errors.length"
          :data="validationResult.errors"
          max-height="260"
          border
        >
          <el-table-column prop="rowNumber" label="行号" width="80" />
          <el-table-column prop="field" label="字段" width="140" />
          <el-table-column prop="message" label="问题" min-width="260" />
          <el-table-column prop="value" label="原值（已脱敏）" min-width="180" show-overflow-tooltip />
        </el-table>
        <p v-if="validationResult.errorsTruncated" class="truncated-tip">
          错误较多，仅展示前 200 条。请先修复当前错误后重新校验。
        </p>
      </section>
    </div>

    <template #footer>
      <el-button :disabled="validating || importing" @click="dialogVisible = false">
        取消
      </el-button>
      <el-button type="primary" plain :loading="validating" :disabled="!selectedFile || importing" @click="validateFile">
        校验文件
      </el-button>
      <el-button type="primary" :loading="importing" :disabled="!canCommit || validating" @click="commitImport">
        确认导入
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.import-dialog {
  display: grid;
  gap: 18px;
}

.field-section,
.result-section {
  display: grid;
  gap: 14px;
}

.section-heading {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.section-heading h3 {
  margin: 0;
  font-size: 16px;
}

.section-heading p {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.field-item {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 8px 10px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.field-item code {
  flex: 0 0 112px;
  font-weight: 700;
  color: var(--el-color-primary);
}

.field-item span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.result-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.summary-item {
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.summary-item span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.summary-item strong {
  font-size: 20px;
}

.summary-item.danger strong {
  color: var(--el-color-danger);
}

.truncated-tip {
  margin: -4px 0 0;
  font-size: 12px;
  color: var(--el-color-warning);
}

@media (width <= 760px) {
  .section-heading {
    flex-direction: column;
  }

  .field-grid {
    grid-template-columns: 1fr;
  }

  .result-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
