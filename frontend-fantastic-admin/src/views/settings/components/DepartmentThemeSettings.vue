<script setup lang="ts">
import type { ArchiveDepartmentTheme, ArchiveDepartmentThemeHistory } from '@/utils/archive-department-theme'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { getSystemSettings, setSetting } from '@/api/modules/settings'
import { getDepartments } from '@/api/modules/statistics'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import { actionDialog } from '@/ui/components/ActionDialog'
import {
  addArchiveDepartmentThemeHistoryToLocal,
  ARCHIVE_DEPARTMENT_THEME_PRESETS,
  ARCHIVE_DEPARTMENT_THEME_SETTING_KEY,
  archiveDepartmentThemeCssVariables,
  isArchiveThemeColor,
  loadArchiveDepartmentThemeHistoryFromLocal,
  loadArchiveDepartmentThemesFromLocal,
  normalizeArchiveDepartmentThemes,
  normalizeDepartmentName,
  resolveArchiveDepartmentTheme,
  saveArchiveDepartmentThemesToLocal,
} from '@/utils/archive-department-theme'
import { hasKey } from '@/utils/object'

defineOptions({ name: 'DepartmentThemeSettings' })

interface EditableDepartmentTheme extends ArchiveDepartmentTheme {
  id: number
}

const loading = ref(false)
/** 首次读取（服务器或本地回退）完成前，编辑区不展示「自动配色」空态。 */
const loaded = ref(false)
const syncState = ref<'unknown' | 'local' | 'server' | 'save-failed'>('unknown')
const saving = ref(false)
const rows = ref<EditableDepartmentTheme[]>([])
const departments = ref<string[]>([])
const departmentLoading = ref(true)
const departmentError = ref(false)
const showAllPreviews = ref(false)
const previewLimit = 12
const historyVisible = ref(false)
const history = ref<ArchiveDepartmentThemeHistory[]>([])
const persistedThemes = ref<ArchiveDepartmentTheme[]>([])
const importInput = ref<HTMLInputElement>()
let nextId = 1

const colorPresets = ARCHIVE_DEPARTMENT_THEME_PRESETS.flatMap(item => [item.folderColor, item.stripColor])
const hasRows = computed(() => rows.value.length > 0)
const hasHistory = computed(() => history.value.length > 0)
const controlsDisabled = computed(() => !loaded.value || loading.value || saving.value)
const isDirty = computed(() => loaded.value && JSON.stringify(normalizedRows()) !== JSON.stringify(persistedThemes.value))
const configuredDepartments = computed(() => new Set(rows.value.map(r => r.department.trim().toLocaleLowerCase('zh-CN'))))
const availableDepartments = computed(() => departments.value.filter(d => !configuredDepartments.value.has(d.toLocaleLowerCase('zh-CN'))))
const previewDepartments = computed(() => {
  const names = [...departments.value, ...normalizedRows().map(row => row.department)]
  return [...new Map(names.map(name => [name.toLocaleLowerCase('zh-CN'), name])).values()]
})
const previewThemes = computed(() => {
  const configured = normalizedRows()
  return previewDepartments.value.map(department => ({
    ...resolveArchiveDepartmentTheme(department, configured),
    custom: configured.some(row => normalizeDepartmentName(row.department).toLocaleLowerCase('zh-CN') === department.toLocaleLowerCase('zh-CN')),
  }))
})
const visibleThemes = computed(() => showAllPreviews.value ? previewThemes.value : previewThemes.value.slice(0, previewLimit))
const exampleThemes = ARCHIVE_DEPARTMENT_THEME_PRESETS.map((theme, index) => ({ ...theme, department: `预设色 ${index + 1}` }))

function customizeTheme(theme: ArchiveDepartmentTheme) {
  if (controlsDisabled.value || configuredDepartments.value.has(theme.department.toLocaleLowerCase('zh-CN'))) { return }
  rows.value.push(toEditable({ department: theme.department, folderColor: theme.folderColor, stripColor: theme.stripColor }))
}

function toEditable(theme: ArchiveDepartmentTheme): EditableDepartmentTheme {
  return { ...theme, id: nextId++ }
}

