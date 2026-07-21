export interface ArchiveDepartmentTheme {
  department: string
  folderColor: string
  stripColor: string
}

export interface ArchiveDepartmentThemeHistory {
  savedAt: string
  themes: ArchiveDepartmentTheme[]
}

export const ARCHIVE_DEPARTMENT_THEME_SETTING_KEY = 'archiveDepartmentThemes'
export const ARCHIVE_DEPARTMENT_THEME_LOCAL_KEY = 'MRR-ADMIN:archive-department-themes'
export const ARCHIVE_DEPARTMENT_THEME_HISTORY_LOCAL_KEY = 'MRR-ADMIN:archive-department-theme-history'
export const ARCHIVE_DEPARTMENT_THEME_UPDATED_EVENT = 'mrr:archive-department-themes-updated'
const ARCHIVE_DEPARTMENT_THEME_HISTORY_LIMIT = 10

export const ARCHIVE_DEPARTMENT_THEME_PRESETS = [
  { folderColor: '#2563EB', stripColor: '#1D4ED8' },
  { folderColor: '#0F766E', stripColor: '#0D9488' },
  { folderColor: '#B86B0B', stripColor: '#D97706' },
  { folderColor: '#BE185D', stripColor: '#DB2777' },
  { folderColor: '#7C3AED', stripColor: '#8B5CF6' },
  { folderColor: '#0369A1', stripColor: '#0EA5E9' },
  { folderColor: '#4D7C0F', stripColor: '#65A30D' },
  { folderColor: '#64748B', stripColor: '#475569' },
] as const

const HEX_COLOR_PATTERN = /^#[\dA-F]{6}$/i

export function normalizeDepartmentName(value: unknown) {
  const text = String(value ?? '').trim()
  return text && text.toUpperCase() !== 'NULL' ? text : '未设置科室'
}

export function isArchiveThemeColor(value: unknown): value is string {
  return typeof value === 'string' && HEX_COLOR_PATTERN.test(value.trim())
}

function normalizeColor(value: unknown, fallback: string) {
  return isArchiveThemeColor(value) ? value.trim().toUpperCase() : fallback
}

export function normalizeArchiveDepartmentThemes(value: unknown): ArchiveDepartmentTheme[] {
  let source = value
  if (typeof source === 'string') {
    try {
      source = JSON.parse(source)
    }
    catch {
      return []
    }
  }

  if (!Array.isArray(source)) {
    return []
  }

  const normalized: ArchiveDepartmentTheme[] = []
  const departments = new Set<string>()

  source.forEach((item, index) => {
    if (!item || typeof item !== 'object') {
      return
    }

    const candidate = item as Partial<ArchiveDepartmentTheme>
    const department = String(candidate.department ?? '').trim()
    const departmentKey = department.toLocaleLowerCase('zh-CN')
    if (!department || departments.has(departmentKey)) {
      return
    }

    const preset = ARCHIVE_DEPARTMENT_THEME_PRESETS[index % ARCHIVE_DEPARTMENT_THEME_PRESETS.length]
    normalized.push({
      department,
      folderColor: normalizeColor(candidate.folderColor, preset.folderColor),
      stripColor: normalizeColor(candidate.stripColor, preset.stripColor),
    })
    departments.add(departmentKey)
  })

  return normalized
}

function themeSignature(themes: ArchiveDepartmentTheme[]) {
  return JSON.stringify(themes)
}

function normalizeArchiveDepartmentThemeHistory(value: unknown): ArchiveDepartmentThemeHistory[] {
  let source = value
  if (typeof source === 'string') {
    try {
      source = JSON.parse(source)
    }
    catch {
      return []
    }
  }

  if (!Array.isArray(source)) {
    return []
  }

  const signatures = new Set<string>()
  const history: ArchiveDepartmentThemeHistory[] = []
  for (const item of source) {
    if (!item || typeof item !== 'object') {
      continue
    }
    const candidate = item as Partial<ArchiveDepartmentThemeHistory>
    const savedAt = String(candidate.savedAt ?? '').trim()
    const themes = normalizeArchiveDepartmentThemes(candidate.themes)
    const signature = themeSignature(themes)
    if (!savedAt || Number.isNaN(Date.parse(savedAt)) || signatures.has(signature)) {
      continue
    }
    history.push({ savedAt, themes })
    signatures.add(signature)
    if (history.length === ARCHIVE_DEPARTMENT_THEME_HISTORY_LIMIT) {
      break
    }
  }
  return history
}

