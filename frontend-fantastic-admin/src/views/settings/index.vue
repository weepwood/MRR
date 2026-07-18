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

type SettingsSection = 'general' | 'archive' | 'security' | 'developer' | 'department' | 'appearance'

interface DepartmentThemeSettingsRef {
  saving: boolean
  isDirty: boolean
  saveThemes: () => Promise<void>
}

interface AppConfigPanelRef {
  autoSaveLabel: string
}

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
    key: 'developer',
    title: '开发者模式',
    description: '接口兼容与跨域调试',
    icon: 'i-ri:code-box-line',
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
const departmentThemeRef = ref<DepartmentThemeSettingsRef>()
const appConfigRef = ref<AppConfigPanelRef>()

const activeMeta = computed(() => settingsNavItems.find(item => item.key === activeSection.value)!)
const isServerSettingSection = computed(() => (
  activeSection.value === 'general'
  || activeSection.value === 'archive'
  || activeSection.value === 'security'
  || activeSection.value === 'developer'
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
const developerModeChanged = computed(() => changedKeys.value.includes('developerModeEnabled'))
const savedDeveloperModeEnabled = computed(() => savedSnapshot.value.developerModeEnabled === 'true')

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

async function confirmDeveloperModeEnable() {
  if (!developerModeChanged.value || !settings.developerModeEnabled || savedDeveloperModeEnabled.value) {
    return true
  }

  try {
    await ElMessageBox.confirm(
      '启用后，无有效 JWT 的受保护 API 将以虚拟管理员身份执行，同时允许任意浏览器 Origin 跨域访问。仅限隔离的开发或联调环境使用。',
      '确认启用开发者模式',
      {
        type: 'error',
        confirmButtonText: '确认启用',
        cancelButtonText: '取消',
        distinguishCancelAndClose: true,
      },
    )
    return true
  }
  catch {
    return false
  }
}

async function handleSave() {
  if (!validateSettings() || !await confirmDeveloperModeEnable()) {
    return
  }

  saving.value = true
  const payload = serializeSystemSettings(settings)
  const developerChangeRequested = developerModeChanged.value
  try {
    await saveSystemSettings(payload)
    settingsSource.value = 'server'
    writeLocalSystemSettings(settings)
    markAsSaved()
    updateSyncTime()
    ElMessage.success(
      developerChangeRequested
        ? `开发者模式已${settings.developerModeEnabled ? '启用' : '关闭'}，后端已即时生效`
        : '设置已保存，并已通知对应功能更新',
    )
  }
  catch {
    if (developerChangeRequested) {
      settings.developerModeEnabled = savedDeveloperModeEnabled.value
      ElMessage.error('服务端保存失败，开发者模式未发生变化')
    }
    else {
      settingsSource.value = 'local'
      writeLocalSystemSettings(settings)
      markAsSaved()
      updateSyncTime()
      ElMessage.warning('服务端保存失败，已保存到当前浏览器')
    }
  }
  finally {
    saving.value = false
  }
}

async function handleDepartmentThemeSave() {
  await departmentThemeRef.value?.saveThemes()
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
          <div class="title-line">
            <h2>系统设置</h2>
            <el-tag v-if="settings.developerModeEnabled" type="danger" effect="dark" round>
              开发者模式已启用
            </el-tag>
          </div>
          <p>按功能分类管理系统、档案浏览、接口安全与界面外观。</p>
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
            :class="{ active: activeSection === item.key, danger: item.key === 'developer' && settings.developerModeEnabled }"
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

        <div v-if="isServerSettingSection" class="sidebar-save-card" :class="{ dirty: isDirty }">
          <div class="save-status sidebar-save-status" :class="{ dirty: isDirty }">
            <span class="status-dot" />
            <div>
              <strong>{{ isDirty ? `${changedKeys.length} 项修改待保存` : '所有设置已保存' }}</strong>
              <small>{{ isDirty ? '保存后由后端立即读取。' : `当前使用${sourceMeta.label}。` }}</small>
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

        <div
          v-else-if="activeSection === 'department'"
          class="sidebar-save-card"
          :class="{ dirty: departmentThemeRef?.isDirty }"
        >
          <div class="save-status sidebar-save-status" :class="{ dirty: departmentThemeRef?.isDirty }">
            <span class="status-dot" />
            <div>
              <strong>{{ departmentThemeRef?.isDirty ? '科室配色修改待保存' : '科室配色已保存' }}</strong>
              <small>{{ departmentThemeRef?.isDirty ? '保存后立即应用到档案袋。' : '配色已同步到当前设置。' }}</small>
            </div>
          </div>
          <el-button
            type="primary"
            :loading="departmentThemeRef?.saving"
            :disabled="!departmentThemeRef?.isDirty"
            @click="handleDepartmentThemeSave"
          >
            <FaIcon name="i-ri:save-3-line" />
            保存科室配色
          </el-button>
        </div>

        <div v-else class="sidebar-save-card">
          <div class="save-status sidebar-save-status">
            <span class="status-dot" />
            <div>
              <strong>{{ appConfigRef?.autoSaveLabel ?? '界面外观自动保存' }}</strong>
              <small>界面外观仅保存到当前浏览器。</small>
            </div>
          </div>
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
          <span class="section-icon" :class="{ danger: activeSection === 'developer' && settings.developerModeEnabled }">
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

            <section v-else-if="activeSection === 'security'" class="setting-section">
              <div class="setting-row">
                <div class="setting-copy">
                  <strong>显示访问水印</strong>
                  <p>在档案页面显示用户 ID 与访问时间。</p>
                </div>
                <el-switch v-model="settings.archiveWatermarkEnabled" />
              </div>

              <div class="setting-row">
                <div class="setting-copy">
                  <strong>允许显示完整身份证号</strong>
                  <p>允许在患者管理表中查看完整身份证号；默认关闭。</p>
                </div>
                <el-switch v-model="settings.patientIdCardRevealEnabled" />
              </div>

              <div class="setting-row">
                <div class="setting-copy">
                  <strong>允许复制身份证号</strong>
                  <p>允许点击身份证号后复制完整号码；默认关闭。</p>
                </div>
                <el-switch v-model="settings.patientIdCardCopyEnabled" />
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

            <section v-else class="setting-section developer-section" :class="{ enabled: settings.developerModeEnabled }">
              <div class="developer-hero">
                <div class="developer-copy">
                  <span class="developer-badge">
                    <FaIcon name="i-ri:terminal-box-line" />
                    Runtime compatibility
                  </span>
                  <h4>兼容旧版无登录接口调用</h4>
                  <p>用于隔离开发环境、接口联调和旧客户端过渡。设置保存后后端立即生效，无需重启。</p>
                </div>
                <div class="developer-switch">
                  <span>{{ settings.developerModeEnabled ? '已启用' : '已关闭' }}</span>
                  <el-switch
                    v-model="settings.developerModeEnabled"
                    size="large"
                    inline-prompt
                    active-text="ON"
                    inactive-text="OFF"
                  />
                </div>
              </div>

              <el-alert
                :type="settings.developerModeEnabled ? 'error' : 'warning'"
                :closable="false"
                show-icon
                :title="settings.developerModeEnabled
                  ? '当前受保护 API 可以在没有有效 JWT 的情况下以虚拟管理员身份执行。'
                  : '默认保持关闭。生产环境不应启用开发者模式。'"
              />

              <div class="developer-grid">
                <article class="developer-card">
                  <span class="developer-card-icon"><FaIcon name="i-ri:shield-keyhole-line" /></span>
                  <div>
                    <strong>认证兼容</strong>
                    <p>无 Token、过期 Token 或无效 Token 会注入旧版 <code>dev / ADMIN</code> 虚拟会话。</p>
                  </div>
                </article>
                <article class="developer-card">
                  <span class="developer-card-icon"><FaIcon name="i-ri:global-line" /></span>
                  <div>
                    <strong>跨域调试</strong>
                    <p>API 临时允许任意 Origin，并继续支持凭证、常用方法和自定义请求头。</p>
                  </div>
                </article>
                <article class="developer-card">
                  <span class="developer-card-icon"><FaIcon name="i-ri:file-warning-line" /></span>
                  <div>
                    <strong>可识别调用</strong>
                    <p>兼容请求响应会附带 <code>X-MRR-Developer-Mode: enabled</code>，后端同时记录警告日志。</p>
                  </div>
                </article>
              </div>

              <div class="developer-boundary">
                <FaIcon name="i-ri:information-line" />
                <p>
                  有效 JWT 始终优先使用真实用户。外部影像 Ticket 接口仍执行独立 HMAC、时间戳、nonce 和 IP 白名单校验；开发者模式主要恢复旧客户端直接调用普通业务 API 的能力。
                </p>
              </div>
            </section>
          </el-form>
        </div>

        <DepartmentThemeSettings
          v-else-if="activeSection === 'department'"
          ref="departmentThemeRef"
        />
        <AppConfigPanel v-else ref="appConfigRef" />
      </main>
    </div>
  </div>
</template>

<style scoped>
.settings-page {
  display: grid;
  gap: var(--mrr-space-5);
}

.page-header,
.settings-shell,
.setting-section {
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-card);
}

.page-header {
  display: flex;
  gap: var(--mrr-space-6);
  align-items: center;
  justify-content: space-between;
  padding: var(--mrr-space-5) var(--mrr-space-6);
}

.header-title,
.title-line,
.header-actions,
.section-header,
.save-status,
.status-heading,
.developer-hero,
.developer-switch,
.developer-boundary {
  display: flex;
  align-items: center;
}

.header-title {
  gap: var(--mrr-space-4);
  min-width: 0;
}

.title-line {
  flex-wrap: wrap;
  gap: var(--mrr-space-3);
}

.header-actions {
  gap: var(--mrr-space-2);
}

.header-actions :deep(.el-button) {
  margin-left: 0;
}

.header-icon,
.section-icon,
.nav-icon,
.developer-card-icon {
  display: grid;
  flex: 0 0 auto;
  color: var(--mrr-primary);
  background: color-mix(in srgb, var(--mrr-primary) 10%, var(--mrr-card));
  place-items: center;
}

.header-icon {
  width: 44px;
  height: 44px;
  font-size: 21px;
  border-radius: var(--mrr-radius-lg);
}

.page-header h2,
.section-header h3,
.developer-copy h4 {
  margin: 0;
  color: var(--mrr-foreground);
}

.page-header h2 {
  font-size: 24px;
}

.page-header p,
.section-header p,
.setting-copy p,
.group-heading p,
.sidebar-status p,
.developer-copy p,
.developer-card p,
.developer-boundary p {
  color: var(--mrr-muted-foreground);
}

.page-header p {
  margin: var(--mrr-space-1) 0 0;
  font-size: 13px;
}

.settings-shell {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  min-height: 620px;
  overflow: clip;
}

.settings-sidebar {
  display: flex;
  flex-direction: column;
  gap: var(--mrr-space-5);
  min-height: 0;
  padding: var(--mrr-space-4);
  background: var(--mrr-muted);
  border-right: 1px solid var(--mrr-border);
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
  color: var(--mrr-muted-foreground);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--mrr-radius-md);
  transition: background-color 0.16s ease, border-color 0.16s ease, color 0.16s ease;
}

.settings-nav-item:hover,
.settings-nav-item.active {
  color: var(--mrr-foreground);
  background: var(--mrr-card);
  border-color: var(--mrr-border);
}

.settings-nav-item.active {
  color: var(--mrr-primary);
}

.settings-nav-item.danger {
  color: var(--mrr-destructive);
  border-color: color-mix(in srgb, var(--mrr-destructive) 28%, var(--mrr-border));
}

.nav-icon {
  width: 34px;
  height: 34px;
  font-size: 17px;
  border-radius: var(--mrr-radius-md);
}

.nav-copy,
.nav-copy strong,
.nav-copy small {
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
  color: var(--mrr-muted-foreground);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-arrow {
  color: var(--mrr-muted-foreground);
}

.sidebar-save-card,
.sidebar-status {
  padding: var(--mrr-space-3);
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
}

.sidebar-save-card {
  margin-top: auto;
}

.sidebar-save-card.dirty {
  border-color: color-mix(in srgb, var(--color-warning) 36%, var(--mrr-border));
}

.sidebar-save-card :deep(.el-button) {
  width: 100%;
  margin-top: var(--mrr-space-3);
}

.save-status {
  gap: 10px;
  min-width: 0;
}

.save-status strong,
.save-status small {
  display: block;
}

.save-status strong {
  margin-bottom: 2px;
  font-size: 13px;
  color: var(--mrr-foreground);
}

.save-status small,
.sidebar-status p {
  font-size: 11px;
}

.status-dot {
  flex: 0 0 auto;
  width: 8px;
  height: 8px;
  background: var(--color-success);
  border-radius: 50%;
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--color-success) 13%, transparent);
}

