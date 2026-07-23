<script setup lang="ts">
import type { UploadFile, UploadFiles, UploadInstance, UploadRawFile, UploadUserFile } from 'element-plus'
import type { DataExchangeDataset, DataExchangeImportResult } from '@/api/modules/data-exchange'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, genFileId } from 'element-plus'
import { computed, ref } from 'vue'
import {
  downloadDataExchangeTemplate,
  importDataExchangeFile,
} from '@/api/modules/data-exchange'

interface FieldDefinition {
  name: string
  description: string
}

const props = defineProps<{
  dataset: DataExchangeDataset
  title: string
  description: string
  fields: FieldDefinition[]
  maxFileSize: string
}>()

const emit = defineEmits<{
  imported: [result: DataExchangeImportResult]
}>()

const uploadRef = ref<UploadInstance>()
const fileList = ref<UploadUserFile[]>([])
const selectedFile = ref<File>()
const result = ref<DataExchangeImportResult>()
const validating = ref(false)
const importing = ref(false)
const downloadingTemplate = ref(false)

const canCommit = computed(() => {
  return !!selectedFile.value
    && !!result.value?.canImport
    && result.value.dryRun
    && result.value.errorRows === 0
})

function resetResult() {
  result.value = undefined
}

function handleFileChange(uploadFile: UploadFile, uploadFiles: UploadFiles) {
  selectedFile.value = uploadFile.raw
  fileList.value = uploadFiles.slice(-1)
  resetResult()
}

function handleFileRemove() {
  selectedFile.value = undefined
  resetResult()
}

function handleExceed(files: File[]) {
  uploadRef.value?.clearFiles()
  const file = files[0] as UploadRawFile
  file.uid = genFileId()
  uploadRef.value?.handleStart(file)
}

async function validateFile() {
  if (!selectedFile.value) {
    ElMessage.warning(`请先选择${props.title}文件`)
    return
  }
  validating.value = true
  try {
    const response = await importDataExchangeFile(props.dataset, selectedFile.value, true)
    result.value = response.data
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
    const response = await importDataExchangeFile(props.dataset, selectedFile.value, false)
    if (!response.data) {
      return
    }
    result.value = response.data
    ElMessage.success(
      `导入完成：新增 ${response.data.insertedRows} 条，更新 ${response.data.updatedRows} 条，跳过重复 ${response.data.duplicateRows} 条`,
    )
    emit('imported', response.data)
  }
  finally {
    importing.value = false
  }
}

async function downloadTemplate() {
  downloadingTemplate.value = true
  try {
    const response = await downloadDataExchangeTemplate(props.dataset)
    downloadBlob(response.data, `${props.dataset}-template.csv`)
  }
  finally {
    downloadingTemplate.value = false
  }
}

function downloadBlob(blobValue: Blob, fileName: string) {
  const blob = blobValue instanceof Blob ? blobValue : new Blob([blobValue])
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <el-card shadow="never" class="dataset-card">
    <template #header>
      <div class="panel-heading">
        <div>
          <h3>{{ title }}</h3>
          <p>{{ description }}</p>
        </div>
        <el-button :icon="Download" :loading="downloadingTemplate" @click="downloadTemplate">
          下载模板
        </el-button>
      </div>
    </template>

    <div class="panel-content">
      <el-alert
        title="先校验，后写入"
        type="info"
        show-icon
        :closable="false"
      >
        <template #default>
          表头必须与模板一致。存在格式或关联错误时，本次文件不会写入数据库。
        </template>
      </el-alert>

      <div class="field-grid">
        <div v-for="field in fields" :key="field.name" class="field-item">
          <code>{{ field.name }}</code>
          <span>{{ field.description }}</span>
        </div>
      </div>

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
          拖入数据文件，或<em>点击选择</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持 CSV / XLSX / XLS，{{ maxFileSize }}，最多 100,000 行。CSV 支持 UTF-8、UTF-16 和 GB18030。
          </div>
        </template>
      </el-upload>

      <div class="action-row">
        <el-button
          type="primary"
          plain
          :loading="validating"
          :disabled="!selectedFile || importing"
          @click="validateFile"
        >
          校验文件
        </el-button>
        <el-button
          type="primary"
          :loading="importing"
          :disabled="!canCommit || validating"
          @click="commitImport"
        >
          确认导入
        </el-button>
      </div>

      <section v-if="result" class="result-section">
        <div class="result-summary">
          <div class="summary-item">
            <span>文件编码</span>
            <strong>{{ result.encoding }}</strong>
          </div>
          <div class="summary-item">
            <span>数据行</span>
            <strong>{{ result.totalRows }}</strong>
          </div>
          <div class="summary-item">
            <span>有效唯一行</span>
            <strong>{{ result.validRows }}</strong>
          </div>
          <div class="summary-item">
            <span>{{ result.dryRun ? '预计新增' : '实际新增' }}</span>
            <strong>{{ result.insertedRows }}</strong>
          </div>
          <div class="summary-item">
            <span>{{ result.dryRun ? '预计更新' : '实际更新' }}</span>
            <strong>{{ result.updatedRows }}</strong>
          </div>
          <div class="summary-item">
            <span>重复跳过</span>
            <strong>{{ result.duplicateRows }}</strong>
          </div>
          <div class="summary-item" :class="{ danger: result.errorRows > 0 }">
            <span>错误行</span>
            <strong>{{ result.errorRows }}</strong>
          </div>
        </div>

        <el-alert
          v-if="result.canImport"
          title="校验通过"
          type="success"
          show-icon
          :closable="false"
        >
          <template #default>
            确认导入时会按业务主键更新已有记录，并跳过完全重复的数据。
          </template>
        </el-alert>
        <el-alert
          v-else
          title="校验未通过"
          type="error"
          show-icon
          :closable="false"
        >
          <template #default>
            请根据错误行号修正源文件后重新上传。当前文件不会写入数据库。
          </template>
        </el-alert>

        <el-table v-if="result.errors.length" :data="result.errors" max-height="280" border>
          <el-table-column prop="rowNumber" label="行号" width="80" />
          <el-table-column prop="field" label="字段" width="150" />
          <el-table-column prop="message" label="问题" min-width="280" />
          <el-table-column prop="value" label="原值（敏感值已脱敏）" min-width="190" show-overflow-tooltip />
        </el-table>
        <p v-if="result.errorsTruncated" class="truncated-tip">
          错误较多，仅展示前 200 条。请先修复当前错误后重新校验。
        </p>
      </section>
    </div>
  </el-card>
</template>

<style scoped>
.dataset-card {
  min-width: 0;
}

.panel-content,
.result-section {
  display: grid;
  gap: 16px;
}

.panel-heading {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.panel-heading h3 {
  margin: 0;
  font-size: 18px;
}

.panel-heading p {
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
  flex: 0 0 128px;
  font-weight: 700;
  color: var(--el-color-primary);
}

.field-item span {
  min-width: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.action-row {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.result-summary {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
}

.summary-item {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.summary-item span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.summary-item strong {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 18px;
  white-space: nowrap;
}

.summary-item.danger strong {
  color: var(--el-color-danger);
}

.truncated-tip {
  margin: -4px 0 0;
  font-size: 12px;
  color: var(--el-color-warning);
}

@media (width <= 1100px) {
  .result-summary {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (width <= 760px) {
  .panel-heading {
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
