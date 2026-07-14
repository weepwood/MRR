<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { getSystemSettings, saveSystemSettings } from '@/api/modules/settings'
import AppConfigPanel from './components/AppConfigPanel.vue'
import DepartmentThemeSettings from './components/DepartmentThemeSettings.vue'

defineOptions({ name: 'SettingsPage' })

type SettingsSource = 'server' | 'local' | 'default'
type SettingsSection = 'general' | 'archive' | 'storage' | 'security' | 'notification' | 'operation'

function createDefaultSettings() {
  return {
    systemName: 'MRR 后台管理中心',
    systemShortName: 'MRR',
    swaggerUrl: '/swagger-ui/index.html',
    defaultPageSize: 20,
    dateFormat: 'YYYY-MM-DD',
    timeZone: 'Asia/Shanghai',

    archiveDefaultView: 'thumbnail',
    archiveThumbnailSize: 200,
    archivePreloadCount: 4,
    archiveAutoFit: true,
    archiveRememberSelection: true,
    archiveWatermarkEnabled: true,
    archiveWatermarkOpacity: 14,

    maxFileSize: 20,
    allowedImageTypes: 'jpg,jpeg,png,tif,tiff,pdf',
    concurrentUploads: 3,
    imageLoadRetryCount: 2,
    duplicateCheckEnabled: true,
    autoBackup: true,
    backupInterval: 6,
    backupRetentionDays: 30,

    sessionTimeout: 120,
    passwordMinLength: 8,
    loginFailureLimit: 5,
    loginCaptchaEnabled: false,
    operationAuditEnabled: true,
    sensitiveDataMaskEnabled: true,

    emailNotification: false,
    smtpServer: '',
    smtpPort: 25,
    senderEmail: '',
    backupFailureNotification: true,
    storageWarningNotification: true,
    storageWarningThreshold: 85,

    maintenanceMode: false,
    maintenanceMessage: '系统维护中，请稍后再试',
    logLevel: 'info',
    performanceMonitoring: true,
    errorReporting: true,
    slowRequestThreshold: 2000,
    healthCheckInterval: 60,
  }
}

type SystemSettings = ReturnType<typeof createDefaultSettings>

const activeTab = ref<'system' | 'department' | 'app'>('system')
const activeSection = ref<SettingsSection>('general')
const loading = ref(false)
const saving = ref(false)
const settingsSource = ref<SettingsSource>('default')
const lastSyncedAt = ref('')
const savedSettings = ref<Record<string, string>>({})
const settings = reactive<SystemSettings>(createDefaultSettings())

const LOCAL_KEY = 'MRR-ADMIN:system-settings'
const settingKeys = Object.keys(createDefaultSettings())

const sectionList: Array<{
  name: SettingsSection
  title: string
  description: string
  icon: string
}> = [
  { name: 'general', title: '基础信息', description: '名称、时区与默认显示', icon: 'i-ri:settings-3-line' },
  { name: 'archive', title: '档案浏览', description: '缩略图、水印与浏览行为', icon: 'i-ri:image-2-line' },
  { name: 'storage', title: '文件与备份', description: '上传限制、校验与备份策略', icon: 'i-ri:database-2-line' },
  { name: 'security', title: '安全策略', description: '会话、登录与审计配置', icon: 'i-ri:shield-keyhole-line' },
  { name: 'notification', title: '通知告警', description: '邮件服务与异常告警', icon: 'i-ri:notification-3-line' },
  { name: 'operation', title: '运维监控', description: '维护模式、日志与健康检查', icon: 'i-ri:pulse-line' },
]

function serializeSettings() {
  const data: Record<string, string> = {}
  for (const [key, value] of Object.entries(settings)) {
    data[key] = String(value ?? '')
  }
  return data
}

function markAsSaved() {
  savedSettings.value = serializeSettings()
}

const changedCount = computed(() => {
  const current = serializeSettings()
  return settingKeys.filter(key => current[key] !== savedSettings.value[key]).length
})

const isDirty = computed(() => changedCount.value > 0)

const enabledFeatureCount = computed(() => {
  return Object.values(settings).filter(value => typeof value === 'boolean' && value).length
})

const sourceMeta = computed(() => {
  const map: Record<SettingsSource, { label: string, type: 'success' | 'warning' | 'info' }> = {
    server: { label: '服务器配置', type: 'success' },
    local: { label: '本地草稿', type: 'warning' },
    default: { label: '默认配置', type: 'info' },
  }
  return map[settingsSource.value]
})

