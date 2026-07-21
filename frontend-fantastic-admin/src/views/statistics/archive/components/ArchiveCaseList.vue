<script setup lang="ts">
import type { IdCardArchiveCase } from '@/api/modules/search'
import type { ArchiveDepartmentTheme } from '@/utils/archive-department-theme'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getSystemSettings } from '@/api/modules/settings'
import {
  ARCHIVE_DEPARTMENT_THEME_SETTING_KEY,
  ARCHIVE_DEPARTMENT_THEME_UPDATED_EVENT,
  archiveDepartmentThemeCssVariables,
  loadArchiveDepartmentThemesFromLocal,
  normalizeArchiveDepartmentThemes,
  resolveArchiveDepartmentTheme,
  saveArchiveDepartmentThemesToLocal,
} from '@/utils/archive-department-theme'
import { padCode } from '../constants'

const props = defineProps<{
  cases: IdCardArchiveCase[]
  activeBah?: string
  activeSjh?: string
  maskedIdCard?: string
  loading?: boolean
  departmentColorsEnabled?: boolean
}>()

const emit = defineEmits<{
  select: [archiveCase: IdCardArchiveCase]
}>()

const caseCountLabel = computed(() => `${props.cases.length} 份病案`)
const departmentThemes = ref<ArchiveDepartmentTheme[]>(loadArchiveDepartmentThemesFromLocal())

function normalizeDate(value: unknown) {
  const text = String(value ?? '').trim()
  return text ? text.replace(/\//g, '-').split(/[ T]/)[0] : '日期未知'
}

function inpatientLocation(item: IdCardArchiveCase) {
  return [item.department, item.bingqu, item.chuangwei].filter(Boolean).join(' · ') || '住院位置未知'
}

function isActive(item: IdCardArchiveCase) {
  return padCode(item.bah || '') === padCode(props.activeBah || '')
    && padCode(item.sjh || '') === padCode(props.activeSjh || '')
}

function departmentStyle(department: string | undefined) {
  if (!props.departmentColorsEnabled) {
    return undefined
  }
  return archiveDepartmentThemeCssVariables(resolveArchiveDepartmentTheme(department, departmentThemes.value)) as Record<string, string>
}

function handleDepartmentThemeUpdate(event: Event) {
  const detail = event instanceof CustomEvent ? event.detail : null
  departmentThemes.value = detail ? normalizeArchiveDepartmentThemes(detail) : loadArchiveDepartmentThemesFromLocal()
}

async function loadDepartmentThemes() {
  try {
    const response = await getSystemSettings()
    const settings = response.data ?? {}
    if (Object.hasOwn(settings, ARCHIVE_DEPARTMENT_THEME_SETTING_KEY)) {
      departmentThemes.value = normalizeArchiveDepartmentThemes(settings[ARCHIVE_DEPARTMENT_THEME_SETTING_KEY])
      saveArchiveDepartmentThemesToLocal(departmentThemes.value)
    }
  }
  catch {
    // 服务端设置不可用时继续使用本地配色。
  }
}

onMounted(() => {
  window.addEventListener(ARCHIVE_DEPARTMENT_THEME_UPDATED_EVENT, handleDepartmentThemeUpdate)
  void loadDepartmentThemes()
})
onBeforeUnmount(() => window.removeEventListener(ARCHIVE_DEPARTMENT_THEME_UPDATED_EVENT, handleDepartmentThemeUpdate))
</script>

<template>
  <section v-if="loading || cases.length" class="archive-case-card" aria-label="身份证关联病案">
    <header class="case-header">
      <small v-if="maskedIdCard">{{ maskedIdCard }}</small>
      <el-tag size="small" effect="plain">
        {{ caseCountLabel }}
      </el-tag>
    </header>

    <el-skeleton v-if="loading && !cases.length" :rows="3" animated />
    <div v-else class="case-list">
      <button
        v-for="item in cases"
        :key="`${item.patientRecordId || 'patient'}:${item.bah || ''}:${item.sjh || ''}`"
        type="button"
        class="case-item"
        :class="{ active: isActive(item) }"
        :style="departmentStyle(item.department)"
        :aria-current="isActive(item) ? 'true' : undefined"
        @click="emit('select', item)"
      >
        <span class="case-main">
          <strong>{{ item.name || '未知患者' }}</strong>
          <small>{{ inpatientLocation(item) }}</small>
          <small>入院：{{ normalizeDate(item.ruyuan || item.admissionTime) }}</small>
        </span>
        <span class="case-codes">
          <span><small>病案号</small>{{ padCode(item.bah || '') || '--' }}</span>
          <span><small>上架号</small>{{ padCode(item.sjh || '') || '--' }}</span>
        </span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.archive-case-card {
  display: grid;
  gap: 10px;
  padding: 12px;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 12px;
}

.case-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.case-main,
.case-codes span {
  display: flex;
  flex-direction: column;
}

.case-header small,
.case-main small,
.case-codes small {
  color: var(--text-tertiary);
}

.case-header small {
  margin-top: 2px;
  font-size: 11px;
}

.case-list {
  display: grid;
  gap: 8px;
  max-height: 260px;
  overflow: auto;
}

.case-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  width: 100%;
  padding: 10px;
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
  background: var(--folder-tint, var(--surface-muted));
  border: 1px solid transparent;
  border-radius: 10px;
  transition: border-color 120ms ease, background-color 120ms ease;
}

.case-item:hover {
  border-color: var(--mrr-navigation-active-border);
}

.case-item.active {
  color: var(--color-primary);
  background: var(--mrr-navigation-active);
  border-color: var(--mrr-navigation-active-border);
}

.case-main {
  min-width: 0;
  gap: 3px;
}

.case-main strong,
.case-main small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.case-main small,
.case-codes {
  font-size: 11px;
}

.case-codes {
  display: grid;
  grid-template-columns: repeat(2, auto);
  gap: 10px;
  font-variant-numeric: tabular-nums;
}

.case-codes span {
  gap: 2px;
}

@media (width <= 480px) {
  .case-item {
    grid-template-columns: 1fr;
  }

  .case-codes {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
