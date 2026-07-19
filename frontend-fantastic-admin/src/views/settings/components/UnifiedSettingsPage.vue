<script setup lang="ts">
import { ref } from 'vue'
import AppConfigPanel from './AppConfigPanel.vue'
import ArchiveSettings from './ArchiveSettings.vue'
import DepartmentThemeSettings from './DepartmentThemeSettings.vue'
import DeveloperSettings from './DeveloperSettings.vue'
import LoginSupportSettings from './LoginSupportSettings.vue'
import SecuritySettings from './SecuritySettings.vue'
import SystemInfoSettings from './SystemInfoSettings.vue'
import { useUnifiedSettings } from '../composables/useUnifiedSettings'

defineOptions({ name: 'UnifiedSettingsPage' })

interface DepartmentThemeSettingsRef {
  saving: boolean
  isDirty: boolean
  saveThemes: () => Promise<void>
}

interface AppConfigPanelRef {
  autoSaveLabel: string
}

const departmentThemeRef = ref<DepartmentThemeSettingsRef>()
const appConfigRef = ref<AppConfigPanelRef>()
const {
  settingsNavItems,
  shellRef,
  activeSection,
  activeMeta,
  loading,
  saving,
  settings,
  sourceMeta,
  lastSyncedAt,
  changedKeys,
  isDirty,
  isServerSettingSection,
  selectSection,
  handleSave,
  handleReload,
  handleReset,
} = useUnifiedSettings()
</script>

<template>
  <div class="settings-page">
    <header class="page-header">
      <div class="header-title">
        <span class="header-icon"><FaIcon name="i-ri:settings-4-line" /></span>
        <div>
          <div class="title-line">
            <h2>系统设置</h2>
            <el-tag v-if="settings.developerModeEnabled" type="danger" effect="dark" round>
              开发者模式已启用
            </el-tag>
          </div>
          <p>统一管理系统标识、登录支持、档案浏览、安全策略与界面外观。</p>
        </div>
      </div>
      <div v-if="isServerSettingSection" class="header-actions">
        <el-button :disabled="loading || saving" @click="handleReload">
          <FaIcon name="i-ri:refresh-line" />重新加载
        </el-button>
        <el-button :disabled="loading || saving" @click="handleReset">
          <FaIcon name="i-ri:restart-line" />恢复默认
        </el-button>
      </div>
    </header>

    <div ref="shellRef" class="settings-shell">
      <aside class="settings-sidebar">
        <nav class="settings-nav" aria-label="设置分类">
          <button
            v-for="item in settingsNavItems"
            :key="item.key"
            type="button"
            class="settings-nav-item"
            :class="{ active: activeSection === item.key, danger: item.key === 'developer' && settings.developerModeEnabled }"
            @click="selectSection(item.key)"
          >
            <span class="nav-icon"><FaIcon :name="item.icon" /></span>
            <span class="nav-copy">
              <strong>{{ item.title }}</strong>
              <small>{{ item.description }}</small>
            </span>
            <FaIcon name="i-ri:arrow-right-s-line" class="nav-arrow" />
          </button>
        </nav>

        <div v-if="isServerSettingSection" class="sidebar-save-card" :class="{ dirty: isDirty }">
          <div class="save-status" :class="{ dirty: isDirty }">
            <span class="status-dot" />
            <div>
              <strong>{{ isDirty ? `${changedKeys.length} 项修改待保存` : '所有设置已保存' }}</strong>
              <small>{{ isDirty ? '保存后写入系统数据库。' : `当前使用${sourceMeta.label}。` }}</small>
            </div>
          </div>
          <el-button type="primary" :loading="saving" :disabled="loading || !isDirty" @click="handleSave">
            <FaIcon name="i-ri:save-3-line" />保存设置
          </el-button>
        </div>

        <div v-else-if="activeSection === 'department'" class="sidebar-save-card" :class="{ dirty: departmentThemeRef?.isDirty }">
          <div class="save-status">
            <span class="status-dot" />
            <div>
              <strong>{{ departmentThemeRef?.isDirty ? '科室配色修改待保存' : '科室配色已保存' }}</strong>
              <small>保存后立即应用到档案袋。</small>
            </div>
          </div>
          <el-button
            type="primary"
            :loading="departmentThemeRef?.saving"
            :disabled="!departmentThemeRef?.isDirty"
            @click="departmentThemeRef?.saveThemes()"
          >
            <FaIcon name="i-ri:save-3-line" />保存科室配色
          </el-button>
        </div>

        <div v-else class="sidebar-save-card">
          <div class="save-status">
            <span class="status-dot" />
            <div>
              <strong>{{ appConfigRef?.autoSaveLabel ?? '界面外观自动保存' }}</strong>
              <small>界面外观仅保存到当前浏览器。</small>
            </div>
          </div>
        </div>

        <div class="sidebar-status">
          <div>
            <span>配置状态</span>
            <el-tag :type="sourceMeta.type" effect="light" round size="small">{{ sourceMeta.label }}</el-tag>
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
          <el-form :model="settings" label-position="top">
            <SystemInfoSettings v-if="activeSection === 'system'" v-model="settings" />
            <LoginSupportSettings v-else-if="activeSection === 'login-support'" v-model="settings" />
            <ArchiveSettings v-else-if="activeSection === 'archive'" v-model="settings" />
            <SecuritySettings v-else-if="activeSection === 'security'" v-model="settings" />
            <DeveloperSettings v-else v-model="settings" />
          </el-form>
        </div>
        <DepartmentThemeSettings v-else-if="activeSection === 'department'" ref="departmentThemeRef" />
        <AppConfigPanel v-else ref="appConfigRef" />
      </main>
    </div>
  </div>
</template>

<style scoped src="./unified-settings.css"></style>