function hashText(value: string) {
  let hash = 2166136261
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index)
    hash = Math.imul(hash, 16777619)
  }
  return hash >>> 0
}

export function resolveArchiveDepartmentTheme(department: unknown, themes: ArchiveDepartmentTheme[]) {
  const normalizedDepartment = normalizeDepartmentName(department)
  const departmentKey = normalizedDepartment.toLocaleLowerCase('zh-CN')
  const configured = themes.find(item => normalizeDepartmentName(item.department).toLocaleLowerCase('zh-CN') === departmentKey)
  if (configured) {
    return configured
  }

  const preset = ARCHIVE_DEPARTMENT_THEME_PRESETS[hashText(normalizedDepartment) % ARCHIVE_DEPARTMENT_THEME_PRESETS.length]
  return {
    department: normalizedDepartment,
    folderColor: preset.folderColor,
    stripColor: preset.stripColor,
  }
}

export function archiveDepartmentThemeCssVariables(theme: ArchiveDepartmentTheme) {
  return {
    '--folder-accent': theme.folderColor,
    '--folder-strip': theme.stripColor,
    '--folder-tint': `color-mix(in srgb, ${theme.folderColor} 12%, var(--surface))`,
  }
}

export function loadArchiveDepartmentThemesFromLocal() {
  if (typeof window === 'undefined') {
    return []
  }

  try {
    return normalizeArchiveDepartmentThemes(localStorage.getItem(ARCHIVE_DEPARTMENT_THEME_LOCAL_KEY))
  }
  catch {
    return []
  }
}

export function loadArchiveDepartmentThemeHistoryFromLocal() {
  if (typeof window === 'undefined') {
    return []
  }

  try {
    return normalizeArchiveDepartmentThemeHistory(localStorage.getItem(ARCHIVE_DEPARTMENT_THEME_HISTORY_LOCAL_KEY))
  }
  catch {
    return []
  }
}

export function addArchiveDepartmentThemeHistoryToLocal(themes: ArchiveDepartmentTheme[]) {
  const normalizedThemes = normalizeArchiveDepartmentThemes(themes)
  const signature = themeSignature(normalizedThemes)
  const history = loadArchiveDepartmentThemeHistoryFromLocal()
  const next = [{ savedAt: new Date().toISOString(), themes: normalizedThemes }]
    .concat(history.filter(item => themeSignature(item.themes) !== signature))
    .slice(0, ARCHIVE_DEPARTMENT_THEME_HISTORY_LIMIT)

  if (typeof window === 'undefined') {
    return next
  }

  try {
    localStorage.setItem(ARCHIVE_DEPARTMENT_THEME_HISTORY_LOCAL_KEY, JSON.stringify(next))
  }
  catch {
    // 浏览器禁用本地存储时忽略，当前配色保存不受影响。
  }
  return next
}

export function saveArchiveDepartmentThemesToLocal(themes: ArchiveDepartmentTheme[]) {
  if (typeof window === 'undefined') {
    return
  }

  try {
    const normalizedThemes = normalizeArchiveDepartmentThemes(themes)
    localStorage.setItem(ARCHIVE_DEPARTMENT_THEME_LOCAL_KEY, JSON.stringify(normalizedThemes))
    window.dispatchEvent(new CustomEvent(ARCHIVE_DEPARTMENT_THEME_UPDATED_EVENT, { detail: normalizedThemes }))
  }
  catch {
    // 浏览器禁用本地存储时忽略，服务端配置仍可正常使用。
  }
}
