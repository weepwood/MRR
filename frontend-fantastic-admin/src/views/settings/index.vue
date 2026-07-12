<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { nextTick, onMounted, reactive, ref } from 'vue'
import { getSystemSettings, saveSystemSettings } from '@/api/modules/settings'
import AppConfigPanel from './components/AppConfigPanel.vue'

defineOptions({ name: 'SettingsPage' })

const activeTab = ref<'system' | 'app'>('system')
const loading = ref(false)
const saving = ref(false)

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

const LOCAL_KEY = 'MRR-ADMIN:system-settings'

/** 加载本地草稿覆盖默认值（优先从后端，回退到本地） */
async function loadSettings() {
  loading.value = true
  try {
    const res = await getSystemSettings()
    const serverSettings = res.data
    if (serverSettings && Object.keys(serverSettings).length > 0) {
      // 后端返回的是字符串键值对，需将数字/布尔字段转换
      Object.assign(settings, serverSettings)
      // 类型转换：将字符串还原为正确类型
      const intFields = ['maxFileSize', 'sessionTimeout', 'backupInterval', 'smtpPort']
      const boolFields = ['autoBackup', 'emailNotification', 'maintenanceMode', 'performanceMonitoring', 'errorReporting']
      for (const f of intFields) {
        const v = Number((settings as any)[f])
        if (!Number.isNaN(v)) (settings as any)[f] = v
      }
      for (const f of boolFields) {
        (settings as any)[f] = (settings as any)[f] === 'true' || (settings as any)[f] === true
      }
      // 延迟同步到本地草稿
      syncToLocal()
      return
    }
  }
  catch {
    // 后端不可用时回退到本地草稿
  }
  finally {
    // 从 localStorage 读取覆盖默认值
    try {
      const raw = localStorage.getItem(LOCAL_KEY)
      if (raw) {
        const local = JSON.parse(raw)
        Object.assign(settings, local)
      }
    }
    catch { /* 忽略解析错误 */ }
    loading.value = false
  }
}

function syncToLocal() {
  try {
    localStorage.setItem(LOCAL_KEY, JSON.stringify(settings))
  }
  catch { /* 忽略 */ }
}

/** 将 settings 中 boolean/number 字段转为字符串以便后端存储 */
function prepareSaveData(): Record<string, string> {
  const data: Record<string, string> = {}
  const intFields = new Set(['maxFileSize', 'sessionTimeout', 'backupInterval', 'smtpPort'])
  const boolFields = new Set(['autoBackup', 'emailNotification', 'maintenanceMode', 'performanceMonitoring', 'errorReporting'])
  for (const [key, val] of Object.entries(settings)) {
    if (intFields.has(key)) {
      data[key] = String(val)
    }
    else if (boolFields.has(key)) {
      data[key] = val ? 'true' : 'false'
    }
    else {
      data[key] = String(val ?? '')
    }
  }
  return data
}

async function handleSave() {
  saving.value = true
  try {
    await saveSystemSettings(prepareSaveData())
    syncToLocal()
    ElMessage.success('设置已保存到服务器')
  }
  catch {
    // 后端保存失败时降级到本地
    syncToLocal()
    ElMessage.warning('服务端保存失败，已保存到本地草稿')
  }
  finally {
    saving.value = false
  }
}

function handleReset() {
  localStorage.removeItem(LOCAL_KEY)
  ElMessage.success('已清除本地草稿，下次加载将使用默认值')
}

onMounted(async () => {
  await nextTick()
  loadSettings()
})
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
          管理系统配置与浏览器本地应用偏好
        </p>
      </div>
      <div v-if="activeTab === 'system'" class="header-actions">
        <el-button type="primary" :loading="saving" @click="handleSave">
          保存设置
        </el-button>
        <el-button :disabled="loading" @click="handleReset">
          清除草稿
        </el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="系统设置" name="system">
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
      </el-tab-pane>

      <el-tab-pane label="应用配置" name="app">
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
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

h2 {
  margin: 0;
  font-size: 28px;
}

.subtitle {
  margin: 8px 0 0;
  color: var(--text-secondary);
}
</style>