function applySettings(values: Record<string, unknown>) {
  const defaults = createDefaultSettings()
  const target = settings as unknown as Record<string, string | number | boolean>

  Object.assign(settings, defaults)

  for (const key of settingKeys) {
    const incoming = values[key]
    const defaultValue = (defaults as unknown as Record<string, string | number | boolean>)[key]
    if (incoming === undefined || incoming === null) {
      continue
    }

    if (typeof defaultValue === 'number') {
      const parsed = Number(incoming)
      if (!Number.isNaN(parsed)) {
        target[key] = parsed
      }
    }
    else if (typeof defaultValue === 'boolean') {
      target[key] = incoming === true || incoming === 'true'
    }
    else {
      target[key] = String(incoming)
    }
  }
}

function readLocalSettings() {
  try {
    const raw = localStorage.getItem(LOCAL_KEY)
    if (!raw) {
      return null
    }
    return JSON.parse(raw) as Record<string, unknown>
  }
  catch {
    return null
  }
}

function syncToLocal() {
  try {
    localStorage.setItem(LOCAL_KEY, JSON.stringify(settings))
  }
  catch {
    // 浏览器禁用存储时不阻断服务端保存
  }
}

function updateSyncTime() {
  lastSyncedAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

/** 优先加载服务端配置；仅在服务端不可用或无数据时读取本地草稿。 */
async function loadSettings() {
  loading.value = true
  let loaded = false

  try {
    const res = await getSystemSettings()
    const serverSettings = res.data
    if (serverSettings && Object.keys(serverSettings).length > 0) {
      applySettings(serverSettings)
      settingsSource.value = 'server'
      syncToLocal()
      loaded = true
    }
  }
  catch {
    // 服务端不可用时继续尝试本地草稿
  }

  if (!loaded) {
    const localSettings = readLocalSettings()
    if (localSettings) {
      applySettings(localSettings)
      settingsSource.value = 'local'
    }
    else {
      applySettings(createDefaultSettings())
      settingsSource.value = 'default'
    }
  }

  markAsSaved()
  updateSyncTime()
  loading.value = false
}

function validateSettings() {
  if (settings.emailNotification && (!settings.smtpServer.trim() || !settings.senderEmail.trim())) {
    activeSection.value = 'notification'
    ElMessage.warning('启用邮件通知后，请填写 SMTP 地址和发件邮箱')
    return false
  }

  if (settings.maintenanceMode && !settings.maintenanceMessage.trim()) {
    activeSection.value = 'operation'
    ElMessage.warning('启用维护模式后，请填写维护提示文案')
    return false
  }

  return true
}

async function handleSave() {
  if (!validateSettings()) {
    return
  }

  saving.value = true
  try {
    await saveSystemSettings(serializeSettings())
    syncToLocal()
    settingsSource.value = 'server'
    markAsSaved()
    updateSyncTime()
    ElMessage.success('系统设置已保存到服务器')
  }
  catch {
    syncToLocal()
    settingsSource.value = 'local'
    markAsSaved()
    updateSyncTime()
    ElMessage.warning('服务端保存失败，当前配置已保存为本地草稿')
  }
  finally {
    saving.value = false
  }
}

function handleReset() {
  applySettings(createDefaultSettings())
  ElMessage.info('已恢复默认值，保存后生效')
}

async function handleReload() {
  if (isDirty.value) {
    try {
      await ElMessageBox.confirm('重新加载会丢弃当前未保存修改，是否继续？', '重新加载设置', {
        type: 'warning',
        confirmButtonText: '继续加载',
        cancelButtonText: '取消',
      })
    }
    catch {
      return
    }
  }
  await loadSettings()
  ElMessage.success('设置已重新加载')
}

onMounted(async () => {
  await nextTick()
  await loadSettings()
})
</script>

<template>
  <div class="page-shell">
    <section class="settings-hero">
      <div class="hero-copy">
        <div class="hero-icon">
          <FaIcon name="i-ri:settings-4-line" />
        </div>
        <div>
          <p class="eyebrow">
            System Settings
          </p>
          <h2>系统设置中心</h2>
          <p class="subtitle">
            集中管理系统参数、档案浏览规则、科室配色和当前浏览器的应用偏好。
          </p>
        </div>
      </div>
      <div v-if="activeTab === 'system'" class="hero-actions">
        <el-button :disabled="loading" @click="handleReload">
          <FaIcon name="i-ri:refresh-line" />
          重新加载
        </el-button>
        <el-button :disabled="loading" @click="handleReset">
          <FaIcon name="i-ri:restart-line" />
          恢复默认
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="loading || (!isDirty && settingsSource === 'server')"
          @click="handleSave"
        >
          <FaIcon name="i-ri:save-3-line" />
          保存设置
        </el-button>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="settings-tabs">
      <el-tab-pane name="system">
        <template #label>
          <span class="tab-label">
            <FaIcon name="i-ri:equalizer-2-line" />
            系统参数
          </span>
        </template>

        <div v-loading="loading" class="system-settings">
          <div class="status-grid">
            <div class="status-card">
              <span class="status-icon status-icon--source">
                <FaIcon name="i-ri:cloud-line" />
              </span>
              <div>
                <span class="status-label">配置来源</span>
                <div class="status-value">
                  <el-tag :type="sourceMeta.type" effect="light" round>
                    {{ sourceMeta.label }}
                  </el-tag>
                </div>
              </div>
            </div>
            <div class="status-card">
              <span class="status-icon status-icon--change">
                <FaIcon name="i-ri:edit-box-line" />
              </span>
              <div>
                <span class="status-label">修改状态</span>
                <strong class="status-value">
                  {{ isDirty ? `${changedCount} 项待保存` : '配置已同步' }}
                </strong>
              </div>
            </div>
            <div class="status-card">
              <span class="status-icon status-icon--feature">
                <FaIcon name="i-ri:toggle-line" />
              </span>
              <div>
                <span class="status-label">已启用功能</span>
                <strong class="status-value">{{ enabledFeatureCount }} 项</strong>
              </div>
            </div>
            <div class="status-card">
              <span class="status-icon status-icon--time">
                <FaIcon name="i-ri:time-line" />
              </span>
              <div>
                <span class="status-label">最近同步</span>
                <strong class="status-value status-value--small">{{ lastSyncedAt || '尚未同步' }}</strong>
              </div>
            </div>
          </div>

          <div class="settings-workspace">
            <aside class="settings-nav">
              <div class="nav-heading">
                <span>配置分类</span>
                <el-tag size="small" effect="plain" round>
                  {{ settingKeys.length }} 项
                </el-tag>
              </div>
              <button
                v-for="section in sectionList"
                :key="section.name"
                type="button"
                class="nav-item"
                :class="{ active: activeSection === section.name }"
                @click="activeSection = section.name"
              >
                <span class="nav-item-icon">
                  <FaIcon :name="section.icon" />
                </span>
                <span class="nav-item-copy">
                  <strong>{{ section.title }}</strong>
                  <small>{{ section.description }}</small>
                </span>
                <FaIcon name="i-ri:arrow-right-s-line" class="nav-arrow" />
              </button>
            </aside>

            <main class="settings-content">
              <section v-if="activeSection === 'general'" class="setting-panel">
                <header class="panel-header">
                  <div class="panel-heading-icon">
                    <FaIcon name="i-ri:settings-3-line" />
                  </div>
                  <div>
                    <h3>基础信息</h3>
                    <p>设置系统标识、接口文档地址以及全局默认显示格式。</p>
                  </div>
                </header>
                <el-form :model="settings" label-position="top" class="form-grid">
                  <el-form-item label="系统名称">
                    <el-input v-model="settings.systemName" maxlength="40" show-word-limit placeholder="用于页面标题和系统标识" />
                  </el-form-item>
                  <el-form-item label="系统简称">
                    <el-input v-model="settings.systemShortName" maxlength="12" show-word-limit placeholder="用于窄屏和紧凑区域" />
                  </el-form-item>
                  <el-form-item label="Swagger 地址" class="form-item-wide">
                    <el-input v-model="settings.swaggerUrl" placeholder="例如 /swagger-ui/index.html">
                      <template #prefix>
                        <FaIcon name="i-ri:link" />
                      </template>
                    </el-input>
                  </el-form-item>
                  <el-form-item label="默认分页数量">
                    <div class="control-with-unit">
                      <el-input-number v-model="settings.defaultPageSize" :min="10" :max="200" :step="10" controls-position="right" />
                      <span>条 / 页</span>
                    </div>
                  </el-form-item>
                  <el-form-item label="日期格式">
                    <el-select v-model="settings.dateFormat" class="full-width">
                      <el-option label="2026-07-14" value="YYYY-MM-DD" />
                      <el-option label="2026/07/14" value="YYYY/MM/DD" />
                      <el-option label="2026年07月14日" value="YYYY年MM月DD日" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="系统时区" class="form-item-wide">
                    <el-select v-model="settings.timeZone" class="full-width" filterable>
                      <el-option label="中国标准时间（Asia/Shanghai）" value="Asia/Shanghai" />
                      <el-option label="日本标准时间（Asia/Tokyo）" value="Asia/Tokyo" />
                      <el-option label="协调世界时（UTC）" value="UTC" />
                    </el-select>
                  </el-form-item>
                </el-form>
              </section>

              <section v-else-if="activeSection === 'archive'" class="setting-panel">
                <header class="panel-header">
                  <div class="panel-heading-icon">
                    <FaIcon name="i-ri:image-2-line" />
                  </div>
                  <div>
                    <h3>档案浏览</h3>
                    <p>控制影像档案袋的默认视图、图片加载方式和水印表现。</p>
                  </div>
                </header>
                <el-form :model="settings" label-position="top" class="form-grid">
                  <el-form-item label="默认浏览模式">
                    <el-segmented
                      v-model="settings.archiveDefaultView"
                      :options="[
                        { label: '缩略图', value: 'thumbnail' },
                        { label: '列表', value: 'list' },
                        { label: '单页', value: 'single' },
                      ]"
                      class="full-width"
                    />
                  </el-form-item>
                  <el-form-item label="缩略图宽度">
                    <div class="control-with-unit">
                      <el-slider v-model="settings.archiveThumbnailSize" :min="160" :max="320" :step="20" show-stops />
                      <span>{{ settings.archiveThumbnailSize }} px</span>
                    </div>
                  </el-form-item>
                  <el-form-item label="预加载图片数量">
                    <div class="control-with-unit">
                      <el-input-number v-model="settings.archivePreloadCount" :min="0" :max="12" controls-position="right" />
                      <span>张</span>
                    </div>
                  </el-form-item>
                  <el-form-item label="水印透明度">
                    <div class="control-with-unit">
                      <el-slider v-model="settings.archiveWatermarkOpacity" :min="5" :max="40" :step="1" />
                      <span>{{ settings.archiveWatermarkOpacity }}%</span>
                    </div>
                  </el-form-item>
                </el-form>
                <div class="switch-list">
                  <div class="switch-item">
                    <div>
                      <strong>影像档案袋水印</strong>
                      <p>在档案浏览页面叠加当前用户和访问时间。</p>
                    </div>
                    <el-switch v-model="settings.archiveWatermarkEnabled" inline-prompt active-text="开" inactive-text="关" />
                  </div>
                  <div class="switch-item">
                    <div>
                      <strong>图片自动适应容器</strong>
                      <p>根据显示区域自动缩放图片，并保持原始比例。</p>
                    </div>
                    <el-switch v-model="settings.archiveAutoFit" inline-prompt active-text="开" inactive-text="关" />
                  </div>
                  <div class="switch-item">
                    <div>
                      <strong>记住档案选择状态</strong>
                      <p>切换浏览模式时保留当前已选中的档案页。</p>
                    </div>
                    <el-switch v-model="settings.archiveRememberSelection" inline-prompt active-text="开" inactive-text="关" />
                  </div>
                </div>
              </section>

              <section v-else-if="activeSection === 'storage'" class="setting-panel">
                <header class="panel-header">
                  <div class="panel-heading-icon">
                    <FaIcon name="i-ri:database-2-line" />
                  </div>
                  <div>
                    <h3>文件与备份</h3>
                    <p>设置文件上传约束、失败重试以及系统备份保留策略。</p>
                  </div>
                </header>
                <el-form :model="settings" label-position="top" class="form-grid">
                  <el-form-item label="单文件大小上限">
                    <div class="control-with-unit">
                      <el-input-number v-model="settings.maxFileSize" :min="1" :max="2048" controls-position="right" />
                      <span>MB</span>
                    </div>
                  </el-form-item>
                  <el-form-item label="并发上传数量">
                    <div class="control-with-unit">
                      <el-input-number v-model="settings.concurrentUploads" :min="1" :max="10" controls-position="right" />
                      <span>个</span>
                    </div>
                  </el-form-item>
                  <el-form-item label="图片加载重试次数">
                    <div class="control-with-unit">
                      <el-input-number v-model="settings.imageLoadRetryCount" :min="0" :max="5" controls-position="right" />
                      <span>次</span>
                    </div>
                  </el-form-item>
                  <el-form-item label="允许的文件类型">
                    <el-input v-model="settings.allowedImageTypes" placeholder="使用英文逗号分隔，例如 jpg,png,pdf" />
                  </el-form-item>
                  <el-form-item label="自动备份间隔">
                    <div class="control-with-unit">
                      <el-input-number v-model="settings.backupInterval" :min="1" :max="72" :disabled="!settings.autoBackup" controls-position="right" />
                      <span>小时</span>
                    </div>
                  </el-form-item>
                  <el-form-item label="备份保留时间">
                    <div class="control-with-unit">
                      <el-input-number v-model="settings.backupRetentionDays" :min="1" :max="365" :disabled="!settings.autoBackup" controls-position="right" />
                      <span>天</span>
                    </div>
                  </el-form-item>
                </el-form>
                <div class="switch-list">
                  <div class="switch-item">
                    <div>
                      <strong>重复文件校验</strong>
                      <p>上传前校验文件特征，减少重复影像进入系统。</p>
                    </div>
                    <el-switch v-model="settings.duplicateCheckEnabled" inline-prompt active-text="开" inactive-text="关" />
                  </div>
                  <div class="switch-item">
                    <div>
                      <strong>自动备份</strong>
                      <p>按设定间隔执行系统数据备份并应用保留策略。</p>
                    </div>
                    <el-switch v-model="settings.autoBackup" inline-prompt active-text="开" inactive-text="关" />
                  </div>
                </div>
              </section>

              <section v-else-if="activeSection === 'security'" class="setting-panel">
                <header class="panel-header">
                  <div class="panel-heading-icon">
                    <FaIcon name="i-ri:shield-keyhole-line" />
                  </div>
                  <div>
                    <h3>安全策略</h3>
                    <p>统一配置登录限制、会话有效期、敏感信息展示和操作审计。</p>
                  </div>
                </header>
                <el-form :model="settings" label-position="top" class="form-grid">
                  <el-form-item label="会话超时时间">
                    <div class="control-with-unit">
                      <el-input-number v-model="settings.sessionTimeout" :min="5" :max="1440" controls-position="right" />
                      <span>分钟</span>
                    </div>
                  </el-form-item>
                  <el-form-item label="密码最小长度">
                    <div class="control-with-unit">
                      <el-input-number v-model="settings.passwordMinLength" :min="6" :max="32" controls-position="right" />
                      <span>位</span>
                    </div>
                  </el-form-item>
                  <el-form-item label="登录失败限制" class="form-item-wide">
                    <div class="control-with-unit">
                      <el-input-number v-model="settings.loginFailureLimit" :min="1" :max="20" controls-position="right" />
                      <span>次后触发限制</span>
                    </div>
                  </el-form-item>
                </el-form>
                <div class="switch-list">
                  <div class="switch-item">
                    <div>
                      <strong>登录验证码</strong>
                      <p>达到安全策略条件时要求用户完成验证码校验。</p>
                    </div>
                    <el-switch v-model="settings.loginCaptchaEnabled" inline-prompt active-text="开" inactive-text="关" />
                  </div>
                  <div class="switch-item">
                    <div>
                      <strong>操作审计</strong>
                      <p>记录关键查询、修改、下载和导出行为。</p>
                    </div>
                    <el-switch v-model="settings.operationAuditEnabled" inline-prompt active-text="开" inactive-text="关" />
                  </div>
                  <div class="switch-item">
                    <div>
                      <strong>敏感信息脱敏</strong>
                      <p>默认隐藏患者标识等敏感字段的部分内容。</p>
                    </div>
                    <el-switch v-model="settings.sensitiveDataMaskEnabled" inline-prompt active-text="开" inactive-text="关" />
                  </div>
                </div>
              </section>

              <section v-else-if="activeSection === 'notification'" class="setting-panel">
                <header class="panel-header">
                  <div class="panel-heading-icon">
                    <FaIcon name="i-ri:notification-3-line" />
                  </div>
                  <div>
                    <h3>通知告警</h3>
                    <p>配置邮件发送通道以及备份、存储空间等异常通知规则。</p>
                  </div>
                </header>
                <div class="switch-list switch-list--top">
                  <div class="switch-item">
                    <div>
                      <strong>邮件通知</strong>
                      <p>启用后可通过 SMTP 发送系统告警和运维通知。</p>
                    </div>
                    <el-switch v-model="settings.emailNotification" inline-prompt active-text="开" inactive-text="关" />
                  </div>
                </div>
                <el-form :model="settings" label-position="top" class="form-grid">
                  <el-form-item label="SMTP 地址">
                    <el-input v-model="settings.smtpServer" :disabled="!settings.emailNotification" placeholder="例如 smtp.example.com" />
                  </el-form-item>
                  <el-form-item label="SMTP 端口">
                    <el-input-number v-model="settings.smtpPort" :min="1" :max="65535" :disabled="!settings.emailNotification" controls-position="right" class="full-width" />
                  </el-form-item>
                  <el-form-item label="发件邮箱" class="form-item-wide">
                    <el-input v-model="settings.senderEmail" :disabled="!settings.emailNotification" placeholder="例如 mrr@example.com" />
                  </el-form-item>
                  <el-form-item label="存储空间告警阈值" class="form-item-wide">
                    <div class="control-with-unit">
                      <el-slider v-model="settings.storageWarningThreshold" :min="50" :max="95" :step="5" show-stops />
                      <span>{{ settings.storageWarningThreshold }}%</span>
                    </div>
                  </el-form-item>
                </el-form>
                <div class="switch-list">
                  <div class="switch-item">
                    <div>
                      <strong>备份失败告警</strong>
                      <p>自动备份执行失败时发送管理员通知。</p>
                    </div>
                    <el-switch v-model="settings.backupFailureNotification" :disabled="!settings.emailNotification" inline-prompt active-text="开" inactive-text="关" />
                  </div>
                  <div class="switch-item">
                    <div>
                      <strong>存储空间告警</strong>
                      <p>磁盘使用率达到阈值后发送管理员通知。</p>
                    </div>
                    <el-switch v-model="settings.storageWarningNotification" :disabled="!settings.emailNotification" inline-prompt active-text="开" inactive-text="关" />
                  </div>
                </div>
              </section>

              <section v-else class="setting-panel">
                <header class="panel-header">
                  <div class="panel-heading-icon">
                    <FaIcon name="i-ri:pulse-line" />
                  </div>
                  <div>
                    <h3>运维监控</h3>
                    <p>配置维护状态、日志详细程度、性能采集和服务健康检查。</p>
                  </div>
                </header>
                <el-form :model="settings" label-position="top" class="form-grid">
                  <el-form-item label="日志级别">
                    <el-select v-model="settings.logLevel" class="full-width">
                      <el-option label="Debug（调试）" value="debug" />
                      <el-option label="Info（信息）" value="info" />
                      <el-option label="Warn（警告）" value="warn" />
                      <el-option label="Error（错误）" value="error" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="慢请求阈值">
                    <div class="control-with-unit">
                      <el-input-number v-model="settings.slowRequestThreshold" :min="200" :max="30000" :step="100" controls-position="right" />
                      <span>ms</span>
                    </div>
                  </el-form-item>
                  <el-form-item label="健康检查间隔" class="form-item-wide">
                    <div class="control-with-unit">
                      <el-input-number v-model="settings.healthCheckInterval" :min="10" :max="3600" :step="10" controls-position="right" />
                      <span>秒</span>
                    </div>
                  </el-form-item>
                  <el-form-item label="维护提示文案" class="form-item-wide">
                    <el-input v-model="settings.maintenanceMessage" type="textarea" :rows="3" :disabled="!settings.maintenanceMode" maxlength="200" show-word-limit />
                  </el-form-item>
                </el-form>
                <div class="switch-list">
                  <div class="switch-item switch-item--danger">
                    <div>
                      <strong>维护模式</strong>
                      <p>开启后限制普通用户访问，请确认维护文案已填写。</p>
                    </div>
                    <el-switch v-model="settings.maintenanceMode" inline-prompt active-text="开" inactive-text="关" />
                  </div>
                  <div class="switch-item">
                    <div>
                      <strong>性能监控</strong>
                      <p>采集接口耗时和页面响应数据用于趋势分析。</p>
                    </div>
                    <el-switch v-model="settings.performanceMonitoring" inline-prompt active-text="开" inactive-text="关" />
                  </div>
                  <div class="switch-item">
                    <div>
                      <strong>错误上报</strong>
                      <p>记录前端异常和后端错误，便于统一排查问题。</p>
                    </div>
                    <el-switch v-model="settings.errorReporting" inline-prompt active-text="开" inactive-text="关" />
                  </div>
                </div>
              </section>

              <footer class="save-bar" :class="{ dirty: isDirty }">
                <div class="save-state">
                  <span class="save-dot" />
                  <div>
                    <strong>{{ isDirty ? `有 ${changedCount} 项修改尚未保存` : '当前配置已同步' }}</strong>
                    <small>{{ isDirty ? '保存后将写入服务器，失败时自动保留为本地草稿。' : `当前使用${sourceMeta.label}。` }}</small>
                  </div>
                </div>
                <el-button type="primary" :loading="saving" :disabled="loading || (!isDirty && settingsSource === 'server')" @click="handleSave">
                  保存全部设置
                </el-button>
              </footer>
            </main>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane name="department">
        <template #label>
          <span class="tab-label">
            <FaIcon name="i-ri:palette-line" />
            科室档案袋配色
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
.page-shell {
  display: grid;
  gap: 20px;
}