.save-status.dirty .status-dot {
  background: var(--color-warning);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--color-warning) 15%, transparent);
}

.status-heading {
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
}

.sidebar-status p {
  margin: var(--mrr-space-2) 0 0;
}

.settings-content {
  min-width: 0;
  padding: var(--mrr-space-6);
}

.section-header {
  gap: var(--mrr-space-3);
  padding-bottom: var(--mrr-space-5);
  margin-bottom: var(--mrr-space-5);
  border-bottom: 1px solid var(--mrr-border);
}

.section-icon {
  width: 38px;
  height: 38px;
  font-size: 18px;
  border-radius: var(--mrr-radius-md);
}

.section-icon.danger {
  color: var(--mrr-destructive);
  background: var(--mrr-destructive-muted);
}

.section-header h3 {
  font-size: 18px;
}

.section-header p {
  margin: var(--mrr-space-1) 0 0;
  font-size: 12px;
}

.system-panel {
  min-height: 420px;
}

.setting-section {
  overflow: hidden;
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
  border-top: 1px solid var(--mrr-border);
}

.setting-row--stack {
  align-items: flex-start;
}

.setting-copy {
  min-width: 180px;
  max-width: 440px;
}

.setting-copy strong,
.group-heading strong,
.switch-row strong,
.developer-card strong {
  color: var(--mrr-foreground);
}