function setRows(themes: ArchiveDepartmentTheme[]) {
  rows.value = themes.map(toEditable)
}

function addDepartmentTheme() {
  if (controlsDisabled.value) { return }
  const preset = ARCHIVE_DEPARTMENT_THEME_PRESETS[rows.value.length % ARCHIVE_DEPARTMENT_THEME_PRESETS.length]
  rows.value.push(toEditable({
    department: '',
    folderColor: preset.folderColor,
    stripColor: preset.stripColor,
  }))
}

function removeDepartmentTheme(id: number) {
  rows.value = rows.value.filter(item => item.id !== id)
}

function resetToAutomaticThemes() {
  actionDialog.confirm(
    '恢复自动配色将清空所有已配置的科室颜色，确定继续？',
    '确认恢复',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
  ).then(() => {
    rows.value = []
    ElMessage.success('已切换为自动配色')
  }).catch(() => {})
}

function previewStyle(row: ArchiveDepartmentTheme) {
  return archiveDepartmentThemeCssVariables(row) as Record<string, string>
}

function validateRows() {
  const normalizedNames = new Set<string>()
  for (const row of rows.value) {
    const department = row.department.trim()
    if (!department) {
      ElMessage.warning('请填写科室名称')
      return false
    }

    const key = department.toLocaleLowerCase('zh-CN')
    if (normalizedNames.has(key)) {
      ElMessage.warning(`科室“${department}”重复，请合并后再保存`)
      return false
    }
    normalizedNames.add(key)

    if (!isArchiveThemeColor(row.folderColor) || !isArchiveThemeColor(row.stripColor)) {
      ElMessage.warning(`科室“${department}”的颜色格式无效，请使用 6 位十六进制颜色`)
      return false
    }
  }
  return true
}

function normalizedRows() {
  return normalizeArchiveDepartmentThemes(rows.value.map(row => ({
    department: row.department,
    folderColor: row.folderColor,
    stripColor: row.stripColor,
  })))
}

function refreshHistory() {
  history.value = loadArchiveDepartmentThemeHistoryFromLocal()
}

function formatHistoryTime(value: string) {
  return dayjs(value).format('YYYY-MM-DD HH:mm')
}

function restoreHistory(item: ArchiveDepartmentThemeHistory) {
  setRows(item.themes)
  historyVisible.value = false
  ElMessage.success('历史配色已恢复，请保存后生效')
}

async function loadThemes() {
  loading.value = true
  let themes = loadArchiveDepartmentThemesFromLocal()
  syncState.value = 'local'
  try {
    const response = await getSystemSettings()
    const settings = response.data ?? {}
    if (hasKey(settings, ARCHIVE_DEPARTMENT_THEME_SETTING_KEY)) {
      themes = normalizeArchiveDepartmentThemes(settings[ARCHIVE_DEPARTMENT_THEME_SETTING_KEY])
      saveArchiveDepartmentThemesToLocal(themes)
      syncState.value = 'server'
    }
  }
  catch {
    ElMessage.warning('服务端设置读取失败，已加载本地科室配色')
  }
  finally {
    setRows(themes)
    persistedThemes.value = themes
    refreshHistory()
    loaded.value = true
    loading.value = false
  }
}

async function loadDepartments() {
  departmentLoading.value = true
  departmentError.value = false
  try {
    const res = await getDepartments()
    departments.value = [...new Set((res.data ?? []).map(name => normalizeDepartmentName(name)))]
  }
  catch {
    departmentError.value = true
  }
  finally {
    departmentLoading.value = false
  }
}

async function saveThemes() {
  if (controlsDisabled.value || !validateRows()) {
    return
  }

  const themes = normalizedRows()
  saving.value = true
  if (JSON.stringify(themes) !== JSON.stringify(persistedThemes.value)) {
    history.value = addArchiveDepartmentThemeHistoryToLocal(persistedThemes.value)
  }
  saveArchiveDepartmentThemesToLocal(themes)
  persistedThemes.value = themes
  syncState.value = 'local'
  try {
    await setSetting(ARCHIVE_DEPARTMENT_THEME_SETTING_KEY, JSON.stringify(themes))
    setRows(themes)
    syncState.value = 'server'
    ElMessage.success('科室档案袋配色已保存到服务器')
  }
  catch {
    syncState.value = 'save-failed'
    ElMessage.error('服务端保存失败，配色已保存到当前浏览器')
  }
  finally {
    saving.value = false
  }
}

