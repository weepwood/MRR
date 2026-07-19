<script setup lang="ts">
import type { ArchiveDepartmentTheme } from '@/utils/archive-department-theme'
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { getSystemSettings } from '@/api/modules/settings'
import {
  ARCHIVE_DEPARTMENT_THEME_LOCAL_KEY,
  ARCHIVE_DEPARTMENT_THEME_SETTING_KEY,
  ARCHIVE_DEPARTMENT_THEME_UPDATED_EVENT,
  archiveDepartmentThemeCssVariables,
  loadArchiveDepartmentThemesFromLocal,
  normalizeArchiveDepartmentThemes,
  resolveArchiveDepartmentTheme,
  saveArchiveDepartmentThemesToLocal,
} from '@/utils/archive-department-theme'
import ArchiveDetailContent from './ArchiveDetailContent.vue'

defineOptions({ name: 'StatisticsDetailThemePage' })

const rootRef = ref<HTMLElement | null>(null)
let departmentThemes: ArchiveDepartmentTheme[] = loadArchiveDepartmentThemesFromLocal()
let observer: MutationObserver | null = null
let animationFrame = 0

function findDepartment(card: HTMLElement) {
  const blocks = Array.from(card.querySelectorAll<HTMLElement>('.folder-code-block'))
  const departmentBlock = blocks.find((block) => {
    return block.querySelector<HTMLElement>('.folder-code-label')?.textContent?.trim() === '住院科室'
  })
  return departmentBlock?.querySelector<HTMLElement>('.folder-code-value')?.textContent?.trim()
}

function applyDepartmentThemes() {
  animationFrame = 0
  const cards = Array.from(rootRef.value?.querySelectorAll<HTMLElement>('.archive-folder-card') ?? [])
  cards.forEach((card) => {
    const theme = resolveArchiveDepartmentTheme(findDepartment(card), departmentThemes)
    const variables = archiveDepartmentThemeCssVariables(theme)
    Object.entries(variables).forEach(([name, value]) => card.style.setProperty(name, value))
    card.dataset.department = theme.department
  })
}

function scheduleDepartmentThemeApply() {
  if (animationFrame) {
    return
  }
  animationFrame = window.requestAnimationFrame(applyDepartmentThemes)
}

async function loadDepartmentThemes() {
  let nextThemes = loadArchiveDepartmentThemesFromLocal()
  try {
    const response = await getSystemSettings()
    const settings = response.data ?? {}
    if (Object.hasOwn(settings, ARCHIVE_DEPARTMENT_THEME_SETTING_KEY)) {
      nextThemes = normalizeArchiveDepartmentThemes(settings[ARCHIVE_DEPARTMENT_THEME_SETTING_KEY])
      saveArchiveDepartmentThemesToLocal(nextThemes)
    }
  }
  catch {
    // 后端不可用时继续使用本地缓存，不影响档案列表加载。
  }

  departmentThemes = nextThemes
  await nextTick()
  scheduleDepartmentThemeApply()
}

function handleThemeUpdate(event: Event) {
  const detail = event instanceof CustomEvent ? event.detail : null
  departmentThemes = detail ? normalizeArchiveDepartmentThemes(detail) : loadArchiveDepartmentThemesFromLocal()
  scheduleDepartmentThemeApply()
}

function handleStorage(event: StorageEvent) {
  if (event.key !== ARCHIVE_DEPARTMENT_THEME_LOCAL_KEY) {
    return
  }
  departmentThemes = normalizeArchiveDepartmentThemes(event.newValue)
  scheduleDepartmentThemeApply()
}

onMounted(() => {
  observer = new MutationObserver(scheduleDepartmentThemeApply)
  if (rootRef.value) {
    observer.observe(rootRef.value, { childList: true, subtree: true, characterData: true })
  }
  window.addEventListener(ARCHIVE_DEPARTMENT_THEME_UPDATED_EVENT, handleThemeUpdate)
  window.addEventListener('storage', handleStorage)
  scheduleDepartmentThemeApply()
  loadDepartmentThemes()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  if (animationFrame) {
    window.cancelAnimationFrame(animationFrame)
  }
  window.removeEventListener(ARCHIVE_DEPARTMENT_THEME_UPDATED_EVENT, handleThemeUpdate)
  window.removeEventListener('storage', handleStorage)
})
</script>