.setting-copy strong,
.group-heading strong {
  font-size: 14px;
}

.setting-copy p,
.group-heading p {
  margin: var(--mrr-space-1) 0 0;
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
  margin-top: var(--mrr-space-3);
}

.setting-group {
  padding: 18px 20px;
}

.group-heading {
  margin-bottom: var(--mrr-space-4);
}

.control-grid,
.switch-list,
.developer-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--mrr-space-5);
}

:deep(.el-form-item) {
  margin-bottom: 0;
}

:deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--mrr-foreground);
}

.full-width,
.number-control :deep(.el-input-number) {
  width: 100%;
}

.slider-control,
.number-control {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 58px;
  gap: var(--mrr-space-3);
  align-items: center;
  width: 100%;
}

.compact-number-control {
  max-width: 300px;
}

.slider-control > span,
.number-control > span {
  font-size: 12px;
  color: var(--mrr-muted-foreground);
  text-align: right;
}

.switch-list {
  gap: var(--mrr-space-3);
  padding: 18px 20px;
}

.switch-row {
  display: flex;
  gap: var(--mrr-space-4);
  align-items: center;
  justify-content: space-between;
  padding: 13px 14px;
  background: var(--mrr-muted);
  border: 1px solid transparent;
  border-radius: var(--mrr-radius-md);
}