function handleExport() {
  if (controlsDisabled.value) { return }
  const themes = normalizedRows()
  const url = URL.createObjectURL(new Blob([JSON.stringify(themes, null, 2)], { type: 'application/json' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `mrr-department-themes-${new Date().toISOString().slice(0, 10)}.json`
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success('科室配色已导出')
}

function triggerImport() {
  if (controlsDisabled.value) { return }
  importInput.value?.click()
}

async function handleImport(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || controlsDisabled.value) {
    return
  }

  try {
    const data: unknown = JSON.parse(await file.text())
    if (!Array.isArray(data)) {
      throw new TypeError('配色文件必须是 JSON 数组')
    }
    setRows(normalizeArchiveDepartmentThemes(data))
    ElMessage.success('科室配色已导入，请保存后生效')
  }
  catch {
    ElMessage.error('导入失败，请选择有效的科室配色 JSON 文件')
  }
  finally {
    input.value = ''
  }
}

defineExpose({ isDirty, saving, saveThemes, loaded, syncState })

onMounted(() => {
  loadThemes()
  loadDepartments()
})
</script>

<template>
  <section v-loading="loading && loaded" class="setting-section department-theme-settings">
    <div class="setting-group">
      <div class="group-heading department-preview-heading">
        <div>
          <strong>{{ isDirty ? '待保存配色预览' : '当前配色预览' }}</strong>
          <p>未单独设置的科室按名称稳定生成颜色；以下预览与档案袋使用同一配色规则。</p>
        </div>
        <span v-if="loaded && previewThemes.length" class="preview-count">{{ previewThemes.length }} 个科室</span>
      </div>
      <AppLoading v-if="!loaded || departmentLoading" type="stats" :cols="4" />
      <template v-else>
        <div v-if="departmentError" class="department-preview-notice" role="status">
          <span>科室列表读取失败，已配置的科室仍可预览。</span>
          <el-button link type="primary" @click="loadDepartments">
            重新读取
          </el-button>
        </div>
        <p v-if="!previewThemes.length" class="department-preview-notice">
          暂无可用科室名称。以下为系统自动配色使用的预设色板，仅供参考。
        </p>
        <div class="department-preview-grid" :class="{ 'is-example': !previewThemes.length }">
          <article v-for="theme in visibleThemes.length ? visibleThemes : exampleThemes" :key="theme.department" class="department-preview-item" :data-department="theme.department">
            <div class="archive-theme-preview" :style="previewStyle(theme)">
              <i />
              <div class="preview-identity">
                <FaIcon name="i-lucide:folder" aria-hidden="true" />
                <span>{{ theme.department }}</span>
              </div>
              <div class="preview-meta">
                <span title="档案袋主色"><b class="folder-color-swatch" />{{ theme.folderColor }}</span>
                <span title="顶部色条"><b class="strip-color-swatch" />{{ theme.stripColor }}</span>
              </div>
            </div>
            <div v-if="previewThemes.length" class="preview-footer">
              <span>{{ 'custom' in theme && theme.custom ? '自定义' : '自动配色' }}</span>
              <el-button v-if="!('custom' in theme && theme.custom)" link type="primary" :disabled="controlsDisabled" :aria-label="`自定义 ${theme.department}`" @click="customizeTheme(theme)">
                自定义
              </el-button>
            </div>
          </article>
        </div>
        <div v-if="previewThemes.length > previewLimit" class="preview-more">
          <el-button @click="showAllPreviews = !showAllPreviews">
            {{ showAllPreviews ? '收起部分科室' : `查看全部 ${previewThemes.length} 个科室` }}
          </el-button>
        </div>
      </template>
    </div>

    <div class="setting-group">
      <div class="group-heading department-theme-header">
        <div>
          <strong>自定义配色</strong>
          <p>只保存需要单独设置的科室，其他科室继续使用自动配色。</p>
        </div>
        <div class="department-theme-actions">
          <el-button :disabled="controlsDisabled || !hasHistory" @click="historyVisible = true">
            历史配色
          </el-button>
          <el-button :disabled="controlsDisabled" @click="resetToAutomaticThemes">
            恢复自动配色
          </el-button>
          <el-button :disabled="controlsDisabled" @click="handleExport">
            <FaIcon name="i-lucide:download" />导出配色
          </el-button>
          <el-button :disabled="controlsDisabled" @click="triggerImport">
            <FaIcon name="i-lucide:upload" />导入配色
          </el-button>
          <el-button type="primary" plain :disabled="controlsDisabled" @click="addDepartmentTheme">
            <FaIcon name="i-lucide:plus" />添加科室配色
          </el-button>
        </div>
      </div>
      <input ref="importInput" class="import-input" type="file" accept="application/json,.json" @change="handleImport">
      <AppLoading v-if="!loaded" type="table" :rows="2" :cols="5" />
      <div v-else-if="hasRows" class="theme-editor">
        <div v-for="row in rows" :key="row.id" class="theme-editor-row">
          <div class="department-name-field">
            <span class="color-field__label">科室名称</span>
            <el-select v-model="row.department" filterable allow-create clearable placeholder="选择或输入科室" class="department-theme-select" aria-label="科室名称">
              <el-option v-for="d in availableDepartments" :key="d" :label="d" :value="d" />
            </el-select>
          </div>

          <div class="color-field">
            <span class="color-field__label">
              档案袋主色
            </span>
            <el-color-picker v-model="row.folderColor" :predefine="colorPresets" aria-label="档案袋主色" />
            <el-input v-model="row.folderColor" maxlength="7" aria-label="档案袋主色值" />
          </div>

          <div class="color-field">
            <span class="color-field__label">
              顶部色条
            </span>
            <el-color-picker v-model="row.stripColor" :predefine="colorPresets" aria-label="顶部色条" />
            <el-input v-model="row.stripColor" maxlength="7" aria-label="顶部色条色值" />
          </div>

          <div class="archive-theme-preview" :style="previewStyle(row)">
            <i />
            <span>{{ row.department || '科室预览' }}</span>
          </div>

          <el-button type="danger" link @click="removeDepartmentTheme(row.id)">
            删除
          </el-button>
        </div>
      </div>

      <p v-else class="department-preview-notice">
        当前没有自定义配色。可从上方预览选择科室，或手动添加。
      </p>
    </div>
  </section>

  <el-dialog v-model="historyVisible" align-center title="历史配色" width="min(680px, calc(100% - 32px))">
    <div v-if="hasHistory" class="theme-history-list">
      <article v-for="item in history" :key="item.savedAt" class="theme-history-item">
        <div class="theme-history-summary">
          <strong>{{ formatHistoryTime(item.savedAt) }}</strong>
          <span>{{ item.themes.length ? `${item.themes.length} 个科室配色` : '自动配色' }}</span>
        </div>
        <div v-if="item.themes.length" class="theme-history-swatches" aria-label="历史配色预览">
          <i
            v-for="theme in item.themes.slice(0, 6)"
            :key="`${theme.department}-${theme.folderColor}-${theme.stripColor}`"
            :style="{ background: theme.folderColor, borderColor: theme.stripColor }"
          />
        </div>
        <el-button plain size="small" @click="restoreHistory(item)">
          恢复
        </el-button>
      </article>
    </div>
    <AppEmpty v-else icon="i-lucide:history" description="暂无历史配色" />
    <template #footer>
      <el-button @click="historyVisible = false">
        关闭
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.setting-group {
  min-width: 0;
  padding: var(--settings-group-inset, var(--mrr-space-4));
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-card);
}

.group-heading { margin-bottom: var(--mrr-space-4); }

.department-preview-heading {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  justify-content: space-between;
}

.preview-count,
.department-preview-notice,
.preview-footer {
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-muted-foreground);
}

