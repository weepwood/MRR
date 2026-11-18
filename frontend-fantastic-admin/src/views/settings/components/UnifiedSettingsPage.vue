<script setup lang="ts">
import type { SettingsSection } from '../composables/useUnifiedSettings'
import type { SettingsSearchResult } from '../settings-search'
import { computed, nextTick, onUnmounted, ref, useId, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { actionDialog } from '@/ui/components/ActionDialog'
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
  changedKeys,
  isDirty,
  isServerSettingSection,
  selectSection,
  handleSave,
  handleReload,
  handleReset,
  discardUnsavedChanges,
} = useUnifiedSettings()

async function openSearchResult(result: SettingsSearchResult) {
  await requestSection(result.section)
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
  const target = anchor ?? sectionHeadingRef.value
  if (!target) { return }

  target.focus({ preventScroll: true })
  const bounds = target.getBoundingClientRect()
  const margin = Number.parseFloat(getComputedStyle(target).scrollMarginTop) || 16
  if (bounds.top < margin || bounds.bottom > window.innerHeight - 16) {
    window.scrollTo({ top: window.scrollY + bounds.top - margin, behavior: 'auto' })
  }
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

/** 当前分类自管保存的配置面板是否有未保存修改（服务器配置区由 isDirty 统一判断）。 */
function activePanelDirty(): boolean {
  if (activeSection.value === 'ocr') { return Boolean(ocrSettingsRef.value?.isDirty) }
  if (activeSection.value === 'runtime') { return Boolean(runtimeSettingsRef.value?.isDirty) }
  if (activeSection.value === 'department') { return Boolean(departmentThemeRef.value?.isDirty) }
  return false
}

const hasUnsavedChanges = computed(() => isDirty.value || activePanelDirty())

/** 内容区重置令牌：递增后自管保存的面板重新挂载，草稿随之丢弃。 */
const panelResetToken = ref(0)

/**
 * 提示文案按作用域区分：切换分类只会丢当前分类面板的草稿，服务器配置区的修改会
 * 一直保留在本页；离开页面则两类修改都会丢。
 */
function unsavedSummary(context: 'section' | 'page'): string {
  if (context === 'section') { return '当前分类的配置修改尚未保存' }
  if (isDirty.value && activePanelDirty()) {
    return `当前有 ${changedKeys.value.length} 项设置修改，以及当前分类的配置修改尚未保存`
  }
  if (isDirty.value) { return `当前有 ${changedKeys.value.length} 项设置修改尚未保存` }
  return '当前分类的配置修改尚未保存'
}

async function savePanelChanges(): Promise<boolean> {
  if (activeSection.value === 'ocr' && ocrSettingsRef.value?.isDirty) {
    if (!await ocrSettingsRef.value.saveSettings()) { return false }
  }
  if (activeSection.value === 'runtime' && runtimeSettingsRef.value?.isDirty) {
    if (!await runtimeSettingsRef.value.saveSettings()) { return false }
  }
  if (activeSection.value === 'department' && departmentThemeRef.value?.isDirty) {
    await departmentThemeRef.value.saveThemes()
    if (departmentThemeRef.value?.isDirty) { return false }
  }
  return true
}

async function saveUnsavedChanges(context: 'section' | 'page'): Promise<boolean> {
  // 切换分类时服务器配置区的修改会保留，不顺手写库：这条路径没有经过变更清单确认。
  // 用户已在离开提示里确认过保存意图，页面级保存也不再重复列出清单。
  if (context === 'page' && isDirty.value && !await handleSave({ confirmChanges: false })) {
    return false
  }
  return savePanelChanges()
}

/**
 * 放弃修改必须真正回滚：设置页被 KeepAlive 缓存，离开再回来不会重新拉取设置，
 * 只 return true 会把被放弃的值留在表单里，下一次保存就会写进系统数据库。
 */
function discardUnsavedChangesFor(context: 'section' | 'page') {
  if (context === 'page' && isDirty.value) {
    discardUnsavedChanges()
  }
  if (activePanelDirty()) {
    panelResetToken.value += 1
  }
}

/** 离开当前内容前确认：保存后再走、放弃修改，或留在原处（取消）。 */
async function confirmLeavingUnsavedChanges(context: 'section' | 'page'): Promise<boolean> {
  const consequence = context === 'section'
    ? '切换分类后该分类的配置修改会丢失'
    : '离开本页后这些修改会丢失'
  const choice = await actionDialog.choose(
    `${unsavedSummary(context)}，${consequence}。是否先保存？`,
    '有未保存的设置',
    {
      type: 'warning',
      confirmButtonText: context === 'section' ? '保存并继续' : '保存并离开',
      extraButtonText: '放弃修改',
      cancelButtonText: '取消',
    },
  )
  if (choice === 'cancel') { return false }
  if (choice === 'extra') {
    discardUnsavedChangesFor(context)
    return true
  }
  return saveUnsavedChanges(context)
}

/**
 * 切换设置分类：服务器配置共用同一份表单状态，切换分类不会丢修改，因此只在
 * 当前分类由面板自管保存且存在未保存修改时提示，避免每次换分类都弹窗。
 */
async function requestSection(section: SettingsSection) {
  if (section === activeSection.value) { return }
  if (activePanelDirty() && !await confirmLeavingUnsavedChanges('section')) { return }
  await selectSection(section)
}

onBeforeRouteLeave(async (to) => {
  // 会话失效后跳登录页时不拦截：此时保存请求大概率会 401，拦下来只会卡住跳转。
  if (to.name === 'login' || to.path.startsWith('/login')) { return true }
  if (!hasUnsavedChanges.value) { return true }
  return confirmLeavingUnsavedChanges('page')
})

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
    <header class="page-header">
      <div class="header-title">
        <div>
          <p class="eyebrow">
            System Settings
          </p>
          <div class="title-line">
            <h2>系统设置</h2>
            <el-tag v-if="settings.developerModeEnabled" type="danger" effect="dark" round>
              开发者模式已启用
            </el-tag>
          </div>
          <p class="subtitle">
            统一管理系统标识、登录支持、帮助文档、档案浏览、OCR 分类、安全策略与界面外观。
          </p>
        </div>
      </div>
      <div v-if="isServerSettingSection" class="header-actions">
        <el-button :disabled="loading || saving" @click="handleReload">
          <FaIcon name="i-lucide:refresh-cw" class="mrr-icon-interactive" />重新加载
        </el-button>
        <el-button :disabled="loading || saving" @click="handleReset">
          <FaIcon name="i-lucide:rotate-cw" class="mrr-icon-interactive" />恢复默认
        </el-button>
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
    </header>

    <div class="settings-layout">
      <aside class="settings-sidebar" aria-label="设置目录">
        <SettingsSearch @select="openSearchResult" />
        <nav class="settings-nav" aria-label="设置分类">
          <button
            v-for="item in settingsNavItems"
            :key="item.key"
            type="button"
            class="settings-nav-item"
            :class="{ active: activeSection === item.key, danger: item.key === 'developer' && settings.developerModeEnabled }"
            :aria-current="activeSection === item.key ? 'true' : undefined"
            @click="requestSection(item.key)"
          >
            <FaIcon :name="item.icon" class="nav-icon" aria-hidden="true" />
            <strong class="nav-title">{{ item.title }}</strong>
          </button>
        </nav>

        <el-select
          :model-value="activeSection"
          class="settings-category-select"
          aria-label="设置分类"
          @change="(value: SettingsSection) => requestSection(value)"
        >
          <el-option
            v-for="item in settingsNavItems"
            :key="item.key"
            :label="item.title"
            :value="item.key"
          />
        </el-select>
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
        </header>

        <div
          :key="`${activeSection}-${panelResetToken}`"
          ref="contentRef"
          class="settings-content"
          role="region"
          :aria-busy="isServerSettingSection ? loading : undefined"
          :aria-labelledby="sectionHeadingId"
        >
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

        <div v-if="activeSection === 'ocr'" class="settings-actions" :class="{ dirty: ocrSettingsRef?.isDirty }">
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
.settings-content :deep([data-settings-search-target]:focus) {
  outline: 2px solid var(--mrr-primary);
  outline-offset: 3px;
}

.section-header h3,
.settings-content :deep([data-settings-search-target]) {
  scroll-margin-top: calc(var(--g-header-actual-height, 0px) + var(--g-toolbar-actual-height, 0px) + var(--g-tabbar-actual-height, 0px) + var(--mrr-space-4));
}
</style>
