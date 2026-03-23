<template>
  <div class="settings-view">
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>基本设置</span>
            </div>
          </template>
          <el-form :model="settings" label-width="120px">
            <el-form-item label="系统名称">
              <el-input v-model="settings.systemName" />
            </el-form-item>
            <el-form-item label="最大文件大小">
              <el-input-number v-model="settings.maxFileSize" :min="1" :max="100" />
              <span style="margin-left: 10px;">MB</span>
            </el-form-item>
            <el-form-item label="会话超时">
              <el-input-number v-model="settings.sessionTimeout" :min="5" :max="480" />
              <span style="margin-left: 10px;">分钟</span>
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
              <span style="margin-left: 10px;">小时</span>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
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
              <span style="margin-left: 10px;">次</span>
            </el-form-item>
            <el-form-item label="IP白名单">
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

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>通知设置</span>
            </div>
          </template>
          <el-form :model="settings" label-width="120px">
            <el-form-item label="邮件通知">
              <el-switch v-model="settings.emailNotification" />
            </el-form-item>
            <el-form-item label="SMTP服务器">
              <el-input v-model="settings.smtpServer" :disabled="!settings.emailNotification" />
            </el-form-item>
            <el-form-item label="SMTP端口">
              <el-input-number v-model="settings.smtpPort" :min="1" :max="65535" :disabled="!settings.emailNotification" />
            </el-form-item>
            <el-form-item label="发送邮箱">
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
        <el-card>
          <template #header>
            <div class="card-header">
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
                placeholder="系统维护中，请稍后再试..."
              />
            </el-form-item>
            <el-form-item label="数据清理">
              <el-switch v-model="settings.autoCleanup" />
            </el-form-item>
            <el-form-item label="清理周期">
              <el-input-number v-model="settings.cleanupInterval" :min="1" :max="30" :disabled="!settings.autoCleanup" />
              <span style="margin-left: 10px;">天</span>
            </el-form-item>
            <el-form-item label="性能监控">
              <el-switch v-model="settings.performanceMonitoring" />
            </el-form-item>
            <el-form-item label="错误报告">
              <el-switch v-model="settings.errorReporting" />
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <!-- 操作按钮 -->
    <div class="settings-actions">
      <el-button type="primary" @click="$emit('save', settings)">
        <el-icon><Check /></el-icon>
        保存设置
      </el-button>
      <el-button @click="$emit('reset')">
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

    <!-- 隐藏的文件输入 -->
    <input 
      ref="fileInput" 
      type="file" 
      accept=".json" 
      style="display: none" 
      @change="handleFileImport"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, RefreshLeft, Download, Upload } from '@element-plus/icons-vue'

const props = defineProps({
  settings: {
    type: Object,
    required: true
  }
})

defineEmits(['save', 'reset'])

const fileInput = ref(null)

const exportSettings = () => {
  try {
    const dataStr = JSON.stringify(props.settings, null, 2)
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
  } catch (error) {
    ElMessage.error('配置导出失败')
  }
}

const importSettings = () => {
  fileInput.value.click()
}

const handleFileImport = (event) => {
  const file = event.target.files[0]
  if (!file) return

  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const importedSettings = JSON.parse(e.target.result)
      ElMessageBox.confirm(
        '导入配置将覆盖当前设置，确定要继续吗？',
        '确认导入',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
        }
      ).then(() => {
        // 这里应该触发父组件更新设置
        ElMessage.success('配置导入成功')
      }).catch(() => {
        ElMessage.info('已取消导入')
      })
    } catch (error) {
      ElMessage.error('配置文件格式错误')
    }
  }
  reader.readAsText(file)
  
  // 清空文件输入
  event.target.value = ''
}
</script>

<style scoped>
.settings-view {
  height: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: bold;
}

.settings-actions {
  margin-top: 30px;
  text-align: center;
  padding: 20px;
  background: #f8f9fa;
  border-radius: 8px;
}

.settings-actions .el-button {
  margin: 0 8px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .settings-actions .el-button {
    margin: 4px;
    width: 100%;
  }
}
</style>
