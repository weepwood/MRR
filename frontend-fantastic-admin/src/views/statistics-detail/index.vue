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
.department-themed-archive {
  --record-card-radius: 18px;
  --record-card-shadow: 0 12px 34px rgb(15 23 42 / 8%);
  --record-card-shadow-hover: 0 22px 52px rgb(15 23 42 / 15%);
}

.department-themed-archive :deep(.archive-grid) {
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 22px;
}

.department-themed-archive :deep(.archive-folder-card) {
  min-height: 478px;
  padding: 0;
}

.department-themed-archive :deep(.folder-layer) {
  display: none;
}

.department-themed-archive :deep(.folder-card-body) {
  gap: 0;
  min-height: 478px;
  padding: 0 26px 22px;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid color-mix(in srgb, var(--folder-accent) 18%, var(--divider));
  border-radius: var(--record-card-radius);
  box-shadow: var(--record-card-shadow);
  transform: translateY(0);
  transition: transform 220ms ease, border-color 220ms ease, box-shadow 220ms ease;
}

.department-themed-archive :deep(.folder-card-body::before) {
  position: absolute;
  inset: 0 0 auto;
  z-index: 0;
  height: 108px;
  content: "";
  background:
    linear-gradient(130deg, rgb(255 255 255 / 18%) 0%, transparent 42%),
    linear-gradient(135deg, var(--folder-accent), var(--folder-strip, var(--folder-accent)));
  border-radius: var(--record-card-radius) var(--record-card-radius) 0 0;
  opacity: 1;
  transform: none;
}

.department-themed-archive :deep(.folder-card-body::after) {
  position: absolute;
  top: -64px;
  right: -46px;
  z-index: 0;
  width: 250px;
  height: 190px;
  pointer-events: none;
  content: "";
  background: linear-gradient(138deg, transparent 26%, rgb(255 255 255 / 18%) 48%, transparent 68%);
  opacity: 0.72;
  transform: rotate(-8deg);
}

.department-themed-archive :deep(.archive-folder-card:hover .folder-card-body),
.department-themed-archive :deep(.archive-folder-card:focus-visible .folder-card-body) {
  background: var(--surface);
  border-color: color-mix(in srgb, var(--folder-accent) 58%, var(--divider));
  box-shadow: var(--record-card-shadow-hover), 0 0 0 3px color-mix(in srgb, var(--folder-accent) 9%, transparent);
  transform: translateY(-5px);
}

.department-themed-archive :deep(.archive-folder-card.is-selected .folder-card-body) {
  border-color: var(--folder-accent);
  box-shadow: 0 18px 44px rgb(15 23 42 / 13%), 0 0 0 3px color-mix(in srgb, var(--folder-accent) 14%, transparent);
}

.department-themed-archive :deep(.folder-top) {
  position: relative;
  z-index: 2;
  min-height: 108px;
  padding: 0 4px;
}

.department-themed-archive :deep(.folder-identity) {
  gap: 10px;
}

.department-themed-archive :deep(.folder-index) {
  font-size: 21px;
  font-weight: 800;
  line-height: 1;
  color: #fff;
  letter-spacing: 0.08em;
  text-shadow: 0 1px 2px rgb(0 0 0 / 12%);
}

.department-themed-archive :deep(.folder-selected-label) {
  padding: 4px 8px;
  color: #fff;
  background: rgb(255 255 255 / 16%);
  border: 1px solid rgb(255 255 255 / 24%);
  backdrop-filter: blur(8px);
}

.department-themed-archive :deep(.folder-selected-label i) {
  background: #fff;
  box-shadow: 0 0 0 3px rgb(255 255 255 / 18%);
}

.department-themed-archive :deep(.folder-top .el-tag) {
  height: 34px;
  padding: 0 17px;
  font-size: 14px;
  font-weight: 750;
  color: var(--folder-strip, var(--folder-accent));
  background: rgb(255 255 255 / 92%);
  border-color: rgb(255 255 255 / 72%);
  border-radius: 999px;
  box-shadow: 0 5px 16px rgb(15 23 42 / 10%);
}

.department-themed-archive :deep(.folder-code-block) {
  position: relative;
  min-width: 0;
  padding: 15px 14px 14px 68px;
  background: color-mix(in srgb, var(--folder-accent) 3.5%, var(--surface));
  border: 1px solid color-mix(in srgb, var(--folder-accent) 14%, var(--divider));
  border-radius: 12px;
}

.department-themed-archive :deep(.folder-code-block::before),
.department-themed-archive :deep(.folder-page-count::before) {
  position: absolute;
  top: 50%;
  left: 14px;
  width: 40px;
  height: 40px;
  pointer-events: none;
  content: "";
  background: color-mix(in srgb, var(--folder-accent) 9%, var(--surface));
  border: 1px solid color-mix(in srgb, var(--folder-accent) 10%, transparent);
  border-radius: 50%;
  transform: translateY(-50%);
}