.department-preview-notice {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  margin: 0 0 var(--mrr-space-3);
  line-height: 1.6;
}

.department-preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 200px), 1fr));
  gap: var(--mrr-space-3);
}

.department-preview-item { min-width: 0; }
.preview-identity { display: flex; gap: var(--mrr-space-2); align-items: center; min-width: 0; }
.preview-identity :deep(svg) { flex: none; color: var(--folder-accent); }
.preview-meta { display: flex; flex-wrap: wrap; gap: var(--mrr-space-2); }
.preview-meta > span { display: flex; gap: 4px; align-items: center; font-size: var(--mrr-font-size-xs); font-weight: 400; color: var(--mrr-muted-foreground); }
.preview-meta b { flex: none; width: 10px; height: 10px; border-radius: 3px; }
.folder-color-swatch { background: var(--folder-accent); }
.strip-color-swatch { background: var(--folder-strip); }
.preview-footer { display: flex; gap: var(--mrr-space-2); align-items: center; justify-content: space-between; min-height: 28px; padding-inline: var(--mrr-space-1); }
.preview-more { display: flex; justify-content: center; margin-top: var(--mrr-space-3); }

.department-theme-select {
  width: 100%;
}

.department-theme-header {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.department-theme-header > div:first-child {
  flex: 1 1 260px;
  min-width: 0;
}

.department-theme-actions {
  display: flex;
  flex: 0 1 auto;
  flex-wrap: wrap;
  gap: 8px;
  max-width: 100%;
}

.department-theme-actions :deep(.el-button) {
  gap: var(--mrr-space-2);
  margin-left: 0;
}

.import-input {
  display: none;
}

.theme-editor {
  display: grid;
  gap: var(--mrr-space-3);
  margin-top: var(--mrr-space-4);
}

.theme-editor-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 180px), 1fr));
  gap: 12px;
  align-items: center;
}