.switch-row:hover {
  border-color: var(--mrr-border);
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
  color: var(--mrr-muted-foreground);
}

.developer-section {
  display: grid;
  gap: var(--mrr-space-5);
  padding: var(--mrr-space-6);
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.developer-section.enabled {
  background: color-mix(in srgb, var(--mrr-destructive) 3%, var(--mrr-card));
  border-color: color-mix(in srgb, var(--mrr-destructive) 36%, var(--mrr-border));
}

.developer-hero {
  gap: var(--mrr-space-6);
  justify-content: space-between;
}

.developer-copy {
  max-width: 680px;
}

.developer-badge {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 5px 9px;
  margin-bottom: var(--mrr-space-3);
  font-size: 11px;
  font-weight: 700;
  color: var(--mrr-primary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  background: color-mix(in srgb, var(--mrr-primary) 9%, var(--mrr-card));
  border-radius: var(--mrr-radius-pill);
}

.developer-copy h4 {
  font-size: 20px;
}

.developer-copy p {
  margin: var(--mrr-space-2) 0 0;
  font-size: 13px;
  line-height: 1.65;
}

.developer-switch {
  flex: 0 0 auto;
  gap: var(--mrr-space-3);
  padding: var(--mrr-space-3) var(--mrr-space-4);
  color: var(--mrr-foreground);
  background: var(--mrr-muted);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
}

.developer-switch > span {
  font-size: 12px;
  font-weight: 700;
}

.developer-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--mrr-space-3);
}

.developer-card {
  display: flex;
  gap: var(--mrr-space-3);
  align-items: flex-start;
  padding: var(--mrr-space-4);
  background: var(--mrr-muted);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
}

.developer-card-icon {
  width: 34px;
  height: 34px;
  border-radius: var(--mrr-radius-md);
}

.developer-card strong {
  font-size: 13px;
}

.developer-card p {
  margin: 5px 0 0;
  font-size: 11px;
  line-height: 1.55;
}

.developer-card code,
.developer-boundary code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.developer-boundary {
  gap: var(--mrr-space-3);
  align-items: flex-start;
  padding: var(--mrr-space-4);
  color: var(--color-info);
  background: color-mix(in srgb, var(--color-info) 7%, var(--mrr-card));
  border: 1px solid color-mix(in srgb, var(--color-info) 22%, var(--mrr-border));
  border-radius: var(--mrr-radius-md);
}

.developer-boundary p {
  margin: 0;
  font-size: 12px;
  line-height: 1.65;
}

@media (max-width: 1080px) {
  .developer-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 980px) {
  .settings-shell {
    grid-template-columns: 1fr;
  }

  .settings-sidebar {
    border-right: 0;
    border-bottom: 1px solid var(--mrr-border);
  }

  .settings-nav {
    display: flex;
    gap: var(--mrr-space-2);
    overflow-x: auto;
  }

  .settings-nav-item {
    flex: 0 0 176px;
  }

  .nav-arrow,
  .sidebar-status {
    display: none;
  }
}

@media (max-width: 700px) {
  .page-header,
  .setting-row,
  .developer-hero {
    align-items: stretch;
    flex-direction: column;
  }

  .header-actions,
  .header-actions :deep(.el-button) {
    width: 100%;
  }

  .settings-content,
  .developer-section {
    padding: var(--mrr-space-4);
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

  .developer-switch {
    justify-content: space-between;
  }
}
</style>
