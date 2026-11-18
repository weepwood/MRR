<script setup lang="ts">
import type { SettingsSearchResult } from '../settings-search'
import { nextTick, onUnmounted, ref, useId, watch } from 'vue'
import { useUnifiedSettings } from '../composables/useUnifiedSettings'
import AppConfigPanel from './AppConfigPanel.vue'
import ArchiveSettings from './ArchiveSettings.vue'
import DepartmentThemeSettings from './DepartmentThemeSettings.vue'
import DeveloperSettings from './DeveloperSettings.vue'
import DocumentationSettings from './DocumentationSettings.vue'
import ExternalLinksSettings from './ExternalLinksSettings.vue'
import LoginSupportSettings from './LoginSupportSettings.vue'
import OcrClassificationSettings from './OcrClassificationSettings.vue'
import RuntimeSettings from './RuntimeSettings.vue'
import SecuritySettings from './SecuritySettings.vue'
import SettingsSearch from './SettingsSearch.vue'
import SystemInfoSettings from './SystemInfoSettings.vue'

defineOptions({ name: 'UnifiedSettingsPage' })

interface DepartmentThemeSettingsRef {
  saving: boolean
  isDirty: boolean
  saveThemes: () => Promise<void>
}

interface OcrClassificationSettingsRef {
  saving: boolean
  isDirty: boolean
  saveSettings: () => Promise<boolean>
}

interface RuntimeSettingsRef {
  canSave: boolean
  statusLabel: string
  synced: boolean
  saving: boolean
  isDirty: boolean
  saveSettings: () => Promise<boolean>
}

interface AppConfigPanelRef {
  autoSaveLabel: string
  syncScopeDescription: string
  openGroup: (group: string) => void
}

const departmentThemeRef = ref<DepartmentThemeSettingsRef>()
const ocrSettingsRef = ref<OcrClassificationSettingsRef>()
const runtimeSettingsRef = ref<RuntimeSettingsRef>()
const appConfigRef = ref<AppConfigPanelRef>()
const contentRef = ref<HTMLElement>()
const sectionHeadingRef = ref<HTMLElement>()
const sectionHeadingId = useId()
const savedRecently = ref(false)
const departmentSavedRecently = ref(false)
const ocrSavedRecently = ref(false)
const runtimeSavedRecently = ref(false)
let saveFeedbackTimer: number | undefined
let departmentFeedbackTimer: number | undefined
let ocrFeedbackTimer: number | undefined
let runtimeFeedbackTimer: number | undefined