.settings-hero {
  display: flex;
  gap: 24px;
  align-items: center;
  justify-content: space-between;
  padding: 24px 28px;
  overflow: hidden;
  background:
    radial-gradient(circle at 92% 10%, color-mix(in srgb, var(--el-color-primary) 14%, transparent), transparent 30%),
    linear-gradient(135deg, var(--el-bg-color), color-mix(in srgb, var(--el-color-primary) 5%, var(--el-bg-color)));
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 18px;
  box-shadow: 0 12px 32px rgb(15 23 42 / 5%);
}

.hero-copy {
  display: flex;
  gap: 18px;
  align-items: center;
  min-width: 0;
}

.hero-icon {
  display: grid;
  flex: 0 0 52px;
  width: 52px;
  height: 52px;
  font-size: 25px;
  color: var(--el-color-primary);
  background: color-mix(in srgb, var(--el-color-primary) 12%, var(--el-bg-color));
  border: 1px solid color-mix(in srgb, var(--el-color-primary) 24%, transparent);
  border-radius: 16px;
  place-items: center;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.hero-actions :deep(.el-button) {
  gap: 6px;
  margin-left: 0;
}

.eyebrow {
  margin: 0 0 5px;
  font-size: 11px;
  font-weight: 700;
  color: var(--el-color-primary);
  text-transform: uppercase;
  letter-spacing: 0.14em;
}

h2 {
  margin: 0;
  font-size: 27px;
  line-height: 1.25;
  color: var(--el-text-color-primary);
}

.subtitle {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
}

.settings-tabs {
  padding: 0 22px 22px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 18px;
  box-shadow: 0 12px 32px rgb(15 23 42 / 4%);
}

.settings-tabs :deep(.el-tabs__header) {
  margin-bottom: 22px;
}

.settings-tabs :deep(.el-tabs__item) {
  height: 58px;
  padding: 0 20px;
}

.settings-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background-color: var(--el-border-color-lighter);
}

