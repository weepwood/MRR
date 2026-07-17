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

type SettingsSection = 'general' | 'archive' | 'security' | 'department' | 'appearance'

interface SettingsNavItem {
  key: SettingsSection
  title: string
  description: string
  icon: string
}

const settingsNavItems: SettingsNavItem[] = [
  {
    key: 'general',
    title: '基础设置',
    description: '系统名称与图片来源',
    icon: 'i-ri:settings-3-line',
  },
  {
    key: 'archive',
    title: '档案浏览',
    description: '浏览方式与加载策略',
    icon: 'i-ri:image-2-line',
  },
  {
    key: 'security',
    title: '访问安全',
    description: '水印与 IP 切换限制',
    icon: 'i-ri:shield-check-line',
  },
  {
    key: 'department',
    title: '科室配色',
    description: '档案袋颜色规则',
    icon: 'i-ri:palette-line',
  },
  {
    key: 'appearance',
    title: '界面外观',
    description: '主题、导航与页面样式',
    icon: 'i-ri:layout-4-line',
  },
]

const activeSection = ref<SettingsSection>('general')
const loading = ref(false)
const saving = ref(false)
const settingsSource = ref<SettingsSource>('default')
const lastSyncedAt = ref('')
const savedSnapshot = ref<Record<string, string>>({})
const settings = reactive<EffectiveSystemSettings>(createDefaultSystemSettings())

const activeMeta = computed(() => settingsNavItems.find(item => item.key === activeSection.value)!)
const isServerSettingSection = computed(() => (
  activeSection.value === 'general'
  || activeSection.value === 'archive'
  || activeSection.value === 'security'
))

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
  if (!Number.isInteger(settings.archiveIpMaxChanges)
    || settings.archiveIpMaxChanges < 0
    || settings.archiveIpMaxChanges > 20) {
    ElMessage.warning('每日 IP 切换次数必须是 0 到 20 之间的整数')
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
          <p>按功能分类管理系统、档案浏览与界面外观。</p>
        </div>
      </div>
      <div v-if="isServerSettingSection" class="header-actions">
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

    <div class="settings-shell">
      <aside class="settings-sidebar">
        <nav class="settings-nav" aria-label="设置分类">
          <button
            v-for="item in settingsNavItems"
            :key="item.key"
            type="button"
            class="settings-nav-item"
            :class="{ active: activeSection === item.key }"
            @click="activeSection = item.key"
          >
            <span class="nav-icon">
              <FaIcon :name="item.icon" />
            </span>
            <span class="nav-copy">
              <strong>{{ item.title }}</strong>
              <small>{{ item.description }}</small>
            </span>
            <FaIcon name="i-ri:arrow-right-s-line" class="nav-arrow" />
          </button>
        </nav>

        <div class="sidebar-save-card" :class="{ dirty: isDirty }">
          <div class="save-status sidebar-save-status" :class="{ dirty: isDirty }">
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
        </div>

        <div class="sidebar-status">
          <div class="status-heading">
            <span>配置状态</span>
            <el-tag :type="sourceMeta.type" effect="light" round size="small">
              {{ sourceMeta.label }}
            </el-tag>
          </div>
          <p>最近同步：{{ lastSyncedAt || '尚未同步' }}</p>
        </div>
      </aside>

      <main class="settings-content">
        <header class="section-header">
          <span class="section-icon">
            <FaIcon :name="activeMeta.icon" />
          </span>
          <div>
            <h3>{{ activeMeta.title }}</h3>
            <p>{{ activeMeta.description }}</p>
          </div>
        </header>

        <div v-if="isServerSettingSection" v-loading="loading" class="system-panel">
          <el-form :model="settings" label-position="top" class="settings-form">
            <section v-if="activeSection === 'general'" class="setting-section">
              <div class="setting-row">
                <div class="setting-copy">
                  <strong>系统名称</strong>
                  <p>用于浏览器标题和系统名称显示。</p>
                </div>
                <div class="setting-control">
                  <el-input
                    v-model="settings.systemName"
                    maxlength="40"
                    show-word-limit
                    placeholder="例如 MRR 后台管理中心"
                  />
                </div>
              </div>

              <div class="setting-row setting-row--stack">
                <div class="setting-copy">
                  <strong>默认图片来源</strong>
                  <p>设置影像档案袋优先从本地图片服务器或 OSS 读取。</p>
                </div>
                <div class="setting-control setting-control--wide">
                  <el-segmented
                    v-model="settings.imageSource"
                    :options="[
                      { label: '本地图片', value: 'local' },
                      { label: 'OSS 图片', value: 'oss' },
                    ]"
                    class="full-width"
                  />
                  <el-alert
                    class="setting-alert"
                    type="info"
                    :closable="false"
                    show-icon
                    :title="settings.imageSource === 'local'
                      ? '默认从本地图片服务器读取，不会为每张图片生成 OSS 签名地址。'
                      : '优先从 OSS 读取；记录尚未迁移或签名失败时会自动回退本地图片。'"
                  />
                </div>
              </div>
            </section>

            <section v-else-if="activeSection === 'archive'" class="setting-section">
              <div class="setting-group">
                <div class="group-heading">
                  <strong>默认浏览方式</strong>
                  <p>进入影像档案袋时自动应用。</p>
                </div>
                <div class="control-grid">
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

              <div class="setting-group">
                <div class="group-heading">
                  <strong>加载与显示</strong>
                  <p>在显示密度与首屏速度之间取得平衡。</p>
                </div>
                <div class="control-grid">
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
            </section>

            <section v-else class="setting-section">
              <div class="setting-row">
                <div class="setting-copy">
                  <strong>显示访问水印</strong>
                  <p>在档案页面显示用户 ID 与访问时间。</p>
                </div>
                <el-switch v-model="settings.archiveWatermarkEnabled" />
              </div>

              <div class="setting-row">
                <div class="setting-copy">
                  <strong>水印透明度</strong>
                  <p>透明度越低，对影像内容遮挡越少。</p>
                </div>
                <div class="setting-control">
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
                </div>
              </div>

              <div class="setting-row setting-row--stack">
                <div class="setting-copy">
                  <strong>每日允许 IP 切换次数</strong>
                  <p>限制同一 userid 每天可更换的内网 IP 数量。</p>
                </div>
                <div class="setting-control setting-control--wide">
                  <div class="number-control compact-number-control">
                    <el-input-number
                      v-model="settings.archiveIpMaxChanges"
                      :min="0"
                      :max="20"
                      :step="1"
                      :precision="0"
                      controls-position="right"
                    />
                    <span>次/日</span>
                  </div>
                  <el-alert
                    class="setting-alert"
                    type="warning"
                    :closable="false"
                    show-icon
                    title="首次访问只绑定 IP，不计入切换次数；达到上限后更换 IP 将被拒绝。"
                  />
                </div>
              </div>
            </section>
          </el-form>

        </div>

        <DepartmentThemeSettings v-else-if="activeSection === 'department'" />
        <AppConfigPanel v-else />
      </main>
    </div>
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
  padding: 20px 22px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
}

