<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { reactive } from 'vue'

defineOptions({ name: 'SettingsPage' })

const settings = reactive({
  systemName: 'MRR 后台管理中心',
  swaggerUrl: '/swagger-ui/index.html',
  maxFileSize: 20,
  sessionTimeout: 120,
  logLevel: 'info',
  autoBackup: true,
  backupInterval: 6,
  emailNotification: false,
  smtpServer: '',
  smtpPort: 25,
  senderEmail: '',
  maintenanceMode: false,
  maintenanceMessage: '系统维护中，请稍后再试',
  performanceMonitoring: true,
  errorReporting: true,
})

function handleSave() {
  localStorage.setItem('MRR-ADMIN:system-settings', JSON.stringify(settings))
  ElMessage.success('设置已保存到本地草稿')
}

function handleReset() {
  localStorage.removeItem('MRR-ADMIN:system-settings')
  ElMessage.success('已清除本地草稿，当前页面保留默认值')
}
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">
          System Settings
        </p>
        <h2>系统设置</h2>
        <p class="subtitle">
          先完成新后台的配置页面容器迁移，后续再逐步接入真实系统参数接口。
        </p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="handleSave">
          保存设置
        </el-button>
        <el-button @click="handleReset">
          清除草稿
        </el-button>
      </div>
    </div>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            基础设置
          </template>
          <el-form :model="settings" label-width="120px">
            <el-form-item label="系统名称">
              <el-input v-model="settings.systemName" />
            </el-form-item>
            <el-form-item label="Swagger 地址">
              <el-input v-model="settings.swaggerUrl" />
            </el-form-item>
            <el-form-item label="最大文件大小">
              <el-input-number v-model="settings.maxFileSize" :min="1" :max="100" />
            </el-form-item>
            <el-form-item label="会话超时">
              <el-input-number v-model="settings.sessionTimeout" :min="5" :max="480" />
            </el-form-item>
            <el-form-item label="日志级别">
              <el-select v-model="settings.logLevel">
                <el-option label="debug" value="debug" />
                <el-option label="info" value="info" />
                <el-option label="warn" value="warn" />
                <el-option label="error" value="error" />
              </el-select>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            运维与通知
          </template>
          <el-form :model="settings" label-width="120px">
            <el-form-item label="自动备份">
              <el-switch v-model="settings.autoBackup" />
            </el-form-item>
            <el-form-item label="备份间隔">
              <el-input-number v-model="settings.backupInterval" :min="1" :max="24" />
            </el-form-item>
            <el-form-item label="邮件通知">
              <el-switch v-model="settings.emailNotification" />
            </el-form-item>
            <el-form-item label="SMTP 地址">
              <el-input v-model="settings.smtpServer" :disabled="!settings.emailNotification" />
            </el-form-item>
            <el-form-item label="发件邮箱">
              <el-input v-model="settings.senderEmail" :disabled="!settings.emailNotification" />
            </el-form-item>
            <el-form-item label="维护模式">
              <el-switch v-model="settings.maintenanceMode" />
            </el-form-item>
            <el-form-item label="维护文案">
              <el-input v-model="settings.maintenanceMessage" type="textarea" :rows="3" />
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
  </div>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 20px;
}

.page-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

h2 {
  margin: 0;
  font-size: 28px;
}

.subtitle {
  margin: 8px 0 0;
  color: #64748b;
}
</style>
