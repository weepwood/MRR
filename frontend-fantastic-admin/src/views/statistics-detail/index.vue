<script setup lang="ts">
import type { ArchiveDepartmentTheme } from '@/utils/archive-department-theme'
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { getSystemSettings } from '@/api/modules/settings'
import {
  ARCHIVE_DEPARTMENT_THEME_LOCAL_KEY,
  ARCHIVE_DEPARTMENT_THEME_SETTING_KEY,
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
  const cards = rootRef.value?.querySelectorAll<HTMLElement>('.archive-folder-card') ?? []
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
    if (Object.prototype.hasOwnProperty.call(settings, ARCHIVE_DEPARTMENT_THEME_SETTING_KEY)) {
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
</style>
