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
          <p>集中管理系统标识、图片来源、档案浏览与访问水印。</p>
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
          <div class="settings-status">
            <div class="source-status">
              <span class="status-label">当前配置</span>
              <el-tag :type="sourceMeta.type" effect="light" round>
                {{ sourceMeta.label }}
              </el-tag>
            </div>
            <span class="sync-time">最近同步：{{ lastSyncedAt || '尚未同步' }}</span>
          </div>

          <el-form :model="settings" label-position="top" class="compact-form">
            <div class="settings-layout">
              <section class="setting-card archive-card">
                <header class="card-header">
                  <span class="card-icon">
                    <FaIcon name="i-ri:image-2-line" />
                  </span>
                  <div>
                    <h3>影像档案袋</h3>
                    <p>设置图片来源、默认浏览方式、加载数量和选择行为。</p>
                  </div>
                </header>

                <div class="card-body">
                  <div class="settings-block">
                    <div class="block-title">
                      <strong>图片来源</strong>
                      <span>控制档案预览默认读取位置</span>
                    </div>
                    <el-form-item label="默认读取源">
                      <el-segmented
                        v-model="settings.imageSource"
                        :options="[
                          { label: '本地图片', value: 'local' },
                          { label: 'OSS 图片', value: 'oss' },
                        ]"
                        class="full-width"
                      />
                    </el-form-item>
                    <el-alert
                      class="image-source-alert"
                      type="info"
                      :closable="false"
                      show-icon
                      :title="settings.imageSource === 'local'
                        ? '默认从本地图片服务器读取，不会为每张图片生成 OSS 签名地址。'
                        : '优先从 OSS 读取；记录尚未迁移或签名失败时会自动回退本地图片。'"
                    />
                  </div>

                  <div class="settings-block">
                    <div class="block-title">
                      <strong>浏览方式</strong>
                      <span>进入档案袋时自动应用</span>
                    </div>
                    <div class="form-grid">
                      <el-form-item label="影像列表">
                        <el-segmented
                          v-model="settings.archiveDefaultView"
                          :options="[
                            { label: '缩略图', value: 'thumb' },
                            { label: '列表', value: 'list' },
                          ]"
                          class="full-width"
                        />
                      </el-form-item>
                      <el-form-item label="预览模式">
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

                  <div class="settings-block">
                    <div class="block-title">
                      <strong>加载设置</strong>
                      <span>平衡显示密度与首屏速度</span>
                    </div>
                    <div class="form-grid">
                      <el-form-item label="缩略图宽度">
                        <div class="slider-control">
                          <el-slider
                            v-model="settings.archiveThumbnailSize"
                            :min="160"
                            :max="320"
                            :step="20"
                          />
                          <span>{{ settings.archiveThumbnailSize }} px</span>
                        </div>
                      </el-form-item>
                      <el-form-item label="首批渲染">
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

                  <div class="switch-list">
                    <div class="switch-row">
                      <div>
                        <strong>自动适应预览区域</strong>
                        <p>完整显示图片；关闭后按原始尺寸浏览。</p>
                      </div>
                      <el-switch v-model="settings.archiveAutoFit" />
                    </div>
                    <div class="switch-row">
                      <div>
                        <strong>记住选择状态</strong>
                        <p>再次进入同一档案时恢复已选影像页。</p>
                      </div>
                      <el-switch v-model="settings.archiveRememberSelection" />
                    </div>
                  </div>
                </div>
              </section>

              <div class="side-settings">
                <section class="setting-card">
                  <header class="card-header">
                    <span class="card-icon">
                      <FaIcon name="i-ri:window-line" />
                    </span>
                    <div>
                      <h3>系统标识</h3>
                      <p>用于浏览器标题和系统名称。</p>
                    </div>
                  </header>
                  <div class="card-body">
                    <el-form-item label="系统名称">
                      <el-input
                        v-model="settings.systemName"
                        maxlength="40"
                        show-word-limit
                        placeholder="例如 MRR 后台管理中心"
                      />
                    </el-form-item>
                  </div>
                </section>

                <section class="setting-card">
                  <header class="card-header">
                    <span class="card-icon">
                      <FaIcon name="i-ri:shield-user-line" />
                    </span>
                    <div>
                      <h3>访问水印</h3>
                      <p>显示用户 ID 与访问时间。</p>
                    </div>
                  </header>
                  <div class="card-body watermark-body">
                    <div class="switch-row plain-switch-row">
                      <div>
                        <strong>显示访问水印</strong>
                        <p>切换后立即应用到档案页面。</p>
                      </div>
                      <el-switch v-model="settings.archiveWatermarkEnabled" />
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
              </div>
            </div>
          </el-form>

          <footer class="save-bar" :class="{ dirty: isDirty }">
            <div class="save-status">
              <span class="status-dot" />
              <div>
                <strong>{{ isDirty ? `${changedKeys.length} 项修改待保存` : '所有设置已保存' }}</strong>
                <small>{{ isDirty ? '保存后由对应功能立即读取。' : `当前使用${sourceMeta.label}。` }}</small>
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
.tab-label,
.source-status {
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
  margin-bottom: 18px;
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
  gap: 14px;
  min-height: 420px;
}