.department-themed-archive :deep(.folder-code-block::after),
.department-themed-archive :deep(.folder-page-count::after) {
  position: absolute;
  top: 50%;
  left: 23px;
  width: 22px;
  height: 22px;
  pointer-events: none;
  content: "";
  background: var(--folder-accent);
  transform: translateY(-50%);
  -webkit-mask-position: center;
  mask-position: center;
  -webkit-mask-repeat: no-repeat;
  mask-repeat: no-repeat;
  -webkit-mask-size: contain;
  mask-size: contain;
}

.department-themed-archive :deep(.folder-code-block-full) {
  display: grid;
  gap: 5px;
  min-height: 108px;
  padding: 23px 12px 22px 74px;
  align-content: center;
  justify-content: stretch;
  background: transparent;
  border: 0;
  border-bottom: 1px solid color-mix(in srgb, var(--folder-accent) 13%, var(--divider));
  border-radius: 0;
}

.department-themed-archive :deep(.folder-code-block-full::before) {
  left: 8px;
  width: 48px;
  height: 48px;
}

.department-themed-archive :deep(.folder-code-block-full::after) {
  left: 19px;
  width: 26px;
  height: 26px;
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath fill='black' d='M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm0 2c-4.42 0-8 2.24-8 5v1h16v-1c0-2.76-3.58-5-8-5Z'/%3E%3C/svg%3E");
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath fill='black' d='M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm0 2c-4.42 0-8 2.24-8 5v1h16v-1c0-2.76-3.58-5-8-5Z'/%3E%3C/svg%3E");
}

.department-themed-archive :deep(.folder-code-label) {
  display: block;
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 650;
  line-height: 1.2;
  color: var(--text-secondary);
  letter-spacing: 0.03em;
}

.department-themed-archive :deep(.folder-code-value) {
  display: block;
  font-size: 18px;
  font-weight: 780;
  font-variant-numeric: tabular-nums;
  line-height: 1.25;
  color: var(--text-primary);
}

.department-themed-archive :deep(.folder-code-block-full .folder-code-label) {
  margin: 0;
}

.department-themed-archive :deep(.folder-code-block-full .folder-code-value) {
  flex: none;
  min-width: 0;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  font-size: clamp(25px, 2.5vw, 31px);
  font-weight: 800;
  color: var(--text-primary);
  white-space: nowrap;
}

