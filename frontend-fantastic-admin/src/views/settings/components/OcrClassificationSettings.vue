<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { getSystemSettings, saveSystemSettings } from '@/api/modules/settings'

interface OcrSettingsModel {
  ocrEnabled: boolean
  ocrProfile: string
  ocrLanguages: string
  ocrMaxConcurrency: number
  ocrPageTimeoutSeconds: number
  ocrMaxOutputBytes: number
  ocrAutoProcessNewScans: boolean
  ocrLowConfidenceThreshold: number
  classificationBatchReviewThreshold: number
}

const defaults: OcrSettingsModel = {
  ocrEnabled: false,
  ocrProfile: '',
  ocrLanguages: 'chi_sim+eng',
  ocrMaxConcurrency: 1,
  ocrPageTimeoutSeconds: 30,
  ocrMaxOutputBytes: 4 * 1024 * 1024,
  ocrAutoProcessNewScans: false,
  ocrLowConfidenceThreshold: 0.7,
  classificationBatchReviewThreshold: 0.92,
}

const settings = reactive<OcrSettingsModel>({ ...defaults })
const savedSnapshot = ref('')
const loading = ref(false)
const saving = ref(false)

function parseBoolean(value: unknown, fallback: boolean) {
  if (value === undefined || value === null || value === '') {
    return fallback
  }
  return ['true', '1', 'yes', 'on'].includes(String(value).trim().toLowerCase())
}

function parseNumber(value: unknown, fallback: number, min: number, max: number) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return fallback
  }
  return Math.min(max, Math.max(min, parsed))
}

function serialize() {
  return {
    ocrEnabled: String(settings.ocrEnabled),
    ocrProfile: settings.ocrProfile.trim(),
    ocrLanguages: settings.ocrLanguages.trim(),
    ocrMaxConcurrency: String(Math.round(settings.ocrMaxConcurrency)),
    ocrPageTimeoutSeconds: String(Math.round(settings.ocrPageTimeoutSeconds)),
    ocrMaxOutputBytes: String(Math.round(settings.ocrMaxOutputBytes)),
    ocrAutoProcessNewScans: String(settings.ocrAutoProcessNewScans),
    ocrLowConfidenceThreshold: settings.ocrLowConfidenceThreshold.toFixed(2),
    classificationBatchReviewThreshold: settings.classificationBatchReviewThreshold.toFixed(2),
  }
}

const isDirty = computed(() => JSON.stringify(serialize()) !== savedSnapshot.value)
const outputMegabytes = computed({
  get: () => Math.max(1, Math.round(settings.ocrMaxOutputBytes / 1024 / 1024)),
  set: value => settings.ocrMaxOutputBytes = Number(value || 1) * 1024 * 1024,
})

async function loadSettings() {
  loading.value = true
  try {
    const response = await getSystemSettings()
    const values = response.data || {}
    settings.ocrEnabled = parseBoolean(values.ocrEnabled, defaults.ocrEnabled)
    settings.ocrProfile = String(values.ocrProfile ?? defaults.ocrProfile).trim()
    settings.ocrLanguages = String(values.ocrLanguages ?? defaults.ocrLanguages).trim()
    settings.ocrMaxConcurrency = Math.round(parseNumber(values.ocrMaxConcurrency, defaults.ocrMaxConcurrency, 1, 4))
    settings.ocrPageTimeoutSeconds = Math.round(parseNumber(values.ocrPageTimeoutSeconds, defaults.ocrPageTimeoutSeconds, 5, 300))
    settings.ocrMaxOutputBytes = Math.round(parseNumber(values.ocrMaxOutputBytes, defaults.ocrMaxOutputBytes, 65536, 16777216))
    settings.ocrAutoProcessNewScans = parseBoolean(values.ocrAutoProcessNewScans, defaults.ocrAutoProcessNewScans)
    settings.ocrLowConfidenceThreshold = parseNumber(values.ocrLowConfidenceThreshold, defaults.ocrLowConfidenceThreshold, 0, 1)
    settings.classificationBatchReviewThreshold = parseNumber(
      values.classificationBatchReviewThreshold,
      defaults.classificationBatchReviewThreshold,
      0.9,
      1,
    )
    savedSnapshot.value = JSON.stringify(serialize())
  }
  catch (error: any) {
    ElMessage.error(error?.message || 'OCR 设置加载失败')
  }
  finally {
    loading.value = false
  }
}

function validate() {
  if (settings.ocrEnabled && !settings.ocrProfile.trim()) {
    ElMessage.warning('启用 OCR 前必须填写服务端白名单配置名称')
    return false
  }
  if (!/^[\w+,-]{1,64}$/.test(settings.ocrLanguages.trim())) {
    ElMessage.warning('OCR 识别语言格式不正确')
    return false
  }
  if (settings.classificationBatchReviewThreshold < 0.9) {
    ElMessage.warning('高置信度批量确认阈值不能低于 90%')
    return false
  }
  return true
}

async function saveSettings() {
  if (!validate()) {
    return false
  }
  saving.value = true
  try {
    await saveSystemSettings(serialize())
    savedSnapshot.value = JSON.stringify(serialize())
    ElMessage.success('OCR 与智能分类设置已保存')
    return true
  }
  catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || 'OCR 设置保存失败')
    return false
  }
  finally {
    saving.value = false
  }
}