.settings-status {
  display: flex;
  gap: 20px;
  align-items: center;
  justify-content: space-between;
  min-height: 42px;
  padding: 8px 12px;
  background: var(--el-fill-color-extra-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.status-label,
.sync-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.settings-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(300px, 0.85fr);
  gap: 14px;
  align-items: start;
}

.side-settings {
  display: grid;
  gap: 14px;
}

.setting-card {
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
}

.card-header {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 16px 18px;
  background: var(--el-fill-color-extra-light);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.card-icon {
  display: grid;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  font-size: 17px;
  color: var(--el-color-primary);
  background: color-mix(in srgb, var(--el-color-primary) 10%, var(--el-bg-color));
  border-radius: 10px;
  place-items: center;
}

.card-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--el-text-color-primary);
}

.card-header p {
  margin: 3px 0 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--el-text-color-secondary);
}

.card-body {
  padding: 18px;
}

.settings-block + .settings-block,
.settings-block + .switch-list {
  padding-top: 18px;
  margin-top: 18px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.block-title {
  display: flex;
  gap: 10px;
  align-items: baseline;
  margin-bottom: 14px;
}

.block-title strong {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.block-title span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.image-source-alert {
  margin-top: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
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

.slider-control,
.number-control {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 58px;
  gap: 12px;
  align-items: center;
  width: 100%;
}

.slider-control > span,
.number-control > span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: right;
}

.switch-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.switch-row {
  display: flex;
  gap: 14px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  padding: 12px 14px;
  background: var(--el-fill-color-extra-light);
  border: 1px solid transparent;
  border-radius: 10px;
}

.switch-row:hover {
  border-color: var(--el-border-color-lighter);
}

.switch-row strong {
  display: block;
  margin-bottom: 3px;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.switch-row p {
  margin: 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.plain-switch-row {
  padding: 0 0 16px;
  margin-bottom: 16px;
  background: transparent;
  border-bottom: 1px solid var(--el-border-color-lighter);
  border-radius: 0;
}

.plain-switch-row:hover {
  border-color: var(--el-border-color-lighter);
}

.save-bar {
  display: flex;
  gap: 18px;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: var(--el-fill-color-extra-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.save-bar.dirty {
  background: color-mix(in srgb, var(--el-color-warning) 5%, var(--el-bg-color));
  border-color: color-mix(in srgb, var(--el-color-warning) 35%, var(--el-border-color-lighter));
}

.save-status {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.save-status strong,
.save-status small {
  display: block;
}

.save-status strong {
  margin-bottom: 2px;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.save-status small {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.status-dot {
  flex: 0 0 auto;
  width: 8px;
  height: 8px;
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

@media (max-width: 1100px) {
  .settings-layout {
    grid-template-columns: 1fr;
  }

  .side-settings {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .page-header,
  .settings-status,
  .save-bar,
  .switch-row {
    align-items: stretch;
  }

  .page-header,
  .header-actions,
  .settings-status,
  .save-bar,
  .switch-row {
    flex-direction: column;
  }

  .header-actions :deep(.el-button),
  .save-bar :deep(.el-button) {
    width: 100%;
  }

  .form-grid,
  .switch-list,
  .side-settings {
    grid-template-columns: 1fr;
  }

  .block-title {
    display: grid;
    gap: 3px;
  }

  .settings-tabs {
    padding-right: 14px;
    padding-left: 14px;
  }
}
</style>
