<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { saveSystemSettings } from '@/api/modules/settings'
import {
  createDefaultSystemSettings,
  loadEffectiveSystemSettings,
  serializeSystemSettings,
  type EffectiveSystemSettings,
  type SettingsSource,
  writeLocalSystemSettings,
} from '@/utils/system-settings'
import AppConfigPanel from './components/AppConfigPanel.vue'
import DepartmentThemeSettings from './components/DepartmentThemeSettings.vue'

defineOptions({ name: 'SettingsPage' })

const activeTab = ref<'system' | 'department' | 'app'>('system')
const loading = ref(false)
const saving = ref(false)
const settingsSource = ref<SettingsSource>('default')
const lastSyncedAt = ref('')
const savedSnapshot = ref<Record<string, string>>({})
const settings = reactive<EffectiveSystemSettings>(createDefaultSystemSettings())

const sourceMeta = computed(() => ({
  server: { label: '服务器配置', type: 'success' as const },
  local: { label: '本地配置', type: 'warning' as const },
  default: { label: '默认配置', type: 'info' as const },
})[settingsSource.value])

const currentSnapshot = computed(() => serializeSystemSettings(settings))
const changedKeys = computed(() => Object.keys(currentSnapshot.value).filter(
  key => currentSnapshot.value[key] !== savedSnapshot.value[key],
))
const isDirty = computed(() => changedKeys.value.length > 0)

function markAsSaved() {
  savedSnapshot.value = { ...currentSnapshot.value }
}