const {
  settingsNavItems,
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

async function openSearchResult(result: SettingsSearchResult) {
  await selectSection(result.section)
  await nextTick()
  if (activeSection.value !== result.section) { return }

  if (result.section === 'appearance' && result.anchor) {
    appConfigRef.value?.openGroup(result.anchor)
    await nextTick()
  }

  const content = contentRef.value
  if (!content) { return }
  const anchor = result.anchor
    ? content.querySelector<HTMLElement>(`[data-settings-search-target="${result.anchor}"]`)
    : undefined
  const target = result.section === 'appearance' ? anchor?.querySelector<HTMLElement>('[role="button"]') : anchor
  if (!target) {
    content.scrollTop = 0
    sectionHeadingRef.value?.focus({ preventScroll: true })
    return
  }

  target.focus({ preventScroll: true })
  const bounds = target.getBoundingClientRect()
  content.scrollTop += bounds.top - content.getBoundingClientRect().top
    - (content.clientHeight - bounds.height) / 2
}

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

function clearOcrSavedFeedback() {
  ocrSavedRecently.value = false
  if (ocrFeedbackTimer !== undefined) {
    window.clearTimeout(ocrFeedbackTimer)
    ocrFeedbackTimer = undefined
  }
}

function clearRuntimeSavedFeedback() {
  runtimeSavedRecently.value = false
  if (runtimeFeedbackTimer !== undefined) {
    window.clearTimeout(runtimeFeedbackTimer)
    runtimeFeedbackTimer = undefined
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

function showOcrSavedFeedback() {
  clearOcrSavedFeedback()
  ocrSavedRecently.value = true
  ocrFeedbackTimer = window.setTimeout(() => {
    ocrSavedRecently.value = false
    ocrFeedbackTimer = undefined
  }, 1200)
}

function showRuntimeSavedFeedback() {
  clearRuntimeSavedFeedback()
  runtimeSavedRecently.value = true
  runtimeFeedbackTimer = window.setTimeout(() => {
    runtimeSavedRecently.value = false
    runtimeFeedbackTimer = undefined
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

async function handleOcrSave() {
  clearOcrSavedFeedback()
  const panel = ocrSettingsRef.value
  if (!panel) {
    return
  }
  if (await panel.saveSettings()) {
    showOcrSavedFeedback()
  }
}

async function handleRuntimeSave() {
  clearRuntimeSavedFeedback()
  const panel = runtimeSettingsRef.value
  if (!panel) {
    return
  }
  if (await panel.saveSettings()) {
    showRuntimeSavedFeedback()
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

watch(() => ocrSettingsRef.value?.isDirty, (dirty) => {
  if (dirty) {
    clearOcrSavedFeedback()
  }
})

watch(() => runtimeSettingsRef.value?.synced, (synced) => {
  if (!synced) { clearRuntimeSavedFeedback() }
})

watch(() => runtimeSettingsRef.value?.isDirty, (dirty) => {
  if (dirty) {
    clearRuntimeSavedFeedback()
  }
})

onUnmounted(() => {
  if (saveFeedbackTimer !== undefined) {
    window.clearTimeout(saveFeedbackTimer)
  }
  if (departmentFeedbackTimer !== undefined) {
    window.clearTimeout(departmentFeedbackTimer)
  }
  if (ocrFeedbackTimer !== undefined) {
    window.clearTimeout(ocrFeedbackTimer)
  }
  if (runtimeFeedbackTimer !== undefined) {
    window.clearTimeout(runtimeFeedbackTimer)
  }
})
</script>

<template>
  <div class="settings-page">
    <header class="settings-header">
      <div class="settings-title">
        <h2>系统设置</h2>
        <el-tag v-if="settings.developerModeEnabled" type="danger" effect="dark" round>
          开发者模式已启用
        </el-tag>
      </div>
      <SettingsSearch class="settings-page-search" @select="openSearchResult" />
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
            :aria-current="activeSection === item.key ? 'true' : undefined"
            @click="selectSection(item.key)"
          >
            <FaIcon :name="item.icon" class="nav-icon" aria-hidden="true" />
            <span class="nav-copy">
              <strong>{{ item.title }}</strong>
              <small>{{ item.description }}</small>
            </span>
          </button>
        </nav>

        <el-select
          :model-value="activeSection"
          class="settings-category-select"
          aria-label="设置分类"
          @change="selectSection"
        >
          <el-option
            v-for="item in settingsNavItems"
            :key="item.key"
            :label="item.title"
            :value="item.key"
          />
        </el-select>

        <div class="settings-source">
          <div>
            <span>配置状态</span>
            <el-tag :type="sourceMeta.type" effect="light" round size="small">
              {{ sourceMeta.label }}
            </el-tag>
          </div>
          <p>最近同步：{{ lastSyncedAt || '尚未同步' }}</p>
        </div>
      </aside>

      <div class="settings-panel">
        <header class="section-header">
          <div class="section-title">
            <span class="section-icon" :class="{ danger: activeSection === 'developer' && settings.developerModeEnabled }">
              <FaIcon :name="activeMeta.icon" aria-hidden="true" />
            </span>
            <div>
              <h3 :id="sectionHeadingId" ref="sectionHeadingRef" tabindex="-1">
                {{ activeMeta.title }}
              </h3>
              <p>{{ activeMeta.description }}</p>
            </div>
          </div>
          <div v-if="isServerSettingSection" class="header-actions">
            <el-button :disabled="loading || saving" @click="handleReload">
              <FaIcon name="i-lucide:refresh-cw" class="mrr-icon-interactive" />重新加载
            </el-button>
            <el-button :disabled="loading || saving" @click="handleReset">
              <FaIcon name="i-lucide:rotate-cw" class="mrr-icon-interactive" />恢复默认
            </el-button>
          </div>
        </header>

        <div :key="activeSection" ref="contentRef" class="settings-content" tabindex="0" role="region" :aria-labelledby="sectionHeadingId">
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
          <OcrClassificationSettings v-else-if="activeSection === 'ocr'" ref="ocrSettingsRef" />
          <RuntimeSettings v-else-if="activeSection === 'runtime'" ref="runtimeSettingsRef" />
          <DepartmentThemeSettings v-else-if="activeSection === 'department'" ref="departmentThemeRef" />
          <ExternalLinksSettings v-else-if="activeSection === 'external-links'" />
          <AppConfigPanel v-else ref="appConfigRef" />
        </div>

        <div v-if="isServerSettingSection" class="settings-actions" :class="{ dirty: isDirty }">
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
              :name="savedRecently ? 'i-lucide:check' : 'i-lucide:save'"
              class="mrr-icon-interactive"
              :class="{ 'mrr-status-pop': savedRecently }"
            />
            {{ savedRecently ? '已保存' : '保存设置' }}
          </el-button>
        </div>

        <div v-else-if="activeSection === 'ocr'" class="settings-actions" :class="{ dirty: ocrSettingsRef?.isDirty }">
          <div class="save-status" :class="{ dirty: ocrSettingsRef?.isDirty }">
            <span class="status-dot" />
            <div>
              <strong>{{ ocrSettingsRef?.isDirty ? 'OCR 配置修改待保存' : 'OCR 配置已保存' }}</strong>
              <small>保存后写入系统数据库，并由后端再次校验安全阈值。</small>
            </div>
          </div>
          <el-button
            :type="ocrSavedRecently ? 'success' : 'primary'"
            :loading="ocrSettingsRef?.saving"
            :disabled="!ocrSettingsRef?.isDirty"
            @click="handleOcrSave"
          >
            <FaIcon
              :name="ocrSavedRecently ? 'i-lucide:check' : 'i-lucide:save'"
              class="mrr-icon-interactive"
              :class="{ 'mrr-status-pop': ocrSavedRecently }"
            />
            {{ ocrSavedRecently ? '已保存' : '保存 OCR 配置' }}
          </el-button>
        </div>

        <div v-else-if="activeSection === 'runtime'" class="settings-actions" :class="{ dirty: runtimeSettingsRef?.isDirty }">
          <div class="save-status" :class="{ dirty: runtimeSettingsRef?.isDirty }">
            <span class="status-dot" />
            <div>
              <strong>{{ runtimeSettingsRef?.statusLabel || '运行参数尚未读取' }}</strong>
              <small>保存后立即生效，不需要重启服务。</small>
            </div>
          </div>
          <el-button
            :type="runtimeSavedRecently ? 'success' : 'primary'"
            :loading="runtimeSettingsRef?.saving"
            :disabled="!runtimeSettingsRef?.canSave"
            @click="handleRuntimeSave"
          >
            <FaIcon
              :name="runtimeSavedRecently ? 'i-lucide:check' : 'i-lucide:save'"
              class="mrr-icon-interactive"
              :class="{ 'mrr-status-pop': runtimeSavedRecently }"
            />
            {{ runtimeSavedRecently ? '已保存' : '保存运行参数' }}
          </el-button>
        </div>

        <div v-else-if="activeSection === 'department'" class="settings-actions" :class="{ dirty: departmentThemeRef?.isDirty }">
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
              :name="departmentSavedRecently ? 'i-lucide:check' : 'i-lucide:save'"
              class="mrr-icon-interactive"
              :class="{ 'mrr-status-pop': departmentSavedRecently }"
            />
            {{ departmentSavedRecently ? '已保存' : '保存科室配色' }}
          </el-button>
        </div>

        <div v-else-if="activeSection === 'external-links'" class="settings-actions">
          <div class="save-status">
            <span class="status-dot" />
            <div>
              <strong>外链设置自动保存</strong>
              <small>添加或移除后立即保存，并同步到全站「其他」菜单。</small>
            </div>
          </div>
        </div>

        <div v-else class="settings-actions">
          <div class="save-status">
            <span class="status-dot" />
            <div>
              <strong>{{ appConfigRef?.autoSaveLabel ?? '界面外观自动保存' }}</strong>
              <small>{{ appConfigRef?.syncScopeDescription ?? '界面外观同时保存在本机并同步到全站默认外观，本机折叠状态等个人偏好不受影响。' }}</small>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped src="./unified-settings.css"></style>

<style scoped>
.settings-content :deep(.control-grid) {
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 240px), 1fr));
}

.settings-content :deep(.control-grid .el-form-item),
.settings-content :deep(.control-grid .el-form-item__content) {
  min-width: 0;
}

.section-header h3:focus,
.settings-content :deep([data-settings-search-target]:focus),
.settings-content :deep([data-settings-search-target] > [role="button"]:focus) {
  outline: 2px solid var(--mrr-navigation-active-foreground, var(--mrr-primary));
  outline-offset: 3px;
}
</style>
