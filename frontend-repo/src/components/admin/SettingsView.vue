<template>
  <div class="settings-view pmr-page">
    <section class="pmr-page-header">
      <div>
        <p class="module-eyebrow">System Settings</p>
        <h2 class="pmr-page-title">系统设置</h2>
        <p class="pmr-page-subtitle">管理基础参数、安全策略、通知与维护相关配置。</p>
      </div>
      <div class="pmr-toolbar-actions">
        <el-button type="primary" @click="handleSave">
          <el-icon><Check /></el-icon>
          保存设置
        </el-button>
        <el-button @click="handleReset">
          <el-icon><RefreshLeft /></el-icon>
          重置设置
        </el-button>
      </div>
    </section>

    <el-alert v-if="error" :title="error" type="error" show-icon class="mb-16" />

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card class="pmr-panel">
          <template #header>
            <div class="card-header pmr-panel-header">
              <span>基础设置</span>
            </div>
          </template>

          <el-form :model="settings" label-width="120px">
            <el-form-item label="系统名称">
              <el-input v-model="settings.systemName" />
            </el-form-item>
            <el-form-item label="Swagger 地址">
              <el-input v-model="settings.swaggerUrl" placeholder="/swagger-ui/index.html" clearable />
            </el-form-item>
            <el-form-item label="最大文件大小">
              <el-input-number v-model="settings.maxFileSize" :min="1" :max="100" />
              <span class="unit-text">MB</span>
            </el-form-item>
            <el-form-item label="会话超时">
              <el-input-number v-model="settings.sessionTimeout" :min="5" :max="480" />
              <span class="unit-text">分钟</span>
            </el-form-item>
            <el-form-item label="日志级别">
              <el-select v-model="settings.logLevel">
                <el-option label="调试" value="debug" />
                <el-option label="信息" value="info" />
                <el-option label="警告" value="warn" />
                <el-option label="错误" value="error" />
              </el-select>
            </el-form-item>
            <el-form-item label="自动备份">
              <el-switch v-model="settings.autoBackup" />
            </el-form-item>
            <el-form-item label="备份间隔">
              <el-input-number v-model="settings.backupInterval" :min="1" :max="24" :disabled="!settings.autoBackup" />
              <span class="unit-text">小时</span>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card class="pmr-panel">
          <template #header>
            <div class="card-header pmr-panel-header">
              <span>安全设置</span>
            </div>
          </template>

          <el-form :model="settings" label-width="120px">
            <el-form-item label="密码策略">
              <el-select v-model="settings.passwordPolicy">
                <el-option label="简单" value="simple" />
                <el-option label="中等" value="medium" />
                <el-option label="复杂" value="complex" />
              </el-select>
            </el-form-item>
            <el-form-item label="登录失败限制">
              <el-input-number v-model="settings.maxLoginAttempts" :min="3" :max="10" />
              <span class="unit-text">次</span>
            </el-form-item>
            <el-form-item label="IP 白名单">
              <el-switch v-model="settings.enableWhitelist" />
            </el-form-item>
            <el-form-item label="双因子认证">
              <el-switch v-model="settings.twoFactorAuth" />
            </el-form-item>
            <el-form-item label="数据加密">
              <el-switch v-model="settings.dataEncryption" />
            </el-form-item>
            <el-form-item label="审计日志">
              <el-switch v-model="settings.auditLog" />
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="settings-row">
      <el-col :span="12">
        <el-card class="pmr-panel">
          <template #header>
            <div class="card-header pmr-panel-header">
              <span>通知设置</span>
            </div>
          </template>

          <el-form :model="settings" label-width="120px">
            <el-form-item label="邮件通知">
              <el-switch v-model="settings.emailNotification" />
            </el-form-item>
            <el-form-item label="SMTP 服务器">
              <el-input v-model="settings.smtpServer" :disabled="!settings.emailNotification" />
            </el-form-item>
            <el-form-item label="SMTP 端口">
              <el-input-number v-model="settings.smtpPort" :min="1" :max="65535" :disabled="!settings.emailNotification" />
            </el-form-item>
            <el-form-item label="发件邮箱">
              <el-input v-model="settings.senderEmail" :disabled="!settings.emailNotification" />
            </el-form-item>
            <el-form-item label="系统警报">
              <el-switch v-model="settings.systemAlerts" />
            </el-form-item>
            <el-form-item label="用户活动">
              <el-switch v-model="settings.userActivityAlerts" />
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card class="pmr-panel">
          <template #header>
            <div class="card-header pmr-panel-header">
              <span>维护设置</span>
            </div>
          </template>

          <el-form :model="settings" label-width="120px">
            <el-form-item label="维护模式">
              <el-switch v-model="settings.maintenanceMode" />
            </el-form-item>
            <el-form-item label="维护消息">
              <el-input
                v-model="settings.maintenanceMessage"
                type="textarea"
                :rows="3"
                :disabled="!settings.maintenanceMode"
                placeholder="系统维护中，请稍后再试"
              />
            </el-form-item>
            <el-form-item label="自动清理">
              <el-switch v-model="settings.autoCleanup" />
            </el-form-item>
            <el-form-item label="清理周期">
              <el-input-number v-model="settings.cleanupInterval" :min="1" :max="30" :disabled="!settings.autoCleanup" />
              <span class="unit-text">天</span>
            </el-form-item>
            <el-form-item label="性能监控">
              <el-switch v-model="settings.performanceMonitoring" />
            </el-form-item>
            <el-form-item label="错误上报">
              <el-switch v-model="settings.errorReporting" />
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="settings-row mt-20">
      <el-col :span="24">
        <el-card class="pmr-panel">
          <template #header>
            <div class="card-header pmr-panel-header">
              <span>界面样式 (动态预览)</span>
            </div>
          </template>

          <el-form :model="settings" label-width="100px" inline>
            <el-form-item label="品牌主色">
              <el-color-picker 
                v-model="settings.primaryColor" 
                @change="val => applyTheme({ ...settings, primaryColor: val })" 
              />
              <span class="unit-text" style="font-family: monospace;">{{ settings.primaryColor }}</span>
            </el-form-item>
            
            <el-form-item label="圆角半径">
              <el-input-number 
                v-model="settings.borderRadius" 
                :min="0" 
                :max="32" 
                @change="val => applyTheme({ ...settings, borderRadius: val })"
              />
              <span class="unit-text">px</span>
            </el-form-item>

            <el-form-item label="常用预设" style="margin-left: 20px;">
              <el-button-group>
                <el-button size="small" @click="setPreset('#2f6fff', 12)">默认蓝</el-button>
                <el-button size="small" @click="setPreset('#14b8a6', 8)">薄荷绿</el-button>
                <el-button size="small" @click="setPreset('#6366f1', 14)">靛青紫</el-button>
                <el-button size="small" @click="setPreset('#f43f5e', 10)">珊瑚红</el-button>
              </el-button-group>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <div class="settings-actions pmr-actions-row">
      <el-button type="primary" @click="handleSave">
        <el-icon><Check /></el-icon>
        保存设置
      </el-button>
      <el-button @click="handleReset">
        <el-icon><RefreshLeft /></el-icon>
        重置设置
      </el-button>
      <el-button type="warning" @click="exportSettings">
        <el-icon><Download /></el-icon>
        导出配置
      </el-button>
      <el-button type="info" @click="importSettings">
        <el-icon><Upload /></el-icon>
        导入配置
      </el-button>
    </div>

    <input
      ref="fileInput"
      type="file"
      accept=".json"
      class="file-input"
      @change="handleFileImport"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Download, RefreshLeft, Upload } from '@element-plus/icons-vue'