.department-themed-archive :deep(.folder-code-grid) {
  position: relative;
  z-index: 1;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.department-themed-archive :deep(.folder-code-grid .folder-code-block) {
  min-height: 88px;
}

.department-themed-archive :deep(.folder-code-grid .folder-code-block:nth-child(-n+2) .folder-code-value) {
  font-size: 17px;
  font-weight: 760;
}

.department-themed-archive :deep(.folder-code-grid .folder-code-block:nth-child(1)::after) {
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath fill='black' d='M4 21V3h10v6h6v12h-2v-2h-2v2h-2V5H6v16H4Zm4-12h2V7H8v2Zm0 4h2v-2H8v2Zm0 4h2v-2H8v2Zm8-4h2v-2h-2v2Zm0 4h2v-2h-2v2Z'/%3E%3C/svg%3E");
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath fill='black' d='M4 21V3h10v6h6v12h-2v-2h-2v2h-2V5H6v16H4Zm4-12h2V7H8v2Zm0 4h2v-2H8v2Zm0 4h2v-2H8v2Zm8-4h2v-2h-2v2Zm0 4h2v-2h-2v2Z'/%3E%3C/svg%3E");
}

.department-themed-archive :deep(.folder-code-grid .folder-code-block:nth-child(2)::after) {
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath fill='black' d='M7 2h2v2h6V2h2v2h3a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h3V2Zm13 8H4v10h16V10ZM4 8h16V6H4v2Zm3 4h2v2H7v-2Zm4 0h2v2h-2v-2Zm4 0h2v2h-2v-2Zm-8 4h2v2H7v-2Zm4 0h2v2h-2v-2Z'/%3E%3C/svg%3E");
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath fill='black' d='M7 2h2v2h6V2h2v2h3a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h3V2Zm13 8H4v10h16V10ZM4 8h16V6H4v2Zm3 4h2v2H7v-2Zm4 0h2v2h-2v-2Zm4 0h2v2h-2v-2Zm-8 4h2v2H7v-2Zm4 0h2v2h-2v-2Z'/%3E%3C/svg%3E");
}

.department-themed-archive :deep(.folder-code-grid .folder-code-block:nth-child(3)::after),
.department-themed-archive :deep(.folder-page-count::after) {
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath fill='black' d='M6 2h8l4 4v16H6V2Zm2 2v16h8V8h-4V4H8Zm6 .83V6h1.17L14 4.83ZM10 11h4v2h-4v-2Zm0 4h4v2h-4v-2Z'/%3E%3C/svg%3E");
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath fill='black' d='M6 2h8l4 4v16H6V2Zm2 2v16h8V8h-4V4H8Zm6 .83V6h1.17L14 4.83ZM10 11h4v2h-4v-2Zm0 4h4v2h-4v-2Z'/%3E%3C/svg%3E");
}

.department-themed-archive :deep(.folder-code-grid .folder-code-block:nth-child(4)::after) {
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath fill='black' d='M3 4h8.59L21 13.41 13.41 21 4 11.59V4Zm2 2v4.76l8.41 8.41 4.76-4.76L10.76 6H5Zm3 1.5A1.5 1.5 0 1 1 8 10a1.5 1.5 0 0 1 0-3Z'/%3E%3C/svg%3E");
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath fill='black' d='M3 4h8.59L21 13.41 13.41 21 4 11.59V4Zm2 2v4.76l8.41 8.41 4.76-4.76L10.76 6H5Zm3 1.5A1.5 1.5 0 1 1 8 10a1.5 1.5 0 0 1 0-3Z'/%3E%3C/svg%3E");
}

.department-themed-archive :deep(.folder-code-copyable) {
  cursor: copy;
  transition: background-color 180ms ease, border-color 180ms ease, transform 180ms ease;
}

.department-themed-archive :deep(.folder-code-copyable:hover) {
  background: color-mix(in srgb, var(--folder-accent) 8%, var(--surface));
  border-color: color-mix(in srgb, var(--folder-accent) 30%, var(--divider));
  transform: translateY(-1px);
}

.department-themed-archive :deep(.folder-footer) {
  position: relative;
  z-index: 1;
  min-height: 72px;
  margin-top: 18px;
  padding: 18px 0 0;
  border-top: 1px solid color-mix(in srgb, var(--folder-accent) 13%, var(--divider));
}

.department-themed-archive :deep(.folder-page-count) {
  position: relative;
  min-height: 46px;
  padding-left: 60px;
}

.department-themed-archive :deep(.folder-page-count::before) {
  left: 2px;
  width: 44px;
  height: 44px;
}

.department-themed-archive :deep(.folder-page-count::after) {
  left: 13px;
  width: 22px;
  height: 22px;
}

.department-themed-archive :deep(.folder-page-count strong) {
  font-size: 34px;
  font-weight: 800;
  color: var(--folder-accent);
  letter-spacing: -0.03em;
}

.department-themed-archive :deep(.folder-page-count span) {
  font-size: 13px;
  font-weight: 650;
  color: var(--text-secondary);
}

.department-themed-archive :deep(.folder-action.el-button) {
  height: 46px;
  padding: 0 18px;
  font-size: 15px;
  font-weight: 750;
  color: var(--folder-accent);
  background: color-mix(in srgb, var(--folder-accent) 8%, var(--surface));
  border: 1px solid color-mix(in srgb, var(--folder-accent) 5%, transparent);
  border-radius: 11px;
}

.department-themed-archive :deep(.folder-action.el-button:hover),
.department-themed-archive :deep(.folder-action.el-button:focus-visible) {
  color: var(--folder-strip, var(--folder-accent));
  background: color-mix(in srgb, var(--folder-accent) 13%, var(--surface));
}

.department-themed-archive :deep(.archive-folder-card:hover .folder-action span),
.department-themed-archive :deep(.archive-folder-card:focus-visible .folder-action span) {
  transform: translateX(4px);
}

@media (prefers-reduced-motion: reduce) {
  .department-themed-archive :deep(.folder-card-body),
  .department-themed-archive :deep(.folder-code-copyable),
  .department-themed-archive :deep(.folder-action span) {
    transition: none;
  }

  .department-themed-archive :deep(.archive-folder-card:hover .folder-card-body),
  .department-themed-archive :deep(.archive-folder-card:focus-visible .folder-card-body),
  .department-themed-archive :deep(.folder-code-copyable:hover) {
    transform: none;
  }
}

@media (width <= 720px) {
  .department-themed-archive :deep(.archive-grid) {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (width <= 480px) {
  .department-themed-archive :deep(.archive-folder-card),
  .department-themed-archive :deep(.folder-card-body) {
    min-height: auto;
  }

  .department-themed-archive :deep(.folder-card-body) {
    padding-right: 18px;
    padding-left: 18px;
  }

  .department-themed-archive :deep(.folder-index) {
    font-size: 17px;
  }

  .department-themed-archive :deep(.folder-code-grid) {
    grid-template-columns: 1fr;
  }

  .department-themed-archive :deep(.folder-footer) {
    gap: 12px;
  }

  .department-themed-archive :deep(.folder-action.el-button) {
    padding: 0 14px;
  }
}
</style>