.theme-editor-row {
  padding: 12px;
  background: var(--mrr-muted);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-lg);
}

.department-name-field { display: grid; gap: var(--mrr-space-2); min-width: 0; }

.color-field {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 8px;
  align-items: center;
}

.color-field__label {
  grid-column: 1 / -1;
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-muted-foreground);
}

.archive-theme-preview {
  --folder-accent: var(--color-primary);
  --folder-strip: var(--color-primary-hover);
  --folder-tint: var(--mrr-tone-primary-soft);

  position: relative;
  display: grid;
  gap: var(--mrr-space-2);
  min-height: 58px;
  padding: var(--mrr-space-3);
  overflow: hidden;
  color: var(--mrr-card-foreground);
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-lg);
}

/* Chrome 86 回退：预览卡的着色描边是「选中哪个文件夹主题」的状态信号。 */
@supports not (color: color-mix(in srgb, #000 50%, #fff)) {
  .archive-theme-preview {
    border-color: var(--folder-accent);
  }
}

.archive-theme-preview i {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 3px;
  background: var(--folder-strip);
  border-radius: 0 0 999px 999px;
}

.archive-theme-preview > span,
.preview-identity > span {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.theme-history-list {
  display: grid;
  gap: var(--mrr-space-3);
}

.theme-history-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: var(--mrr-space-3);
  align-items: center;
  padding: 12px;
  background: var(--surface-alt);
  border: 1px solid var(--divider);
  border-radius: var(--mrr-radius-lg);
}

.theme-history-summary {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.theme-history-summary strong {
  font-size: 14px;
  color: var(--text-primary);
}

.theme-history-summary span {
  font-size: 12px;
  color: var(--text-secondary);
}

.theme-history-swatches {
  display: flex;
  gap: 5px;
}

.theme-history-swatches i {
  width: 16px;
  height: 16px;
  border: 3px solid;
  border-radius: 50%;
}

@media (max-width: 640px) {
  .department-theme-header > div:first-child {
    flex-basis: auto;
  }

  .department-theme-header {
    flex-direction: column;
  }

  .department-theme-actions {
    flex-wrap: wrap;
  }

  .theme-editor-row {
    grid-template-columns: 1fr;
  }

  .theme-history-item {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .theme-history-swatches {
    grid-column: 1 / -1;
  }

  .archive-theme-preview {
    grid-column: auto;
  }
}
</style>