<template>
  <div ref="rootRef" class="department-themed-archive">
    <ArchiveDetailContent />
  </div>
</template>

<style scoped>
.department-themed-archive :deep(.archive-folder-card .folder-card-body::before) {
  background: var(--folder-strip, var(--folder-accent)) !important;
}

.department-themed-archive :deep(.folder-card-body) {
  gap: 14px;
}

.department-themed-archive :deep(.folder-code-grid) {
  gap: 10px;
}

.department-themed-archive :deep(.folder-code-block) {
  display: flex;
  flex-direction: column;
  gap: 7px;
  justify-content: center;
  min-height: 78px;
  padding: 12px 14px;
  transition: background-color 180ms ease, border-color 180ms ease, box-shadow 180ms ease;
}

.department-themed-archive :deep(.folder-code-block-full) {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  column-gap: 18px;
  align-items: center;
  min-height: 86px;
  padding: 15px 16px;
}

.department-themed-archive :deep(.folder-code-label) {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  width: fit-content;
  margin-bottom: 0;
  font-size: 11px;
  line-height: 1;
  letter-spacing: 0.04em;
}

.department-themed-archive :deep(.folder-code-label::before) {
  flex: none;
  width: 15px;
  height: 15px;
  content: "";
  background: var(--folder-accent);
  opacity: 0.78;
  mask-repeat: no-repeat;
  mask-position: center;
  mask-size: contain;
}

.department-themed-archive :deep(.folder-code-block-full .folder-code-label::before) {
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath fill='black' d='M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm0 2c-4.42 0-8 2.24-8 5v1h16v-1c0-2.76-3.58-5-8-5Z'/%3E%3C/svg%3E");
}

.department-themed-archive :deep(.folder-code-grid .folder-code-block:nth-child(1) .folder-code-label::before) {
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath fill='black' d='M4 21V3h10v6h6v12h-2v-2h-2v2h-2V5H6v16H4Zm4-12h2V7H8v2Zm0 4h2v-2H8v2Zm0 4h2v-2H8v2Zm8-4h2v-2h-2v2Zm0 4h2v-2h-2v2Z'/%3E%3C/svg%3E");
}

.department-themed-archive :deep(.folder-code-grid .folder-code-block:nth-child(2) .folder-code-label::before) {
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath fill='black' d='M7 2h2v2h6V2h2v2h3a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h3V2Zm13 8H4v10h16V10ZM4 8h16V6H4v2Zm3 4h2v2H7v-2Zm4 0h2v2h-2v-2Zm4 0h2v2h-2v-2Z'/%3E%3C/svg%3E");
}

.department-themed-archive :deep(.folder-code-grid .folder-code-block:nth-child(3) .folder-code-label::before) {
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath fill='black' d='M6 2h9l5 5v15H6V2Zm2 2v16h10V8h-4V4H8Zm2 7h6v2h-6v-2Zm0 4h6v2h-6v-2Z'/%3E%3C/svg%3E");
}

.department-themed-archive :deep(.folder-code-grid .folder-code-block:nth-child(4) .folder-code-label::before) {
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath fill='black' d='M3 4h18v5H3V4Zm2 7h14v9H5v-9Zm5 2v2h4v-2h-4Z'/%3E%3C/svg%3E");
}

.department-themed-archive :deep(.folder-code-value) {
  line-height: 1.3;
}

.department-themed-archive :deep(.folder-code-block-full .folder-code-value) {
  line-height: 1.15;
  text-align: right;
}

.department-themed-archive :deep(.folder-code-grid .folder-code-block:nth-child(-n+2) .folder-code-value) {
  font-size: 14px;
  line-height: 1.35;
}

.department-themed-archive :deep(.folder-code-copyable:hover) {
  box-shadow: 0 6px 16px color-mix(in srgb, var(--folder-accent) 9%, transparent);
}

@media (width <= 480px) {
  .department-themed-archive :deep(.folder-code-block-full) {
    grid-template-columns: minmax(0, 1fr);
    gap: 9px;
  }

  .department-themed-archive :deep(.folder-code-block-full .folder-code-value) {
    text-align: left;
  }
}
</style>