.tab-label {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  font-weight: 600;
}

.system-settings {
  min-height: 520px;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.status-card {
  display: flex;
  gap: 13px;
  align-items: center;
  min-width: 0;
  padding: 16px;
  background: var(--el-fill-color-extra-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
}

.status-icon {
  display: grid;
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  font-size: 19px;
  border-radius: 11px;
  place-items: center;
}

.status-icon--source {
  color: var(--el-color-success);
  background: color-mix(in srgb, var(--el-color-success) 12%, transparent);
}

.status-icon--change {
  color: var(--el-color-warning);
  background: color-mix(in srgb, var(--el-color-warning) 12%, transparent);
}

.status-icon--feature {
  color: var(--el-color-primary);
  background: color-mix(in srgb, var(--el-color-primary) 12%, transparent);
}

.status-icon--time {
  color: var(--el-color-info);
  background: color-mix(in srgb, var(--el-color-info) 12%, transparent);
}

.status-label {
  display: block;
  margin-bottom: 5px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.status-value {
  display: block;
  overflow: hidden;
  font-size: 15px;
  font-weight: 650;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-value--small {
  font-size: 13px;
}

.settings-workspace {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.settings-nav {
  position: sticky;
  top: 16px;
  display: grid;
  gap: 8px;
  padding: 14px;
  background: var(--el-fill-color-extra-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 15px;
}

.nav-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 4px 10px;
  font-size: 13px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.nav-item {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 18px;
  gap: 10px;
  align-items: center;
  width: 100%;
  padding: 11px;
  font: inherit;
  color: var(--el-text-color-regular);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 11px;
  transition: 0.2s ease;
}

.nav-item:hover {
  background: var(--el-bg-color);
  border-color: var(--el-border-color-lighter);
}

.nav-item.active {
  color: var(--el-color-primary);
  background: color-mix(in srgb, var(--el-color-primary) 9%, var(--el-bg-color));
  border-color: color-mix(in srgb, var(--el-color-primary) 24%, transparent);
  box-shadow: 0 7px 18px rgb(15 23 42 / 5%);
}

.nav-item-icon {
  display: grid;
  width: 34px;
  height: 34px;
  font-size: 17px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  place-items: center;
}

.nav-item.active .nav-item-icon {
  background: color-mix(in srgb, var(--el-color-primary) 13%, var(--el-bg-color));
  border-color: color-mix(in srgb, var(--el-color-primary) 25%, transparent);
}

.nav-item-copy {
  min-width: 0;
}

.nav-item-copy strong,
.nav-item-copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-item-copy strong {
  margin-bottom: 3px;
  font-size: 13px;
}

.nav-item-copy small {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.nav-arrow {
  color: var(--el-text-color-placeholder);
}

.settings-content {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.setting-panel {
  padding: 22px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 15px;
}

.panel-header {
  display: flex;
  gap: 13px;
  align-items: center;
  padding-bottom: 18px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.panel-heading-icon {
  display: grid;
  flex: 0 0 40px;
  width: 40px;
  height: 40px;
  font-size: 19px;
  color: var(--el-color-primary);
  background: color-mix(in srgb, var(--el-color-primary) 10%, transparent);
  border-radius: 12px;
  place-items: center;
}

.panel-header h3 {
  margin: 0;
  font-size: 18px;
  color: var(--el-text-color-primary);
}

.panel-header p {
  margin: 5px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 2px 20px;
}

.form-item-wide {
  grid-column: 1 / -1;
}

.form-grid :deep(.el-form-item) {
  margin-bottom: 18px;
}

.form-grid :deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.full-width,
.form-grid :deep(.el-input-number) {
  width: 100%;
}

.control-with-unit {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  width: 100%;
}

.control-with-unit > span {
  min-width: 45px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: right;
}

.switch-list {
  display: grid;
  gap: 10px;
  padding-top: 6px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.switch-list--top {
  padding-top: 0;
  padding-bottom: 18px;
  margin-bottom: 18px;
  border-top: 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.switch-item {
  display: flex;
  gap: 18px;
  align-items: center;
  justify-content: space-between;
  padding: 14px 15px;
  background: var(--el-fill-color-extra-light);
  border: 1px solid transparent;
  border-radius: 11px;
}

.switch-item:hover {
  border-color: var(--el-border-color-lighter);
}

.switch-item--danger {
  background: color-mix(in srgb, var(--el-color-danger) 5%, var(--el-fill-color-extra-light));
}

.switch-item strong {
  display: block;
  margin-bottom: 4px;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.switch-item p {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
  color: var(--el-text-color-secondary);
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
  background: color-mix(in srgb, var(--el-bg-color) 92%, transparent);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 13px;
  box-shadow: 0 12px 30px rgb(15 23 42 / 10%);
  backdrop-filter: blur(14px);
}

.save-bar.dirty {
  border-color: color-mix(in srgb, var(--el-color-warning) 35%, var(--el-border-color-lighter));
}

.save-state {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.save-state strong,
.save-state small {
  display: block;
}

.save-state strong {
  margin-bottom: 3px;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.save-state small {
  overflow: hidden;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.save-dot {
  width: 9px;
  height: 9px;
  background: var(--el-color-success);
  border-radius: 50%;
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--el-color-success) 13%, transparent);
}

.save-bar.dirty .save-dot {
  background: var(--el-color-warning);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--el-color-warning) 15%, transparent);
}

@media (max-width: 1100px) {
  .status-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .settings-workspace {
    grid-template-columns: 220px minmax(0, 1fr);
  }
}

@media (max-width: 820px) {
  .settings-hero {
    align-items: flex-start;
  }

  .settings-hero,
  .hero-actions {
    flex-direction: column;
  }

  .hero-actions {
    width: 100%;
  }

  .hero-actions :deep(.el-button) {
    width: 100%;
  }

  .settings-workspace {
    grid-template-columns: 1fr;
  }

  .settings-nav {
    position: static;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .nav-heading {
    grid-column: 1 / -1;
  }
}

@media (max-width: 600px) {
  .settings-hero,
  .setting-panel {
    padding: 18px;
  }

  .settings-tabs {
    padding-right: 14px;
    padding-left: 14px;
  }

  .status-grid,
  .form-grid,
  .settings-nav {
    grid-template-columns: 1fr;
  }

  .save-bar {
    position: static;
    align-items: stretch;
  }

  .save-bar,
  .switch-item {
    flex-direction: column;
  }

  .save-bar :deep(.el-button),
  .switch-item :deep(.el-switch) {
    width: 100%;
  }
}
</style>
