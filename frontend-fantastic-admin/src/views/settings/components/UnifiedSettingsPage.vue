<script setup lang="ts">
import { nextTick, onUnmounted, ref, watch } from 'vue'
import { useUnifiedSettings } from '../composables/useUnifiedSettings'
import AppConfigPanel from './AppConfigPanel.vue'
import ArchiveSettings from './ArchiveSettings.vue'
import DepartmentThemeSettings from './DepartmentThemeSettings.vue'
import DeveloperSettings from './DeveloperSettings.vue'
import DocumentationSettings from './DocumentationSettings.vue'
import ExternalLinksSettings from './ExternalLinksSettings.vue'
import LoginSupportSettings from './LoginSupportSettings.vue'
import SecuritySettings from './SecuritySettings.vue'
import SystemInfoSettings from './SystemInfoSettings.vue'

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
const savedRecently = ref(false)
const departmentSavedRecently = ref(false)
let saveFeedbackTimer: number | undefined
let departmentFeedbackTimer: number | undefined

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

function clearSavedFeedback() {
  savedRecently.value = false
  if (saveFeedbackTimer !== undefined) {
    window.clearTimeout(saveFeedbackTimer)
    saveFeedbackTimer = undefined
  }
}

function clearDepartmentSavedFeedback() {
  departmentSavedRecently.value = false
  if (departmentFeedbackTimer !== undefined) {
    window.clearTimeout(departmentFeedbackTimer)
    departmentFeedbackTimer = undefined
  }
}

function showSavedFeedback() {
  clearSavedFeedback()
  savedRecently.value = true
  saveFeedbackTimer = window.setTimeout(() => {
    savedRecently.value = false
    saveFeedbackTimer = undefined
  }, 1200)
}

function showDepartmentSavedFeedback() {
  clearDepartmentSavedFeedback()
  departmentSavedRecently.value = true
  departmentFeedbackTimer = window.setTimeout(() => {
    departmentSavedRecently.value = false
    departmentFeedbackTimer = undefined
  }, 1200)
}

async function handleServerSave() {
  clearSavedFeedback()
  if (await handleSave()) {
    showSavedFeedback()
  }
}

async function handleDepartmentSave() {
  clearDepartmentSavedFeedback()
  const panel = departmentThemeRef.value
  if (!panel) {
    return
  }
  await panel.saveThemes()
  await nextTick()
  if (!panel.isDirty) {
    showDepartmentSavedFeedback()
  }
}

watch(isDirty, (dirty) => {
  if (dirty) {
    clearSavedFeedback()
  }
})

watch(() => departmentThemeRef.value?.isDirty, (dirty) => {
  if (dirty) {
    clearDepartmentSavedFeedback()
  }
})

onUnmounted(() => {
  if (saveFeedbackTimer !== undefined) {
    window.clearTimeout(saveFeedbackTimer)
  }
  if (departmentFeedbackTimer !== undefined) {
    window.clearTimeout(departmentFeedbackTimer)
  }
})

void shellRef
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
          <p>统一管理系统标识、登录支持、帮助文档、档案浏览、安全策略与界面外观。</p>
        </div>
      </div>
      <div v-if="isServerSettingSection" class="header-actions">
        <el-button :disabled="loading || saving" @click="handleReload">
          <FaIcon name="i-ri:refresh-line" class="mrr-icon-interactive" />重新加载
        </el-button>
        <el-button :disabled="loading || saving" @click="handleReset">
          <FaIcon name="i-ri:restart-line" class="mrr-icon-interactive" />恢复默认
        </el-button>
      </div>
    </header>

    <div ref="shellRef" class="settings-workspace-frame">
      <div class="settings-shell">
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
            <div class="save-status" :class="{ dirty: isDirty }">
              <span class="status-dot" />
              <div>
                <strong>{{ isDirty ? `${changedKeys.length} 项修改待保存` : '所有设置已保存' }}</strong>
                <small>{{ isDirty ? '保存后写入系统数据库。' : `当前使用${sourceMeta.label}。` }}</small>
              </div>
            </div>
            <el-button
              :type="savedRecently ? 'success' : 'primary'"
              :loading="saving"
              :disabled="loading || !isDirty"
              @click="handleServerSave"
            >
              <FaIcon
                :name="savedRecently ? 'i-ri:check-line' : 'i-ri:save-3-line'"
                class="mrr-icon-interactive"
                :class="{ 'mrr-status-pop': savedRecently }"
              />
              {{ savedRecently ? '已保存' : '保存设置' }}
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
              :type="departmentSavedRecently ? 'success' : 'primary'"
              :loading="departmentThemeRef?.saving"
              :disabled="!departmentThemeRef?.isDirty"
              @click="handleDepartmentSave"
            >
              <FaIcon
                :name="departmentSavedRecently ? 'i-ri:check-line' : 'i-ri:save-3-line'"
                class="mrr-icon-interactive"
                :class="{ 'mrr-status-pop': departmentSavedRecently }"
              />
              {{ departmentSavedRecently ? '已保存' : '保存科室配色' }}
            </el-button>
          </div>

          <div v-else-if="activeSection === 'external-links'" class="sidebar-save-card">
            <div class="save-status">
              <span class="status-dot" />
              <div>
                <strong>外链设置自动保存</strong>
                <small>添加或移除后立即保存到当前浏览器。</small>
              </div>
            </div>
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
            <el-form :model="settings" label-position="top">
              <SystemInfoSettings v-if="activeSection === 'system'" v-model="settings" />
              <LoginSupportSettings v-else-if="activeSection === 'login-support'" v-model="settings" />
              <DocumentationSettings v-else-if="activeSection === 'documentation'" v-model="settings" />
              <ArchiveSettings v-else-if="activeSection === 'archive'" v-model="settings" />
              <SecuritySettings v-else-if="activeSection === 'security'" v-model="settings" />
              <DeveloperSettings v-else v-model="settings" />
            </el-form>
          </div>
          <DepartmentThemeSettings v-else-if="activeSection === 'department'" ref="departmentThemeRef" />
          <ExternalLinksSettings v-else-if="activeSection === 'external-links'" />
          <AppConfigPanel v-else ref="appConfigRef" />
        </main>
      </div>
    </div>
  </div>
</template>

<style scoped src="./unified-settings.css"></style>