import { useAdminSettings } from '@/shared/composables/useAdminSettings'
import { applyTheme } from '@/utils/theme'

const fileInput = ref(null)
const { settings, loadSettings, saveSettings, resetSettings } = useAdminSettings()
const error = ref('')

const exportSettings = () => {
  try {
    const dataStr = JSON.stringify(settings, null, 2)
    const dataBlob = new Blob([dataStr], { type: 'application/json' })
    const url = URL.createObjectURL(dataBlob)
    const link = document.createElement('a')
    link.href = url
    link.download = `system-settings-${new Date().toISOString().split('T')[0]}.json`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    ElMessage.success('配置导出成功')
  } catch (err) {
    ElMessage.error('配置导出失败')
  }
}

const importSettings = () => {
  fileInput.value?.click()
}

const handleSave = () => {
  saveSettings(settings)
  ElMessage.success('配置保存成功')
}

const handleReset = () => {
  resetSettings()
  ElMessage.success('配置已恢复默认')
}

const setPreset = (color, radius) => {
  settings.primaryColor = color
  settings.borderRadius = radius
  applyTheme({ primaryColor: color, borderRadius: radius })
}

const handleFileImport = (event) => {
  const file = event.target.files?.[0]
  if (!file) return

  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const importedSettings = JSON.parse(String(e.target.result || '{}'))
      ElMessageBox.confirm(
        '导入配置将覆盖当前设置，确定要继续吗？',
        '确认导入',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
        .then(() => {
          saveSettings(importedSettings)
          ElMessage.success('配置导入成功')
        })
        .catch(() => {
          ElMessage.info('已取消导入')
        })
    } catch (err) {
      ElMessage.error('配置文件格式错误')
    }
  }
  reader.readAsText(file)
  event.target.value = ''
}

onMounted(() => {
  loadSettings()
})
</script>

<style scoped>
.settings-view {
  display: grid;
  gap: 20px;
}

.settings-row {
  margin-top: 0;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 700;
}

.unit-text {
  margin-left: 10px;
  color: #6b7280;
}

.settings-actions {
  margin-top: 10px;
  padding: 20px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(195, 197, 215, 0.18);
}

.settings-actions .el-button {
  margin: 0 8px;
}

.file-input {
  display: none;
}

.mb-16 {
  margin-bottom: 16px;
}

.mt-20 {
  margin-top: 20px;
}

@media (max-width: 768px) {
  .settings-actions .el-button {
    width: 100%;
    margin: 4px 0;
  }
}
</style>
