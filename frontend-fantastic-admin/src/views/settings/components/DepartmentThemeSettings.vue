<script setup lang="ts">
import type { ArchiveDepartmentTheme } from '@/utils/archive-department-theme'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { getSystemSettings, setSetting } from '@/api/modules/settings'
import {
  ARCHIVE_DEPARTMENT_THEME_PRESETS,
  ARCHIVE_DEPARTMENT_THEME_SETTING_KEY,
  archiveDepartmentThemeCssVariables,
  isArchiveThemeColor,
  loadArchiveDepartmentThemesFromLocal,
  normalizeArchiveDepartmentThemes,
  saveArchiveDepartmentThemesToLocal,
} from '@/utils/archive-department-theme'

defineOptions({ name: 'DepartmentThemeSettings' })

interface EditableDepartmentTheme extends ArchiveDepartmentTheme {
  id: number
}

const loading = ref(false)
const saving = ref(false)
const rows = ref<EditableDepartmentTheme[]>([])
let nextId = 1

const colorPresets = ARCHIVE_DEPARTMENT_THEME_PRESETS.flatMap(item => [item.folderColor, item.stripColor])
const hasRows = computed(() => rows.value.length > 0)

function toEditable(theme: ArchiveDepartmentTheme): EditableDepartmentTheme {
  return { ...theme, id: nextId++ }
}

function setRows(themes: ArchiveDepartmentTheme[]) {
  rows.value = themes.map(toEditable)
}

function addDepartmentTheme() {
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
  rows.value = []
  ElMessage.info('已切换为自动配色，点击“保存科室配色”后生效')
}

function previewStyle(row: EditableDepartmentTheme) {
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

async function loadThemes() {
  loading.value = true
  let themes = loadArchiveDepartmentThemesFromLocal()
  try {
    const response = await getSystemSettings()
    const settings = response.data ?? {}
    if (Object.prototype.hasOwnProperty.call(settings, ARCHIVE_DEPARTMENT_THEME_SETTING_KEY)) {
      themes = normalizeArchiveDepartmentThemes(settings[ARCHIVE_DEPARTMENT_THEME_SETTING_KEY])
      saveArchiveDepartmentThemesToLocal(themes)
    }
  }
  catch {
    ElMessage.warning('服务端设置读取失败，已加载本地科室配色')
  }
  finally {
    setRows(themes)
    loading.value = false
  }
}

async function saveThemes() {
  if (!validateRows()) {
    return
  }

  const themes = normalizedRows()
  saving.value = true
  saveArchiveDepartmentThemesToLocal(themes)
  try {
    await setSetting(ARCHIVE_DEPARTMENT_THEME_SETTING_KEY, JSON.stringify(themes))
    setRows(themes)
    ElMessage.success('科室档案袋配色已保存到服务器')
  }
  catch {
    ElMessage.warning('服务端保存失败，已保存到当前浏览器')
  }
  finally {
    saving.value = false
  }
}

onMounted(loadThemes)
</script>

<template>
  <el-card v-loading="loading" shadow="never" class="department-theme-card">
    <template #header>
      <div class="department-theme-header">
        <div>
          <strong>科室档案袋配色</strong>
          <p>同一科室始终使用相同配色，可分别设置档案袋主色与顶部色条颜色。</p>
        </div>
        <div class="department-theme-actions">
          <el-button @click="resetToAutomaticThemes">
            恢复自动配色
          </el-button>
          <el-button type="primary" :loading="saving" @click="saveThemes">
            保存科室配色
          </el-button>
        </div>
      </div>
    </template>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="没有单独配置的科室会根据科室名称稳定生成颜色；分页、排序或刷新后颜色不会改变。"
    />

    <div v-if="hasRows" class="theme-editor">
      <div class="theme-editor-head" aria-hidden="true">
        <span>科室名称</span>
        <span>档案袋主色</span>
        <span>顶部色条</span>
        <span>预览</span>
        <span>操作</span>
      </div>

      <div v-for="row in rows" :key="row.id" class="theme-editor-row">
        <el-input v-model="row.department" clearable placeholder="例如：心内科" maxlength="50" />

        <div class="color-field">
          <el-color-picker v-model="row.folderColor" :predefine="colorPresets" />
          <el-input v-model="row.folderColor" maxlength="7" />
        </div>

        <div class="color-field">
          <el-color-picker v-model="row.stripColor" :predefine="colorPresets" />
          <el-input v-model="row.stripColor" maxlength="7" />
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

    <el-empty v-else :image-size="72" description="当前使用按科室名称自动生成的稳定配色" />

    <div class="editor-footer">
      <el-button plain @click="addDepartmentTheme">
        添加科室配色
      </el-button>
    </div>
  </el-card>
</template>

<style scoped>
.department-theme-card {
  border-radius: 12px;
}

.department-theme-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.department-theme-header strong {
  font-size: 16px;
  color: var(--text-primary);
}

.department-theme-header p {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.department-theme-actions {
  display: flex;
  flex: none;
  gap: 8px;
}

.theme-editor {
  display: grid;
  gap: 10px;
  margin-top: 18px;
}

.theme-editor-head,
.theme-editor-row {
  display: grid;
  grid-template-columns: minmax(160px, 1.2fr) minmax(170px, 1fr) minmax(170px, 1fr) minmax(150px, 0.9fr) 56px;
  gap: 12px;
  align-items: center;
}

.theme-editor-head {
  padding: 0 10px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
}

.theme-editor-row {
  padding: 12px;
  background: var(--surface-alt);
  border: 1px solid var(--divider);
  border-radius: 10px;
}

.color-field {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 8px;
  align-items: center;
}

.archive-theme-preview {
  --folder-accent: #2563eb;
  --folder-strip: #1d4ed8;
  --folder-tint: #eef4ff;

  position: relative;
  display: grid;
  gap: 7px;
  min-height: 58px;
  padding: 13px 14px 10px;
  overflow: hidden;
  color: var(--folder-accent);
  background: linear-gradient(155deg, var(--folder-tint), var(--surface) 68%);
  border: 1px solid color-mix(in srgb, var(--folder-accent) 42%, var(--divider));
  border-radius: 10px;
}

.archive-theme-preview i {
  position: absolute;
  top: 0;
  right: 12px;
  left: 12px;
  height: 4px;
  background: var(--folder-strip);
  border-radius: 0 0 999px 999px;
}

.archive-theme-preview span {
  overflow: hidden;
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-footer {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}

@media (width <= 980px) {
  .theme-editor-head {
    display: none;
  }

  .theme-editor-row {
    grid-template-columns: 1fr 1fr;
  }

  .archive-theme-preview {
    grid-column: 1 / -1;
  }
}

@media (width <= 640px) {
  .department-theme-header {
    flex-direction: column;
  }

  .department-theme-actions {
    flex-wrap: wrap;
  }

  .theme-editor-row {
    grid-template-columns: 1fr;
  }

  .archive-theme-preview {
    grid-column: auto;
  }
}
</style>
