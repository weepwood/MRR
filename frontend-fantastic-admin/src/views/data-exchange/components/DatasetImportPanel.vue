<script setup lang="ts">
import type { UploadFile, UploadFiles, UploadInstance, UploadRawFile, UploadUserFile } from 'element-plus'
import type { DataExchangeDataset, DataExchangeImportResult } from '@/api/modules/data-exchange'
import { Download, InfoFilled, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, genFileId } from 'element-plus'
import { computed, ref } from 'vue'
import {
  downloadDataExchangeTemplate,
  importDataExchangeFile,
} from '@/api/modules/data-exchange'
import MrrSectionCard from '@/components/MrrSectionCard/index.vue'
import { createCsvBlob, downloadBlob, downloadResponseBlob } from '@/utils/file-download'
import FieldDictionary from './FieldDictionary.vue'

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

const errorReportLabel = computed(() => {
  return result.value?.errorsTruncated ? '下载当前错误明细' : '下载错误报告'
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
  if (!selectedFile.value || !canCommit.value || !result.value) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `将写入“${props.title}”：预计新增 ${result.value.insertedRows} 条、更新 ${result.value.updatedRows} 条、跳过重复 ${result.value.duplicateRows} 条。系统会在写入前再次执行完整校验。`,
      '确认正式导入',
      {
        confirmButtonText: '确认写入',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  }
  catch {
    return
  }

  importing.value = true
  try {
    const response = await importDataExchangeFile(props.dataset, selectedFile.value, false)
    if (!response.data) {
      return
    }
    result.value = response.data
    if (!response.data.canImport || response.data.errorRows > 0) {
      ElMessage.warning('正式导入前的再次校验未通过，本次未写入数据库')
      return
    }
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
    downloadResponseBlob(response, `${props.dataset}-template.csv`)
  }
  finally {
    downloadingTemplate.value = false
  }
}

function downloadErrorReport() {
  if (!result.value?.errors.length) {
    return
  }

  const report = createCsvBlob(
    ['rowNumber', 'field', 'message', 'value'],
    result.value.errors.map(error => [
      error.rowNumber,
      error.field,
      error.message,
      error.value,
    ]),
  )
  downloadBlob(report, `${props.dataset}-validation-errors-${today()}.csv`)
}

function today() {
  return new Date().toISOString().slice(0, 10)
}
</script>

<template>
  <MrrSectionCard :title="title" :description="description">
    <template #title-suffix>
      <el-tooltip placement="top" :show-after="200">
        <template #content>
          <div class="import-safety-tooltip">
            <strong>先校验，后写入</strong>
            <p>表头必须与模板一致。存在格式或关联错误时，本次文件不会写入数据库。</p>
          </div>
        </template>
        <button
          type="button"
          class="import-safety-trigger"
          aria-label="导入校验说明：先校验，后写入。表头必须与模板一致。存在格式或关联错误时，本次文件不会写入数据库。"
        >
          <el-icon><InfoFilled /></el-icon>
        </button>
      </el-tooltip>
    </template>

    <template #actions>
      <el-button :icon="Download" :loading="downloadingTemplate" @click="downloadTemplate">
        下载模板
      </el-button>
    </template>

    <div class="panel-content">
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
        <div class="result-head">
          <h3 class="result-head__title">
            {{ result.dryRun ? '校验结果' : '导入结果' }}
          </h3>
          <span class="result-head__meta">文件编码 {{ result.encoding }}</span>
        </div>

        <dl class="result-metrics">
          <div class="result-metrics__item">
            <dt>数据行</dt>
            <dd>{{ result.totalRows }}</dd>
          </div>
          <div class="result-metrics__item">
            <dt>有效唯一行</dt>
            <dd>{{ result.validRows }}</dd>
          </div>
          <div class="result-metrics__item">
            <dt>{{ result.dryRun ? '预计新增' : '实际新增' }}</dt>
            <dd>{{ result.insertedRows }}</dd>
          </div>
          <div class="result-metrics__item">
            <dt>{{ result.dryRun ? '预计更新' : '实际更新' }}</dt>
            <dd>{{ result.updatedRows }}</dd>
          </div>
          <div class="result-metrics__item">
            <dt>重复跳过</dt>
            <dd>{{ result.duplicateRows }}</dd>
          </div>
          <div class="result-metrics__item" :class="{ 'result-metrics__item--danger': result.errorRows > 0 }">
            <dt>错误行</dt>
            <dd>{{ result.errorRows }}</dd>
          </div>
        </dl>

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

        <div v-if="result.errors.length" class="result-actions">
          <el-button :icon="Download" @click="downloadErrorReport">
            {{ errorReportLabel }}
          </el-button>
        </div>

        <el-table v-if="result.errors.length" :data="result.errors" max-height="280" border>
          <el-table-column prop="rowNumber" label="行号" width="80" />
          <el-table-column prop="field" label="字段" width="150" />
          <el-table-column prop="message" label="问题" min-width="280" />
          <el-table-column prop="value" label="原值（敏感值已脱敏）" min-width="190" show-overflow-tooltip />
        </el-table>
        <p v-if="result.errorsTruncated" class="truncated-tip">
          错误较多，页面与下载文件仅包含后端返回的前 200 条脱敏明细；请先修复当前错误后重新校验。
        </p>
      </section>

      <FieldDictionary title="导入字段说明" :fields="fields" />
    </div>
  </MrrSectionCard>
</template>

<style scoped>
.panel-content,
.result-section {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.import-safety-trigger {
  display: inline-flex;
  flex: 0 0 18px;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  color: var(--mrr-muted-foreground);
  cursor: help;
  background: transparent;
  border: 0;
  border-radius: var(--mrr-radius-pill);
}

.import-safety-trigger:hover,
.import-safety-trigger:focus-visible {
  color: var(--mrr-primary);
}

.import-safety-trigger:focus-visible {
  outline: 2px solid var(--mrr-primary);
  outline-offset: 2px;
}

.import-safety-tooltip {
  max-width: 300px;
  line-height: 1.5;
}

.import-safety-tooltip p {
  margin: 4px 0 0;
}

.action-row,
.result-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.action-row :deep(.el-button + .el-button),
.result-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

/* 结果标题行：指标条取代原先 7 个描边小方块，文件编码降级为标题右侧说明。 */
.result-head {
  display: flex;
  gap: 12px;
  align-items: baseline;
  justify-content: space-between;
  min-width: 0;
}

.result-head__title {
  margin: 0;
  font-size: 13px;
  font-weight: 650;
  color: var(--mrr-foreground);
}

.result-head__meta {
  font-size: 12px;
  color: var(--mrr-muted-foreground);
}

/* 1px gap + 底色做分隔线：任意列数都能得到均匀的细分隔，无需按 nth-child 补边框。 */
.result-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1px;
  margin: 0;
  overflow: hidden;
  background: var(--mrr-shell-divider);
  border-radius: var(--mrr-radius-md);
}

.result-metrics__item {
  display: grid;
  gap: 3px;
  min-width: 0;
  padding: 9px 12px;
  background: var(--mrr-muted);
}

.result-metrics dt {
  font-size: 11px;
  line-height: 1.4;
  color: var(--mrr-muted-foreground);
}

.result-metrics dd {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 16px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  line-height: 1.3;
  color: var(--mrr-foreground);
  white-space: nowrap;
}

.result-metrics__item--danger dd {
  color: var(--el-color-danger);
}

.truncated-tip {
  margin: -4px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-color-warning);
}

@media (min-width: 1600px) {
  .result-metrics {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }
}

@media (max-width: 520px) {
  .result-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .action-row,
  .result-actions {
    flex-direction: column;
  }

  .action-row :deep(.el-button),
  .result-actions :deep(.el-button) {
    width: 100%;
  }
}
</style>