function resetSettings() {
  Object.assign(settings, defaults)
}

defineExpose({ saving, isDirty, saveSettings, loadSettings, resetSettings })

onMounted(loadSettings)
</script>

<template>
  <section v-loading="loading" class="setting-section">
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="OCR 仅允许选择服务端预先配置的白名单名称，页面不会接受任意可执行程序路径或命令。"
    />

    <div class="switch-list">
      <div class="switch-row">
        <div>
          <strong>启用本地 OCR</strong>
          <p>默认关闭。启用后才允许创建单病案 OCR 和智能分类任务。</p>
        </div>
        <el-switch v-model="settings.ocrEnabled" />
      </div>
      <div class="switch-row">
        <div>
          <strong>新扫描自动 OCR</strong>
          <p>仅影响后续接入的新图片，不会启动历史图片全量识别。</p>
        </div>
        <el-switch v-model="settings.ocrAutoProcessNewScans" :disabled="!settings.ocrEnabled" />
      </div>
    </div>

    <div class="setting-group">
      <div class="group-heading">
        <strong>OCR 引擎与资源限制</strong>
        <p>服务端会再次校验全部范围，避免异常配置拖垮 JVM 或 Windows 服务器。</p>
      </div>
      <div class="control-grid">
        <el-form-item label="白名单配置名称">
          <el-input v-model="settings.ocrProfile" placeholder="例如 tesseract-local" maxlength="64" />
        </el-form-item>
        <el-form-item label="识别语言">
          <el-input v-model="settings.ocrLanguages" placeholder="chi_sim+eng" maxlength="64" />
        </el-form-item>
        <el-form-item label="最大并发">
          <el-input-number v-model="settings.ocrMaxConcurrency" :min="1" :max="4" controls-position="right" />
        </el-form-item>
        <el-form-item label="单页超时">
          <div class="number-control">
            <el-input-number v-model="settings.ocrPageTimeoutSeconds" :min="5" :max="300" controls-position="right" />
            <span>秒</span>
          </div>
        </el-form-item>
        <el-form-item label="最大输出">
          <div class="number-control">
            <el-input-number v-model="outputMegabytes" :min="1" :max="16" controls-position="right" />
            <span>MB</span>
          </div>
        </el-form-item>
        <el-form-item label="OCR 低质量阈值">
          <div class="number-control">
            <el-input-number v-model="settings.ocrLowConfidenceThreshold" :min="0" :max="1" :step="0.05" :precision="2" controls-position="right" />
            <span>{{ Math.round(settings.ocrLowConfidenceThreshold * 100) }}%</span>
          </div>
        </el-form-item>
        <el-form-item label="批量确认最低阈值">
          <div class="number-control">
            <el-input-number v-model="settings.classificationBatchReviewThreshold" :min="0.9" :max="1" :step="0.01" :precision="2" controls-position="right" />
            <span>{{ Math.round(settings.classificationBatchReviewThreshold * 100) }}%</span>
          </div>
        </el-form-item>
      </div>
    </div>

    <div class="setting-group">
      <div class="group-heading">
        <strong>OCR 字典与分类规则</strong>
        <p>字典采用草稿、发布、归档版本机制；运行中的任务固定使用启动时的发布版本。</p>
      </div>
      <div class="foundation-grid">
        <div class="foundation-card">
          <span>OCR 识别词库</span>
          <strong>数据表已建立</strong>
          <small>术语、别名和常见 OCR 错字管理界面将在下一阶段接入。</small>
        </div>
        <div class="foundation-card">
          <span>分类规则字典</span>
          <strong>数据表已建立</strong>
          <small>支持标题、正文、排除词、权重和优先级的规则管理界面将在下一阶段接入。</small>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.setting-section { display: grid; gap: var(--mrr-space-5); }

.setting-group,
.switch-list { padding: var(--mrr-space-5); background: var(--mrr-card); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-xl); }
.group-heading { margin-bottom: var(--mrr-space-5); }
.group-heading strong { font-size: 15px; }
.group-heading p { margin: 4px 0 0; font-size: 11px; color: var(--mrr-muted-foreground); }
.control-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--mrr-space-4); }
.number-control { display: flex; gap: var(--mrr-space-2); align-items: center; }
.switch-list { display: grid; gap: 0; padding-top: 0; padding-bottom: 0; }
.switch-row { display: flex; gap: var(--mrr-space-4); align-items: center; justify-content: space-between; min-height: 76px; border-bottom: 1px solid var(--mrr-border); }
.switch-row:last-child { border-bottom: 0; }
.switch-row strong { font-size: 13px; }
.switch-row p { margin: 3px 0 0; font-size: 10px; color: var(--mrr-muted-foreground); }

.foundation-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--mrr-space-4); }
.foundation-card { display: grid; gap: 6px; padding: var(--mrr-space-4); border: 1px dashed var(--mrr-border); border-radius: var(--mrr-radius-lg); }
.foundation-card span,
.foundation-card small { color: var(--mrr-muted-foreground); }
.foundation-card small { line-height: 1.6; }

@media (width <= 680px) {
  .control-grid,
  .foundation-grid { grid-template-columns: 1fr; }
}
</style>