function updateSyncTime() {
  lastSyncedAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

async function loadSettings(showMessage = false) {
  loading.value = true
  try {
    const result = await loadEffectiveSystemSettings()
    Object.assign(settings, result.settings)
    settingsSource.value = result.source
    markAsSaved()
    updateSyncTime()
    if (showMessage) {
      ElMessage.success('设置已重新加载')
    }
  }
  finally {
    loading.value = false
  }
}

function validateSettings() {
  if (!settings.systemName.trim()) {
    ElMessage.warning('系统名称不能为空')
    return false
  }
  return true
}

async function handleSave() {
  if (!validateSettings()) {
    return
  }

  saving.value = true
  const payload = serializeSystemSettings(settings)
  try {
    await saveSystemSettings(payload)
    settingsSource.value = 'server'
    ElMessage.success('设置已保存，并已通知对应功能更新')
  }
  catch {
    settingsSource.value = 'local'
    ElMessage.warning('服务端保存失败，已保存到当前浏览器')
  }
  finally {
    writeLocalSystemSettings(settings)
    markAsSaved()
    updateSyncTime()
    saving.value = false
  }
}

function handleReset() {
  Object.assign(settings, createDefaultSystemSettings())
  ElMessage.info('已恢复默认值，保存后生效')
}

async function handleReload() {
  if (isDirty.value) {
    try {
      await ElMessageBox.confirm(
        '重新加载会丢弃当前未保存修改，是否继续？',
        '重新加载设置',
        {
          type: 'warning',
          confirmButtonText: '继续加载',
          cancelButtonText: '取消',
        },
      )
    }
    catch {
      return
    }
  }
  await loadSettings(true)
}

onMounted(() => loadSettings())
</script>

<template>
  <div class="settings-page">
    <header class="page-header">
      <div class="header-title">
        <span class="header-icon">
          <FaIcon name="i-ri:settings-4-line" />
        </span>
        <div>
          <h2>系统设置</h2>
          <p>这里只展示已经接入实际功能的配置，保存后会由对应页面读取并应用。</p>
        </div>
      </div>
      <div v-if="activeTab === 'system'" class="header-actions">
        <el-button :disabled="loading || saving" @click="handleReload">
          <FaIcon name="i-ri:refresh-line" />
          重新加载
        </el-button>
        <el-button :disabled="loading || saving" @click="handleReset">
          <FaIcon name="i-ri:restart-line" />
          恢复默认
        </el-button>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="settings-tabs">
      <el-tab-pane name="system">
        <template #label>
          <span class="tab-label">
            <FaIcon name="i-ri:equalizer-2-line" />
            系统与档案
          </span>
        </template>

        <div v-loading="loading" class="system-panel">
          <div class="settings-summary">
            <div>
              <span class="summary-label">配置来源</span>
              <el-tag :type="sourceMeta.type" effect="light" round>
                {{ sourceMeta.label }}
              </el-tag>
            </div>
            <div>
              <span class="summary-label">保存状态</span>
              <strong :class="{ warning: isDirty }">
                {{ isDirty ? `${changedKeys.length} 项待保存` : '已同步' }}
              </strong>
            </div>
            <div>
              <span class="summary-label">最近同步</span>
              <strong>{{ lastSyncedAt || '尚未同步' }}</strong>
            </div>
          </div>

          <el-alert
            title="当前设置均已接入对应功能"
            description="系统名称会更新浏览器标题；档案浏览设置会在影像档案袋中生效；水印开关与透明度支持即时更新。"
            type="success"
            :closable="false"
            show-icon
          />

          <section class="setting-section">
            <header class="section-header">
              <span class="section-index">01</span>
              <div>
                <h3>系统标识</h3>
                <p>控制浏览器标签页显示的系统名称。</p>
              </div>
              <el-tag effect="plain" round>全局生效</el-tag>
            </header>

            <div class="section-body single-column">
              <el-form label-position="top" :model="settings">
                <el-form-item label="系统名称">
                  <el-input
                    v-model="settings.systemName"
                    maxlength="40"
                    show-word-limit
                    placeholder="例如 MRR 后台管理中心"
                  />
                  <p class="field-help">保存后立即更新当前浏览器标签页标题。</p>
                </el-form-item>
              </el-form>
            </div>
          </section>

          <section class="setting-section">
            <header class="section-header">
              <span class="section-index">02</span>
              <div>
                <h3>影像档案袋浏览</h3>
                <p>控制进入档案袋后的默认布局、图片加载和选择行为。</p>
              </div>
              <el-tag effect="plain" round>档案页面</el-tag>
            </header>

            <div class="subsection">
              <div class="subsection-title">
                <strong>默认打开方式</strong>
                <span>每次进入影像档案袋时应用</span>
              </div>
              <div class="form-grid">
                <el-form-item label="左侧影像列表">
                  <el-segmented
                    v-model="settings.archiveDefaultView"
                    :options="[
                      { label: '缩略图', value: 'thumb' },
                      { label: '列表', value: 'list' },
                    ]"
                    class="full-width"
                  />
                </el-form-item>
                <el-form-item label="右侧预览模式">
                  <el-segmented
                    v-model="settings.archivePreviewMode"
                    :options="[
                      { label: '单页', value: 'single' },
                      { label: '连续滚动', value: 'scroll' },
                    ]"
                    class="full-width"
                  />
                </el-form-item>
              </div>
            </div>

            <div class="subsection">
              <div class="subsection-title">
                <strong>缩略图与加载</strong>
                <span>直接影响档案袋中间影像栏</span>
              </div>
              <div class="form-grid">
                <el-form-item label="缩略图目标宽度">
                  <div class="slider-control">
                    <el-slider
                      v-model="settings.archiveThumbnailSize"
                      :min="160"
                      :max="320"
                      :step="20"
                      show-stops
                    />
                    <span>{{ settings.archiveThumbnailSize }} px</span>
                  </div>
                </el-form-item>
                <el-form-item label="首批渲染数量">
                  <div class="number-control">
                    <el-input-number
                      v-model="settings.archivePreloadCount"
                      :min="10"
                      :max="100"
                      :step="10"
                      controls-position="right"
                    />
                    <span>张</span>
                  </div>
                </el-form-item>
              </div>
            </div>

            <div class="switch-group">
              <div class="switch-row">
                <div>
                  <strong>图片自动适应预览区域</strong>
                  <p>开启时完整显示图片；关闭时按原始尺寸展示并允许滚动查看。</p>
                </div>
                <el-switch v-model="settings.archiveAutoFit" inline-prompt active-text="开" inactive-text="关" />
              </div>
              <div class="switch-row">
                <div>
                  <strong>记住档案选择状态</strong>
                  <p>重新进入同一病案号和上架号时，恢复上次选中的影像页。</p>
                </div>
                <el-switch v-model="settings.archiveRememberSelection" inline-prompt active-text="开" inactive-text="关" />
              </div>
            </div>
          </section>

          <section class="setting-section">
            <header class="section-header">
              <span class="section-index">03</span>
              <div>
                <h3>访问水印</h3>
                <p>控制影像档案袋上的用户 ID 与访问时间水印。</p>
              </div>
              <el-tag effect="plain" round>即时更新</el-tag>
            </header>

            <div class="watermark-settings">
              <div class="switch-row watermark-switch">
                <div>
                  <strong>显示访问水印</strong>
                  <p>关闭后立即移除档案页面水印，重新开启后立即恢复。</p>
                </div>
                <el-switch v-model="settings.archiveWatermarkEnabled" inline-prompt active-text="开" inactive-text="关" />
              </div>
              <el-form-item label="水印透明度">
                <div class="slider-control">
                  <el-slider
                    v-model="settings.archiveWatermarkOpacity"
                    :min="5"
                    :max="35"
                    :step="1"
                    :disabled="!settings.archiveWatermarkEnabled"
                  />
                  <span>{{ settings.archiveWatermarkOpacity }}%</span>
                </div>
              </el-form-item>
            </div>
          </section>

          <footer class="save-bar" :class="{ dirty: isDirty }">
            <div class="save-status">
              <span class="status-dot" />
              <div>
                <strong>{{ isDirty ? `${changedKeys.length} 项修改尚未保存` : '当前设置已同步' }}</strong>
                <small>{{ isDirty ? '保存后会通知对应功能立即刷新或在下次进入时应用。' : `当前使用${sourceMeta.label}。` }}</small>
              </div>
            </div>
            <el-button
              type="primary"
              :loading="saving"
              :disabled="loading || !isDirty"
              @click="handleSave"
            >
              <FaIcon name="i-ri:save-3-line" />
              保存设置
            </el-button>
          </footer>
        </div>
      </el-tab-pane>

      <el-tab-pane name="department">
        <template #label>
          <span class="tab-label">
            <FaIcon name="i-ri:palette-line" />
            科室配色
          </span>
        </template>
        <DepartmentThemeSettings />
      </el-tab-pane>

      <el-tab-pane name="app">
        <template #label>
          <span class="tab-label">
            <FaIcon name="i-ri:layout-4-line" />
            应用外观
          </span>
        </template>
        <AppConfigPanel />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.settings-page {
  display: grid;
  gap: 18px;
}