.header-title {
  display: flex;
  gap: 14px;
  align-items: center;
  min-width: 0;
}

.header-icon,
.section-icon,
.nav-icon {
  display: grid;
  flex: 0 0 auto;
  color: var(--el-color-primary);
  background: color-mix(in srgb, var(--el-color-primary) 10%, var(--el-bg-color));
  place-items: center;
}

.header-icon {
  width: 44px;
  height: 44px;
  font-size: 21px;
  border-radius: 12px;
}

.page-header h2,
.section-header h3 {
  margin: 0;
  color: var(--el-text-color-primary);
}

.page-header h2 {
  font-size: 24px;
}

.page-header p,
.section-header p,
.setting-copy p,
.group-heading p,
.sidebar-status p {
  color: var(--el-text-color-secondary);
}

.page-header p {
  margin: 5px 0 0;
  font-size: 13px;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.header-actions :deep(.el-button) {
  margin-left: 0;
}

.settings-shell {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  min-height: 620px;
  overflow: clip;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
}

.settings-sidebar {
  align-self: start;
  position: sticky;
  top: calc(var(--g-header-actual-height) + var(--g-tabbar-actual-height) + 16px);
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 16px;
  background: var(--el-fill-color-extra-light);
  border-right: 1px solid var(--el-border-color-lighter);
}

.settings-nav {
  display: grid;
  gap: 6px;
}

.settings-nav-item {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  width: 100%;
  padding: 10px;
  font: inherit;
  color: var(--el-text-color-regular);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 10px;
  transition: background-color 0.16s ease, border-color 0.16s ease, color 0.16s ease;
}

.settings-nav-item:hover {
  background: var(--el-bg-color);
  border-color: var(--el-border-color-lighter);
}

.settings-nav-item.active {
  color: var(--el-color-primary);
  background: var(--el-bg-color);
  border-color: color-mix(in srgb, var(--el-color-primary) 24%, var(--el-border-color-lighter));
  box-shadow: 0 1px 2px rgb(0 0 0 / 3%);
}

.nav-icon {
  width: 34px;
  height: 34px;
  font-size: 17px;
  border-radius: 9px;
}

.nav-copy {
  min-width: 0;
}

.nav-copy strong,
.nav-copy small {
  display: block;
}

.nav-copy strong {
  margin-bottom: 2px;
  font-size: 13px;
  font-weight: 650;
}

.nav-copy small {
  overflow: hidden;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-arrow {
  font-size: 16px;
  color: var(--el-text-color-placeholder);
}

.settings-nav-item.active .nav-arrow {
  color: var(--el-color-primary);
}

.sidebar-status {
  padding: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.sidebar-save-card {
  padding: 12px;
  margin-top: auto;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.sidebar-save-status {
  margin-bottom: 12px;
}

.sidebar-save-status.dirty {
  background: color-mix(in srgb, var(--el-color-warning) 5%, var(--el-bg-color));
}

.sidebar-save-card.dirty {
  border-color: color-mix(in srgb, var(--el-color-warning) 35%, var(--el-border-color-lighter));
}

.sidebar-save-card :deep(.el-button) {
  width: 100%;
  gap: 6px;
}

.status-heading {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.sidebar-status p {
  margin: 8px 0 0;
  font-size: 11px;
  line-height: 1.5;
}

.settings-content {
  min-width: 0;
  padding: 22px;
}

.section-header {
  display: flex;
  gap: 12px;
  align-items: center;
  padding-bottom: 18px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.section-icon {
  width: 38px;
  height: 38px;
  font-size: 18px;
  border-radius: 10px;
}

.section-header h3 {
  font-size: 18px;
}

.section-header p {
  margin: 4px 0 0;
  font-size: 12px;
}

.system-panel {
  display: grid;
  gap: 16px;
  min-height: 420px;
}

.setting-section {
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.setting-row {
  display: flex;
  gap: 28px;
  align-items: center;
  justify-content: space-between;
  min-height: 82px;
  padding: 18px 20px;
}

.setting-row + .setting-row,
.setting-group + .setting-group,
.setting-group + .switch-list {
  border-top: 1px solid var(--el-border-color-lighter);
}

.setting-row--stack {
  align-items: flex-start;
}

.setting-copy {
  min-width: 180px;
  max-width: 420px;
}

.setting-copy strong,
.group-heading strong,
.switch-row strong {
  color: var(--el-text-color-primary);
}

.setting-copy strong,
.group-heading strong {
  font-size: 14px;
}

.setting-copy p,
.group-heading p {
  margin: 5px 0 0;
  font-size: 12px;
  line-height: 1.55;
}

.setting-control {
  width: min(420px, 52%);
}

.setting-control--wide {
  width: min(560px, 58%);
}

.setting-alert {
  margin-top: 12px;
}

.setting-group {
  padding: 18px 20px;
}

.group-heading {
  margin-bottom: 16px;
}

.control-grid {
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

.slider-control,
.number-control {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 58px;
  gap: 12px;
  align-items: center;
  width: 100%;
}

.compact-number-control {
  max-width: 300px;
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
  gap: 12px;
  padding: 18px 20px;
}

.switch-row {
  display: flex;
  gap: 14px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  padding: 13px 14px;
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
}

.switch-row p {
  margin: 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
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

.save-status.dirty .status-dot {
  background: var(--el-color-warning);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--el-color-warning) 15%, transparent);
}

@media (max-width: 980px) {
  .settings-shell {
    grid-template-columns: 1fr;
  }

  .settings-sidebar {
    position: static;
    padding: 12px;
    border-right: 0;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .settings-nav {
    display: flex;
    gap: 8px;
    overflow-x: auto;
    scrollbar-width: thin;
  }

  .settings-nav-item {
    flex: 0 0 168px;
  }

  .nav-arrow,
  .sidebar-status {
    display: none;
  }
}

@media (max-width: 700px) {
  .page-header,
  .setting-row {
    align-items: stretch;
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions :deep(.el-button) {
    width: 100%;
  }

  .settings-content {
    padding: 16px;
  }

  .setting-control,
  .setting-control--wide {
    width: 100%;
  }

  .control-grid,
  .switch-list {
    grid-template-columns: 1fr;
  }

  .setting-copy {
    max-width: none;
  }

}
</style>