.page-header {
  display: flex;
  gap: 24px;
  align-items: center;
  justify-content: space-between;
  padding: 22px 24px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
}

.header-title {
  display: flex;
  gap: 15px;
  align-items: center;
  min-width: 0;
}

.header-icon {
  display: grid;
  flex: 0 0 46px;
  width: 46px;
  height: 46px;
  font-size: 22px;
  color: var(--el-color-primary);
  background: color-mix(in srgb, var(--el-color-primary) 11%, var(--el-bg-color));
  border-radius: 13px;
  place-items: center;
}

.page-header h2 {
  margin: 0;
  font-size: 25px;
  color: var(--el-text-color-primary);
}

.page-header p {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.header-actions,
.tab-label {
  display: flex;
  gap: 8px;
  align-items: center;
}

.header-actions :deep(.el-button) {
  margin-left: 0;
}

.settings-tabs {
  padding: 0 22px 22px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
}

.settings-tabs :deep(.el-tabs__header) {
  margin-bottom: 20px;
}

.settings-tabs :deep(.el-tabs__item) {
  height: 56px;
  padding: 0 20px;
}

.tab-label {
  font-weight: 600;
}

.system-panel {
  display: grid;
  gap: 16px;
  min-height: 420px;
}

.settings-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 28px;
  align-items: center;
  padding: 13px 16px;
  background: var(--el-fill-color-extra-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.settings-summary > div {
  display: flex;
  gap: 9px;
  align-items: center;
}

.summary-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.settings-summary strong {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.settings-summary strong.warning {
  color: var(--el-color-warning-dark-2);
}

.setting-section {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
}

.section-header {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding: 18px 20px;
  background: var(--el-fill-color-extra-light);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.section-index {
  display: grid;
  width: 38px;
  height: 38px;
  font-size: 12px;
  font-weight: 800;
  color: var(--el-color-primary);
  background: color-mix(in srgb, var(--el-color-primary) 10%, var(--el-bg-color));
  border-radius: 11px;
  place-items: center;
}

.section-header h3 {
  margin: 0;
  font-size: 17px;
  color: var(--el-text-color-primary);
}

.section-header p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.section-body,
.subsection,
.switch-group,
.watermark-settings {
  padding: 20px;
}

.single-column {
  max-width: 720px;
}

.subsection + .subsection,
.subsection + .switch-group {
  border-top: 1px dashed var(--el-border-color-lighter);
}

.subsection-title {
  display: flex;
  gap: 12px;
  align-items: baseline;
  margin-bottom: 16px;
}

.subsection-title strong {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.subsection-title span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

:deep(.el-form-item) {
  margin-bottom: 0;
}

:deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.full-width,
.number-control :deep(.el-input-number) {
  width: 100%;
}

.field-help {
  margin: 7px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.slider-control,
.number-control {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 60px;
  gap: 14px;
  align-items: center;
  width: 100%;
}

.slider-control > span,
.number-control > span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: right;
}

.switch-group {
  display: grid;
  gap: 10px;
  padding-top: 14px;
}

.switch-row {
  display: flex;
  gap: 18px;
  align-items: center;
  justify-content: space-between;
  padding: 14px 15px;
  background: var(--el-fill-color-extra-light);
  border: 1px solid transparent;
  border-radius: 11px;
}

.switch-row:hover {
  border-color: var(--el-border-color-lighter);
}

.switch-row strong {
  display: block;
  margin-bottom: 4px;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.switch-row p {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
  color: var(--el-text-color-secondary);
}

.watermark-settings {
  display: grid;
  gap: 18px;
}

.watermark-switch {
  padding: 0 0 18px;
  background: transparent;
  border-bottom: 1px dashed var(--el-border-color-lighter);
  border-radius: 0;
}

.watermark-settings :deep(.el-form-item) {
  max-width: 720px;
}

.save-bar {
  position: sticky;
  bottom: 14px;
  z-index: 3;
  display: flex;
  gap: 18px;
  align-items: center;
  justify-content: space-between;
  padding: 13px 16px;
  background: color-mix(in srgb, var(--el-bg-color) 94%, transparent);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 13px;
  box-shadow: 0 10px 28px rgb(15 23 42 / 9%);
  backdrop-filter: blur(14px);
}

.save-bar.dirty {
  border-color: color-mix(in srgb, var(--el-color-warning) 35%, var(--el-border-color-lighter));
}

.save-status {
  display: flex;
  gap: 11px;
  align-items: center;
  min-width: 0;
}

.save-status strong,
.save-status small {
  display: block;
}

.save-status strong {
  margin-bottom: 3px;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.save-status small {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.status-dot {
  width: 9px;
  height: 9px;
  background: var(--el-color-success);
  border-radius: 50%;
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--el-color-success) 13%, transparent);
}

.save-bar.dirty .status-dot {
  background: var(--el-color-warning);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--el-color-warning) 15%, transparent);
}

.save-bar :deep(.el-button) {
  gap: 6px;
}

@media (max-width: 760px) {
  .page-header,
  .save-bar,
  .switch-row {
    align-items: stretch;
  }

  .page-header,
  .header-actions,
  .save-bar,
  .switch-row {
    flex-direction: column;
  }

  .header-actions :deep(.el-button),
  .save-bar :deep(.el-button) {
    width: 100%;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .section-header {
    grid-template-columns: 42px minmax(0, 1fr);
  }

  .section-header :deep(.el-tag) {
    grid-column: 2;
    justify-self: start;
  }

  .settings-tabs {
    padding-right: 14px;
    padding-left: 14px;
  }
}
</style>
