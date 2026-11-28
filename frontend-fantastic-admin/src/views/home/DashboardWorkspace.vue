<script setup lang="ts">
import type { DashboardSource } from './dashboard-data'
import type { DashboardAppearanceMode } from './dashboard-palette'
import type {
  DashboardWidgetColor,
  DashboardWidgetDefinition,
  DashboardWidgetPreference,
  DashboardWidgetSize,
} from './dashboard-widgets'
import { ElMessage } from 'element-plus'
import { computed, defineAsyncComponent, nextTick, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import { useUserStore } from '@/store/modules/user'
import { actionDialog } from '@/ui/components/ActionDialog'
import { hasPermission } from '@/utils/session'
import { sourceMetrics } from './dashboard-data'
import { appearanceModes, isWidgetColor, normalizeAppearance, paletteStyle, widgetAppearanceStyle, widgetPalette } from './dashboard-palette'
import {
  createDefaultWidgetPreferences,
  mergeWidgetPreferences,
  moveWidget,
  setWidgetPinned,
  sortWidgetPreferences,
  updateWidgetPreference,
} from './dashboard-widgets'
import DashboardDataContent from './DashboardDataContent.vue'
import HomeArchiveHistory from './HomeArchiveHistory.vue'
import HomeArchiveLookup from './HomeArchiveLookup.vue'
import { useDashboardData } from './useDashboardData'
import { useWidgetLongPress } from './useWidgetLongPress'

defineOptions({ name: 'DashboardWorkspace' })

const pagesScenarioEnabled = import.meta.env.MODE === 'pages' && String(import.meta.env.VITE_BUILD_MOCK) === 'true'
const DemoScenarioControl = pagesScenarioEnabled ? defineAsyncComponent(() => import('./DemoScenarioControl.vue')) : undefined

type DashboardWidgetView = DashboardWidgetDefinition & DashboardWidgetPreference

const router = useRouter()
const userStore = useUserStore()

const sizeOptions: Array<{ label: string, value: DashboardWidgetSize }> = [
  { label: '标准', value: 'small' },
  { label: '宽', value: 'wide' },
  { label: '大', value: 'large' },
]

const colorOptions = widgetPalette

const baseWidgetDefinitions: DashboardWidgetDefinition[] = [
  {
    id: 'archive',
    title: '影像档案袋',
    description: '查询并浏览病案影像、缩略图和患者信息。',
    icon: 'i-lucide:folder-open',
    path: '/archive/embed',
    permission: 'record:read',
    defaultSize: 'wide',
    defaultPinned: true,
  },
  {
    id: 'records',
    title: '记录管理',
    description: '维护病案扫描记录及其基础数据。',
    icon: 'i-lucide:files',
    path: '/records',
    permission: 'record:read',
    defaultSize: 'small',
  },
  {
    id: 'statistics',
    availableMetrics: sourceMetrics.statistics.map(item => item.key),
    title: '病案扫描统计',
    description: '按年份、科室和扫描状态查看统计结果。',
    icon: 'i-lucide:chart-column',
    path: '/statistics',
    permission: 'statistics:read',
    defaultSize: 'wide',
  },
  {
    id: 'monitoring',
    availableMetrics: sourceMetrics.health.map(item => item.key),
    title: '系统监控',
    description: '查看服务、数据库、内存和运行指标。',
    icon: 'i-lucide:activity',
    path: '/monitoring',
    permission: 'system:read',
    defaultSize: 'small',
  },
  {
    id: 'statistics-detail',
    availableMetrics: sourceMetrics.statistics.map(item => item.key),
    title: '统计明细',
    description: '查看病案扫描与档案记录的详细数据。',
    icon: 'i-lucide:table-2',
    path: '/statistics-detail',
    permission: 'statistics:read',
    defaultSize: 'small',
  },
  {
    id: 'audit-images',
    title: '病案图片访问审计',
    description: '查询病案影像查看、下载和访问记录。',
    icon: 'i-lucide:eye',
    path: '/audit-images',
    permission: 'log:read',
    defaultSize: 'small',
  },
  {
    id: 'patients',
    title: '患者管理',
    description: '查看患者与住院病案的关联信息。',
    icon: 'i-lucide:users-round',
    path: '/patients',
    permission: 'record:read',
    defaultSize: 'small',
    defaultVisible: false,
  },
  {
    id: 'archive-boxes',
    title: '档案装箱',
    description: '管理病案装箱、箱号和档案状态。',
    icon: 'i-lucide:archive',
    path: '/archive-boxes',
    permission: 'record:read',
    defaultSize: 'small',
    defaultVisible: false,
  },
  {
    id: 'oss-migration',
    title: 'OSS 迁移管理',
    description: '查看与管理病案影像迁移任务。',
    icon: 'i-lucide:cloud-upload',
    path: '/oss-migration',
    permission: 'record:read',
    defaultSize: 'small',
    defaultVisible: false,
  },
  {
    id: 'data-relations',
    title: '数据关系工作台',
    description: '检查数据库表之间的关联和数据完整性。',
    icon: 'i-lucide:workflow',
    path: '/data-relations',
    permission: 'system:read',
    defaultSize: 'wide',
    defaultVisible: false,
  },
  {
    id: 'users',
    title: '用户管理',
    description: '创建用户、审核账号和重置密码。',
    icon: 'i-lucide:user-round',
    path: '/users',
    permission: 'user:manage',
    defaultSize: 'small',
    defaultVisible: false,
  },
  {
    id: 'permissions',
    title: '权限管理',
    description: '维护角色权限和功能访问范围。',
    icon: 'i-lucide:shield-check',
    path: '/permissions',
    permission: 'role:read',
    defaultSize: 'small',
    defaultVisible: false,
  },
  {
    id: 'settings',
    title: '系统设置',
    description: '配置系统信息、档案浏览、安全和界面外观。',
    icon: 'i-lucide:sliders-horizontal',
    path: '/settings',
    permission: 'system:read',
    defaultSize: 'small',
    defaultVisible: false,
  },
  {
    id: 'logs',
    title: '日志管理',
    description: '检索系统日志、操作记录和异常信息。',
    icon: 'i-lucide:file-search',
    path: '/logs',
    permission: 'log:read',
    defaultSize: 'small',
    defaultVisible: false,
  },
  {
    id: 'status',
    title: '服务状态',
    description: '快速确认系统服务是否正常运行。',
    icon: 'i-lucide:circle-check',
    path: '/status',
    permission: 'system:read',
    defaultSize: 'small',
    defaultVisible: false,
  },
  {
    id: 'response-analysis',
    title: '接口响应分析',
    description: '分析慢接口、响应时间和近期性能趋势。',
    icon: 'i-lucide:gauge',
    path: '/response-analysis',
    permission: 'system:read',
    defaultSize: 'wide',
    defaultVisible: false,
  },
  {
    id: 'auth-test',
    title: '认证接口测试',
    description: '验证登录、鉴权和外部系统接入流程。',
    icon: 'i-lucide:key-round',
    path: '/auth-test',
    permission: 'user:manage',
    defaultSize: 'small',
    defaultVisible: false,
  },
  {
    id: 'help',
    title: '帮助与文档',
    description: '查看功能说明、使用流程和常见问题。',
    icon: 'i-lucide:book-open',
    path: '/help',
    defaultSize: 'small',
    defaultVisible: false,
  },
]

// Functional categories are presentation only; permission and navigation stay on each definition.
const widgetGroups = [
  { label: '病案业务', color: 'blue', ids: ['archive', 'records', 'patients', 'archive-boxes'] },
  { label: '统计分析', color: 'teal', ids: ['statistics', 'statistics-detail'] },
  { label: '系统运维', color: 'amber', ids: ['monitoring', 'status', 'response-analysis'] },
  { label: '日志与审计', color: 'slate', ids: ['audit-images', 'logs'] },
  { label: '数据治理', color: 'indigo', ids: ['data-relations', 'oss-migration'] },
  { label: '系统管理', color: 'purple', ids: ['users', 'permissions', 'settings', 'auth-test'] },
  { label: '帮助与文档', color: 'slate', ids: ['help'] },
] as const
const widgetDefinitions: DashboardWidgetDefinition[] = widgetGroups.flatMap(group =>
  group.ids.flatMap((id) => {
    const definition = baseWidgetDefinitions.find(item => item.id === id)
    return definition ? [{ ...definition, group: group.label, defaultColor: group.color }] : []
  }),
)

const definitionsById = new Map(widgetDefinitions.map(definition => [definition.id, definition]))
const preferences = ref<DashboardWidgetPreference[]>(createDefaultWidgetPreferences(widgetDefinitions))
const appearance = ref(normalizeAppearance(null))
const editorBackgroundChanged = ref(false)
const editMode = ref(false)
const managerVisible = ref(false)
const editorVisible = ref(false)
const settingsDialogVisible = computed({
  get: () => managerVisible.value || editorVisible.value,
  set: (visible: boolean) => {
    if (!visible) {
      managerVisible.value = false
      editorVisible.value = false
    }
  },
})
// Keep content and geometry unchanged through the actual leave transition.
const settingsView = ref<'manager' | 'editor'>('manager')
const settingsFromManager = ref(true)
const settingsDialogTitle = computed(() => settingsView.value === 'editor' ? '编辑小组件' : '管理小组件')
watch(managerVisible, (visible) => {
  if (visible && !editorVisible.value) {
    settingsView.value = 'manager'
    settingsFromManager.value = true
  }
}, { flush: 'sync' })
let managerReturnFocus: HTMLElement | undefined
async function closeWidgetEditor() {
  editorVisible.value = false
  if (managerVisible.value) {
    settingsView.value = 'manager'
    await nextTick()
    if (managerReturnFocus?.isConnected) { managerReturnFocus.focus({ preventScroll: true }) }
  }
}
function beforeSettingsClose(done: () => void) {
  if (editorVisible.value && managerVisible.value) {
    void closeWidgetEditor()
  }
  else {
    done()
  }
}

const editingWidgetId = ref('')
const draggedWidgetId = ref('')
const dragPreview = ref<DashboardWidgetPreference[] | null>(null)
let dragBaseline: DashboardWidgetPreference[] | undefined
const dragOverWidgetId = ref('')
// 拖到「置顶状态不同」的卡片上时单独标记为不可放置，避免先亮起可放置高亮、松手后才被提示拒绝。
const dragInvalidWidgetId = ref('')
const editorForm = reactive({
  title: '',
  description: '',
  size: 'small' as DashboardWidgetSize,
  color: 'default' as DashboardWidgetColor,
  backgroundColor: 'default' as DashboardWidgetColor,
  showDescription: true,
  metricKeys: [] as string[],
  showChart: true,
  compact: false,
})

const storageKey = computed(() => {
  const identity = userStore.profile?.id ?? userStore.profile?.username ?? 'anonymous'
  return `MRR-ADMIN:dashboard-widgets:${identity}`
})

function canAccessWidget(definition: DashboardWidgetDefinition) {
  return !definition.permission || hasPermission(definition.permission)
}

const accessibleDefinitions = computed(() => widgetDefinitions.filter(canAccessWidget))
const preferencesById = computed(() => new Map(preferences.value.map(item => [item.id, item])))

const committedWidgets = computed<DashboardWidgetView[]>(() => sortWidgetPreferences(preferences.value).flatMap((preference) => {
  const definition = definitionsById.get(preference.id)
  if (!definition || !preference.visible || !canAccessWidget(definition)) {
    return []
  }
  return [{ ...definition, ...preference }]
}))

// Draft ordering is view-only; permissions and network sources use committed state.
const visibleWidgets = computed(() => {
  if (!dragPreview.value) { return committedWidgets.value }
  const rank = new Map(dragPreview.value.map((item, index) => [item.id, index]))
  return [...committedWidgets.value].sort((a, b) => (rank.get(a.id) ?? 0) - (rank.get(b.id) ?? 0))
})

const welcomeDescription = computed(() => `欢迎回来${userStore.profile?.displayName ? `，${userStore.profile.displayName}` : ''}。`)
function widgetSource(id: string): DashboardSource | undefined {
  // These two modules are standard navigation entries, even for older stored metric selections.
  return id === 'statistics-detail' ? 'statistics' : undefined
}
function selectedMetrics(widget: DashboardWidgetView) {
  return widget.metricKeys ?? (widget.id === 'statistics-detail' ? [] : definitionsById.get(widget.id)?.availableMetrics ?? [])
}
function hasData(widget: DashboardWidgetView) {
  return !!widgetSource(widget.id) && selectedMetrics(widget).length > 0
}
const activeSources = computed(() => [...new Set(committedWidgets.value.filter(hasData).map(widget => widgetSource(widget.id)!))].sort())
const { states: dataStates, refresh: refreshData } = useDashboardData(() => userStore.sessionEpoch, () => activeSources.value)
const editorSource = computed(() => widgetSource(editingWidgetId.value))
const editorMetrics = computed(() => editorSource.value ? sourceMetrics[editorSource.value] : [])

function loadAppearance() {
  try {
    appearance.value = normalizeAppearance(JSON.parse(localStorage.getItem(`${storageKey.value}:appearance`) ?? 'null'))
  }
  catch { appearance.value = normalizeAppearance(null) }
}
function updateAppearance(mode: DashboardAppearanceMode, strength = appearance.value.strength) {
  appearance.value = normalizeAppearance({ mode, strength })
  try { localStorage.setItem(`${storageKey.value}:appearance`, JSON.stringify(appearance.value)) }
  catch { ElMessage.warning('当前浏览器无法保存首页外观') }
}
function changeAppearanceMode(value: unknown) {
  if (appearanceModes.some(item => item.value === value)) { updateAppearance(value as DashboardAppearanceMode) }
}
function changeAppearanceStrength(value: unknown) {
  if (typeof value === 'number') { updateAppearance(appearance.value.mode, value) }
}

function loadPreferences() {
  try {
    const stored = localStorage.getItem(storageKey.value)
    preferences.value = mergeWidgetPreferences(widgetDefinitions, stored ? JSON.parse(stored) : null)
  }
  catch {
    preferences.value = createDefaultWidgetPreferences(widgetDefinitions)
  }
}

function persistPreferences() {
  try {
    localStorage.setItem(storageKey.value, JSON.stringify(preferences.value))
  }
  catch {
    ElMessage.warning('当前浏览器无法保存管理概览布局')
  }
}

function applyPreferences(nextPreferences: DashboardWidgetPreference[]) {
  preferences.value = nextPreferences
  persistPreferences()
}

function getWidgetPreference(widgetId: string) {
  return preferencesById.value.get(widgetId)
}

function isWidgetVisible(widgetId: string) {
  return getWidgetPreference(widgetId)?.visible !== false
}

function isWidgetPinned(widgetId: string) {
  return getWidgetPreference(widgetId)?.pinned === true
}

function getWidgetSize(widgetId: string): DashboardWidgetSize {
  return getWidgetPreference(widgetId)?.size ?? definitionsById.get(widgetId)?.defaultSize ?? 'small'
}

function getWidgetColor(widgetId: string): DashboardWidgetColor {
  return getWidgetPreference(widgetId)?.color ?? definitionsById.get(widgetId)?.defaultColor ?? 'default'
}

function openWidget(widget: DashboardWidgetView) {
  if (editMode.value || widget.id === 'archive' || hasData(widget)) {
    return
  }
  void router.push(widget.path)
}

const REORDER_KEYS: Record<string, -1 | 1> = {
  ArrowLeft: -1,
  ArrowUp: -1,
  ArrowRight: 1,
  ArrowDown: 1,
}

/**
 * 调整布局时提供键盘排序：Alt + 方向键与相邻小组件交换位置。
 * 原实现只支持鼠标拖拽，键盘与触屏用户无法调整顺序。
 */
function moveWidgetByKeyboard(widget: DashboardWidgetView, direction: -1 | 1) {
  const list = visibleWidgets.value
  const index = list.findIndex(item => item.id === widget.id)
  const neighbor = list[index + direction]
  if (index < 0 || !neighbor) {
    return
  }
  if (neighbor.pinned !== widget.pinned) {
    ElMessage.info('置顶组件与普通组件需要先设置为相同置顶状态后再排序')
    return
  }
  // moveWidget 把 source 插到 target 之前：向前移动时自己插到前一个之前，向后移动时让后一个插到自己之前。
  const next = direction < 0
    ? moveWidget(preferences.value, widget.id, neighbor.id)
    : moveWidget(preferences.value, neighbor.id, widget.id)
  applyPreferences(next)
  void nextTick(() => {
    document.querySelector<HTMLElement>(`[data-widget-id="${widget.id}"]`)?.focus()
  })
}

function handleWidgetKeydown(event: KeyboardEvent, widget: DashboardWidgetView) {
  if (event.target !== event.currentTarget) { return }
  if (editMode.value) {
    const direction = REORDER_KEYS[event.key]
    if (direction && event.altKey && event.target === event.currentTarget) {
      // 阻止冒泡：标签栏在 document 上监听 Alt + ←/→ 切换标签页。
      event.preventDefault()
      event.stopPropagation()
      moveWidgetByKeyboard(widget, direction)
    }
    return
  }
  if (widget.id === 'archive' || hasData(widget)) { return }
  if (event.key !== 'Enter' && event.key !== ' ') {
    return
  }
  event.preventDefault()
  openWidget(widget)
}

function handleDragStart(event: DragEvent, widgetId: string) {
  if (!editMode.value) {
    event.preventDefault()
    return
  }
  draggedWidgetId.value = widgetId
  event.dataTransfer?.setData('text/plain', widgetId)
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function isDropCompatible(targetWidgetId: string) {
  const source = getWidgetPreference(draggedWidgetId.value)
  const target = getWidgetPreference(targetWidgetId)
  return Boolean(source && target && source.pinned === target.pinned)
}

function handleDragEnter(widgetId: string) {
  if (!editMode.value || !draggedWidgetId.value || draggedWidgetId.value === widgetId) {
    return
  }
  if (isDropCompatible(widgetId)) {
    dragOverWidgetId.value = widgetId
    dragInvalidWidgetId.value = ''
  }
  else {
    dragOverWidgetId.value = ''
    dragInvalidWidgetId.value = widgetId
  }
}

function handleDragOver(event: DragEvent, widgetId: string) {
  if (!editMode.value || !draggedWidgetId.value) {
    return
  }
  // 只有可放置目标才 preventDefault；不可放置时浏览器显示「禁止」光标。
  if (draggedWidgetId.value === widgetId || isDropCompatible(widgetId)) {
    event.preventDefault()
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move'
    }
  }
}

function handleDrop(event: DragEvent, targetWidgetId: string) {
  if (!editMode.value) {
    return
  }
  const sourceWidgetId = draggedWidgetId.value || event.dataTransfer?.getData('text/plain') || ''
  const source = getWidgetPreference(sourceWidgetId)
  const target = getWidgetPreference(targetWidgetId)
  dragOverWidgetId.value = ''
  dragInvalidWidgetId.value = ''
  draggedWidgetId.value = ''
  if (!source || !target || sourceWidgetId === targetWidgetId) {
    return
  }
  if (source.pinned !== target.pinned) {
    ElMessage.info('置顶组件与普通组件需要先设置为相同置顶状态后再排序')
    return
  }
  applyPreferences(moveWidget(preferences.value, sourceWidgetId, targetWidgetId))
}

function handleDragEnd() {
  draggedWidgetId.value = ''
  dragOverWidgetId.value = ''
  dragInvalidWidgetId.value = ''
}

function toggleWidgetPinned(widgetId: string) {
  applyPreferences(setWidgetPinned(preferences.value, widgetId, !isWidgetPinned(widgetId)))
}

function setWidgetVisible(widgetId: string, visible: boolean) {
  applyPreferences(updateWidgetPreference(preferences.value, widgetId, { visible }))
}

function handleVisibilityChange(widgetId: string, value: unknown) {
  setWidgetVisible(widgetId, value === true)
}

function handleSizeChange(widgetId: string, value: unknown) {
  if (value === 'small' || value === 'wide' || value === 'large') {
    applyPreferences(updateWidgetPreference(preferences.value, widgetId, { size: value }))
  }
}

function handleColorChange(widgetId: string, value: unknown) {
  if (isWidgetColor(value)) {
    const preference = getWidgetPreference(widgetId)
    applyPreferences(updateWidgetPreference(preferences.value, widgetId, { color: value, backgroundColor: preference?.backgroundColor ?? preference?.color ?? 'default' }))
  }
}

const longPress = useWidgetLongPress({
  start(id) {
    if (editMode.value || managerVisible.value || editorVisible.value || !committedWidgets.value.some(item => item.id === id)) { return false }
    dragBaseline = preferences.value
    dragPreview.value = sortWidgetPreferences(preferences.value)
    draggedWidgetId.value = id
    return true
  },
  move(id) {
    const source = draggedWidgetId.value
    const compatible = id && id !== source && isDropCompatible(id)
    dragOverWidgetId.value = compatible ? id : ''
    dragInvalidWidgetId.value = id && id !== source && !isDropCompatible(id) ? id : ''
    if (!compatible || !dragPreview.value || preferences.value !== dragBaseline) { return false }
    const list = visibleWidgets.value
    const from = list.findIndex(item => item.id === source)
    const to = list.findIndex(item => item.id === id)
    if (from < 0 || to < 0) { return false }
    dragPreview.value = moveWidget(dragPreview.value, source, id, from < to)
    return true
  },
  drop(id) {
    if (!id || !dragPreview.value || preferences.value !== dragBaseline || !isDropCompatible(id)) { return }
    const next = dragPreview.value
    const before = sortWidgetPreferences(preferences.value)
    if (next.some((item, index) => item.id !== before[index]?.id)) { applyPreferences(next) }
  },
  end() {
    dragPreview.value = null
    dragBaseline = undefined
    handleDragEnd()
  },
})
const longPressActive = longPress.active
const longPressPoint = longPress.point
const windowWidth = computed(() => { void longPressPoint.value; return document.documentElement.clientWidth })
const draggedWidget = computed(() => committedWidgets.value.find(widget => widget.id === draggedWidgetId.value))
watch([storageKey, () => userStore.sessionEpoch, editMode, managerVisible, editorVisible, () => committedWidgets.value.map(item => item.id).sort().join(',')], () => longPress.cancel(), { flush: 'sync' })

function openWidgetEditor(widgetId: string) {
  const preference = getWidgetPreference(widgetId)
  const definition = definitionsById.get(widgetId)
  if (!preference || !definition) {
    return
  }
  managerReturnFocus = managerVisible.value && document.activeElement instanceof HTMLElement ? document.activeElement : undefined
  editingWidgetId.value = widgetId
  editorForm.title = preference.title
  editorForm.description = preference.description
  editorForm.size = preference.size
  editorForm.color = preference.color
  editorBackgroundChanged.value = false
  editorForm.backgroundColor = preference.backgroundColor ?? preference.color
  editorForm.showDescription = preference.showDescription !== false
  editorForm.metricKeys = [...(preference.metricKeys ?? (widgetId === 'statistics-detail' ? [] : definition.availableMetrics ?? []))]
  editorForm.showChart = preference.showChart !== false
  editorForm.compact = preference.compact === true
  settingsFromManager.value = managerVisible.value
  settingsView.value = 'editor'
  editorVisible.value = true
  if (managerVisible.value) {
    void nextTick(() => document.querySelector<HTMLElement>('.widget-editor-form input')?.focus({ preventScroll: true }))
  }
}

function restoreEditorDefaults() {
  const definition = definitionsById.get(editingWidgetId.value)
  if (!definition) {
    return
  }
  editorForm.title = definition.title
  editorForm.description = definition.description
  editorForm.size = definition.defaultSize
  editorForm.color = definition.defaultColor ?? 'default'
  editorBackgroundChanged.value = false
  editorForm.backgroundColor = editorForm.color
  editorForm.showDescription = true
  editorForm.metricKeys = [...(definition.id === 'statistics-detail' ? [] : definition.availableMetrics ?? [])]
  editorForm.showChart = true
  editorForm.compact = false
}

function saveWidgetEditor() {
  const definition = definitionsById.get(editingWidgetId.value)
  if (!definition) {
    return
  }
  applyPreferences(updateWidgetPreference(preferences.value, editingWidgetId.value, {
    title: editorForm.title.trim() || definition.title,
    description: editorForm.description.trim() || definition.description,
    size: editorForm.size,
    color: editorForm.color,
    backgroundColor: editorForm.backgroundColor,
    showDescription: editorForm.showDescription,
    metricKeys: editorForm.metricKeys.filter(key => definition.availableMetrics?.includes(key)),
    showChart: editorForm.showChart,
    compact: editorForm.compact,
  }))
  if (editorBackgroundChanged.value) { updateAppearance('custom') }
  void closeWidgetEditor()
  ElMessage.success('小组件设置已保存')
}

function handleWidgetCommand(command: unknown, widget: DashboardWidgetView) {
  const normalizedCommand = String(command)
  switch (normalizedCommand) {
    case 'edit':
      openWidgetEditor(widget.id)
      break
    case 'pin':
      toggleWidgetPinned(widget.id)
      break
    case 'hide':
      setWidgetVisible(widget.id, false)
      break
    default:
      if (normalizedCommand.startsWith('size:')) {
        handleSizeChange(widget.id, normalizedCommand.slice(5))
      }
  }
}

async function applyGroupColors() {
  try {
    await actionDialog.confirm('仅将所有模块的图标和底色恢复为所属功能分区的配色，保留排列、大小、名称和显示状态。', '应用功能分区配色', {
      confirmButtonText: '应用配色',
      cancelButtonText: '取消',
    })
  }
  catch { return }
  applyPreferences(preferences.value.map((item) => {
    const color = definitionsById.get(item.id)?.defaultColor ?? 'default'
    return { ...item, color, backgroundColor: color }
  }))
}
async function resetDashboard() {
  try {
    await actionDialog.confirm(
      '将恢复默认的小组件内容、颜色、大小、显示状态和排列顺序。',
      '恢复默认布局',
      {
        type: 'warning',
        confirmButtonText: '恢复默认',
        cancelButtonText: '取消',
      },
    )
  }
  catch {
    return
  }
  applyPreferences(createDefaultWidgetPreferences(widgetDefinitions))
  updateAppearance('focus', 4)
  ElMessage.success('管理概览已恢复默认布局')
}

watch(storageKey, () => { loadPreferences(); loadAppearance() }, { immediate: true, flush: 'sync' })
</script>

<template>
  <MrrPageShell width="fluid">
    <MrrPageHeader
      class="dashboard-header"
      :class="{ 'is-editing': editMode, 'is-managing': managerVisible || editorVisible }"
      title="管理概览"
      :description="welcomeDescription"
    >
      <template #actions>
        <div class="dashboard-workspace__actions">
          <el-button @click="managerVisible = true">
            <FaIcon name="i-lucide:layout-grid" />
            管理模块
          </el-button>
          <el-button :type="editMode ? 'primary' : undefined" @click="editMode = !editMode">
            <FaIcon :name="editMode ? 'i-lucide:check' : 'i-lucide:move'" />
            {{ editMode ? '完成调整' : '调整布局' }}
          </el-button>
          <el-button v-if="editMode" @click="resetDashboard">
            <FaIcon name="i-lucide:rotate-cw" />
            恢复默认
          </el-button>
        </div>
      </template>
    </MrrPageHeader>

    <component :is="DemoScenarioControl" v-if="DemoScenarioControl" />

    <section class="dashboard-workspace" aria-label="常用模块">
      <div v-if="editMode" class="dashboard-workspace__notice">
        <FaIcon name="i-lucide:move" />
        长按卡片或拖动卡片（也可聚焦后按 Alt + 方向键）调整顺序；右上角菜单可修改大小、颜色、内容和置顶状态。
      </div>

      <!-- TransitionGroup 只用于重排时的位移过渡（FLIP），卡片换位有连续轨迹而不是瞬移。 -->
      <TransitionGroup v-if="visibleWidgets.length" tag="div" name="dashboard-widget" class="dashboard-grid" :class="{ 'is-editing': editMode, 'has-data-widgets': activeSources.length > 0, 'is-live-dragging': longPressActive }">
        <article
          v-for="widget in visibleWidgets"
          :key="widget.id"
          class="dashboard-widget"
          :class="[
            `dashboard-widget--${widget.size}`,
            `dashboard-widget--color-${widget.backgroundColor ?? widget.color}`,
            {
              'is-editing': editMode,
              'has-content': widget.id === 'archive' || hasData(widget),
              'is-action': widget.id === 'archive',
              'is-compact': widget.compact,
              'is-pinned': widget.pinned,
              'is-dragging': draggedWidgetId === widget.id,
              'is-drag-over': dragOverWidgetId === widget.id,
              'is-drop-invalid': dragInvalidWidgetId === widget.id,
            },
          ]"
          :style="widgetAppearanceStyle(widget.color, widget.backgroundColor ?? widget.color, widget.id, appearance)"
          :data-widget-id="widget.id"
          :draggable="editMode"
          :role="editMode || widget.id === 'archive' || hasData(widget) ? 'group' : 'link'"
          :aria-label="`${widget.title}${widget.pinned ? '，已置顶' : ''}${editMode ? '，按 Alt 加方向键调整位置' : ''}`"
          :aria-grabbed="editMode ? draggedWidgetId === widget.id : undefined"
          :tabindex="editMode || (widget.id !== 'archive' && !hasData(widget)) ? 0 : undefined"
          @pointerdown="longPress.pointerDown($event, widget.id)"
          @touchstart.passive="longPress.touchStart($event, widget.id)"
          @click="openWidget(widget)"
          @keydown="handleWidgetKeydown($event, widget)"
          @dragstart="handleDragStart($event, widget.id)"
          @dragenter.prevent="handleDragEnter(widget.id)"
          @dragover="handleDragOver($event, widget.id)"
          @drop.prevent="handleDrop($event, widget.id)"
          @dragend="handleDragEnd"
        >
          <header class="dashboard-widget__header">
            <span class="dashboard-widget__icon" aria-hidden="true">
              <FaIcon :name="widget.icon" />
            </span>
            <div class="dashboard-widget__heading">
              <div class="dashboard-widget__title-row">
                <strong>{{ widget.title }}</strong>
                <span v-if="widget.pinned" class="pin-badge">
                  <FaIcon name="i-lucide:pin" />
                  <span class="sr-only">置顶</span>
                </span>
              </div>
              <p v-if="widget.showDescription !== false" class="dashboard-widget__description">
                {{ widget.description }}
              </p>
            </div>

            <div v-if="editMode" class="dashboard-widget__tools" @click.stop>
              <el-tooltip content="拖动小组件" placement="top">
                <el-button text circle size="small" class="drag-handle" aria-label="拖动小组件">
                  <FaIcon name="i-lucide:grip-vertical" />
                </el-button>
              </el-tooltip>
              <el-dropdown trigger="click" @command="handleWidgetCommand($event, widget)">
                <el-button text circle size="small" aria-label="小组件设置">
                  <FaIcon name="i-lucide:ellipsis" />
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit">
                      <FaIcon name="i-lucide:pencil" />编辑设置
                    </el-dropdown-item>
                    <el-dropdown-item command="pin">
                      <FaIcon :name="widget.pinned ? 'i-lucide:pin-off' : 'i-lucide:pin'" />
                      {{ widget.pinned ? '取消置顶' : '置顶' }}
                    </el-dropdown-item>
                    <el-dropdown-item divided command="size:small">
                      标准大小
                    </el-dropdown-item>
                    <el-dropdown-item command="size:wide">
                      宽卡片
                    </el-dropdown-item>
                    <el-dropdown-item command="size:large">
                      大卡片
                    </el-dropdown-item>
                    <el-dropdown-item divided command="hide">
                      <FaIcon name="i-lucide:eye-off" />隐藏组件
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
            <!-- 整卡可点，头部箭头是唯一的入口暗示；底部不再重复「进入模块 →」。 -->
            <div v-else-if="widget.id === 'archive'" class="archive-header-actions" @click.stop @keydown.stop>
              <HomeArchiveHistory :key="userStore.sessionEpoch" />
              <el-button text circle :aria-label="`进入${widget.title}`" @click.stop="router.push(widget.path)">
                <FaIcon name="i-lucide:arrow-up-right" />
              </el-button>
            </div>
            <el-button v-else-if="hasData(widget)" text circle :aria-label="`进入${widget.title}`" @click.stop="router.push(widget.path)">
              <FaIcon name="i-lucide:arrow-up-right" />
            </el-button>
            <FaIcon v-else name="i-lucide:chevron-right" class="dashboard-widget__arrow" aria-hidden="true" />
          </header>
          <div v-if="widget.id === 'archive' || hasData(widget)" class="dashboard-widget__content">
            <HomeArchiveLookup v-if="widget.id === 'archive'" :key="userStore.sessionEpoch" :size="widget.size" :editing="editMode" />
            <DashboardDataContent v-else-if="widgetSource(widget.id)" :source="widgetSource(widget.id)!" :state="dataStates[widgetSource(widget.id)!]" :size="widget.size" :metric-keys="selectedMetrics(widget)" :show-chart="widget.showChart" :preview="editMode" @refresh="refreshData(widgetSource(widget.id)!)" />
          </div>
        </article>
      </TransitionGroup>

      <div v-else class="dashboard-empty">
        <FaIcon name="i-lucide:layout-grid" />
        <strong>当前没有显示的小组件</strong>
        <p>可以从“小组件管理”中重新启用需要的模块。</p>
        <el-button type="primary" @click="managerVisible = true">
          管理小组件
        </el-button>
      </div>
    </section>

    <Teleport to="body">
      <div v-if="longPressActive && longPressPoint && draggedWidget" class="widget-drag-preview" aria-hidden="true" :style="{ ...widgetAppearanceStyle(draggedWidget.color, draggedWidget.backgroundColor ?? draggedWidget.color, draggedWidget.id, appearance), transform: `translate3d(${Math.max(8, Math.min(longPressPoint.x + 12, windowWidth - 200))}px, ${Math.max(8, longPressPoint.y - 52)}px, 0)` }">
        <span class="widget-drag-preview__icon"><FaIcon :name="draggedWidget.icon" /></span>
        <strong>{{ draggedWidget.title }}</strong>
      </div>
    </Teleport>

    <el-dialog v-model="settingsDialogVisible" align-center :title="settingsDialogTitle" :width="settingsFromManager ? '860px' : '540px'" :before-close="beforeSettingsClose" class="dashboard-settings-dialog">
      <div v-show="settingsView === 'manager'" class="widget-manager-view">
        <p class="dialog-description">
          选择首页模块，默认按功能分区配色；可以独立设置图标、底色、大小和置顶状态。配置只保存在当前浏览器和当前账号下。
        </p>
        <div class="dashboard-appearance-controls">
          <div class="dashboard-appearance-row">
            <strong>首页配色</strong>
            <el-radio-group :model-value="appearance.mode" aria-label="首页配色风格" @change="changeAppearanceMode">
              <el-radio-button v-for="mode in appearanceModes" :key="mode.value" :value="mode.value">
                {{ mode.label }}
              </el-radio-button>
            </el-radio-group>
          </div>
          <div v-if="appearance.mode === 'tint' || appearance.mode === 'focus'" class="dashboard-appearance-row">
            <span>背景染色强度</span>
            <el-slider :model-value="appearance.strength" :min="0" :max="10" :step="1" :format-tooltip="value => `${value}%`" aria-label="背景染色强度" @input="changeAppearanceStrength" />
            <span class="appearance-strength">{{ appearance.strength }}%</span>
          </div>
          <small>外观模式仅影响显示效果；原有单卡底色保留，可通过完全自定义恢复。</small>
        </div>
        <div class="widget-manager-list">
          <div v-for="definition in accessibleDefinitions" :key="definition.id" class="widget-manager-item">
            <span class="widget-manager-item__icon" :style="paletteStyle(getWidgetColor(definition.id), 'default')">
              <FaIcon :name="definition.icon" />
            </span>
            <div class="widget-manager-item__copy">
              <strong>{{ getWidgetPreference(definition.id)?.title || definition.title }}</strong>
              <small>{{ definition.group }} · {{ definition.description }}</small>
            </div>
            <el-select
              :model-value="getWidgetSize(definition.id)"
              class="widget-size-select"
              :disabled="!isWidgetVisible(definition.id)"
              aria-label="卡片大小"
              @change="handleSizeChange(definition.id, $event)"
            >
              <el-option v-for="option in sizeOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <el-select
              :model-value="getWidgetColor(definition.id)"
              class="widget-color-select"
              :disabled="!isWidgetVisible(definition.id)"
              aria-label="图标颜色"
              @change="handleColorChange(definition.id, $event)"
            >
              <el-option v-for="option in colorOptions" :key="option.value" :label="option.label" :value="option.value">
                <span class="color-option">
                  <span class="color-option__swatch" :style="{ '--swatch-color': option.accent }" />
                  {{ option.label }}
                </span>
              </el-option>
            </el-select>
            <el-button
              text
              :type="isWidgetPinned(definition.id) ? 'primary' : undefined"
              :disabled="!isWidgetVisible(definition.id)"
              @click="toggleWidgetPinned(definition.id)"
            >
              <FaIcon name="i-lucide:pin" />
              {{ isWidgetPinned(definition.id) ? '已置顶' : '置顶' }}
            </el-button>
            <el-button text :disabled="!isWidgetVisible(definition.id)" @click="openWidgetEditor(definition.id)">
              <FaIcon name="i-lucide:pencil" />编辑
            </el-button>
            <el-switch
              :model-value="isWidgetVisible(definition.id)"
              :aria-label="`${isWidgetVisible(definition.id) ? '隐藏' : '显示'}${definition.title}`"
              @change="handleVisibilityChange(definition.id, $event)"
            />
          </div>
        </div>
      </div>
      <el-form v-if="settingsView === 'editor'" label-position="top" class="widget-editor-form mrr-edit-form" :class="{ 'is-manager-editor': settingsFromManager }">
        <el-form-item label="组件名称">
          <el-input v-model="editorForm.title" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item label="组件说明">
          <el-input v-model="editorForm.description" type="textarea" :rows="4" maxlength="120" show-word-limit />
        </el-form-item>
        <el-form-item label="组件大小">
          <el-radio-group v-model="editorForm.size">
            <el-radio-button v-for="option in sizeOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="展示内容">
          <el-checkbox v-model="editorForm.showDescription">
            模块说明
          </el-checkbox>
          <el-checkbox v-model="editorForm.compact">
            紧凑密度
          </el-checkbox>
          <el-checkbox v-if="editorSource" v-model="editorForm.showChart">
            可视化
          </el-checkbox>
        </el-form-item>
        <el-form-item v-if="editorSource" label="指标选择">
          <el-checkbox-group v-model="editorForm.metricKeys">
            <el-checkbox v-for="metric in editorMetrics" :key="metric.key" :value="metric.key">
              {{ metric.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <div class="widget-editor-preview" :class="`dashboard-widget--color-${editorForm.backgroundColor}`" :style="widgetAppearanceStyle(editorForm.color, editorForm.backgroundColor, editingWidgetId, editorBackgroundChanged ? { ...appearance, mode: 'custom' } : appearance)">
          <span class="widget-manager-item__icon"><FaIcon :name="definitionsById.get(editingWidgetId)?.icon || 'i-lucide:layout-grid'" /></span>
          <strong>{{ editorForm.title }}</strong>
          <p v-if="editorForm.showDescription">
            {{ editorForm.description }}
          </p>
          <DashboardDataContent v-if="editorSource && editorForm.metricKeys.length" :source="editorSource" :state="dataStates[editorSource]" :size="editorForm.size" :metric-keys="editorForm.metricKeys" :show-chart="editorForm.showChart" preview />
          <small v-if="editingWidgetId === 'archive'">病案号 · 上架号 · 最近查询</small>
        </div>
        <p class="dialog-description">
          选择卡片底色并保存，将切换为完全自定义外观；其他卡片的原有底色配置不变。
        </p>
        <el-form-item v-for="field in ([['color', '图标颜色'], ['backgroundColor', '卡片底色']] as const)" :key="field[0]" :label="field[1]">
          <div class="color-picker-options" :aria-label="field[1]" role="group">
            <button
              v-for="option in colorOptions"
              :key="option.value"
              type="button"
              class="color-picker-option"
              :class="{ 'is-selected': editorForm[field[0]] === option.value }"
              :style="{ '--swatch-color': field[0] === 'color' ? option.accent : option.surface }"
              :aria-pressed="editorForm[field[0]] === option.value"
              @click="editorForm[field[0]] = option.value; if (field[0] === 'backgroundColor') editorBackgroundChanged = true"
            >
              <span class="color-picker-option__swatch" />
              {{ option.label }}
            </button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <template v-if="settingsView === 'editor'">
          <el-button @click="restoreEditorDefaults">
            恢复该组件默认值
          </el-button>
          <el-button @click="closeWidgetEditor">
            取消
          </el-button>
          <el-button type="primary" @click="saveWidgetEditor">
            保存
          </el-button>
        </template>
        <template v-else>
          <el-button @click="applyGroupColors">
            应用功能配色
          </el-button>
          <el-button @click="resetDashboard">
            恢复默认
          </el-button>
          <el-button type="primary" @click="managerVisible = false">
            完成
          </el-button>
        </template>
      </template>
    </el-dialog>
  </MrrPageShell>
</template>

<style scoped>
.dashboard-workspace__notice {
  display: inline-flex;
  gap: var(--mrr-space-2);
  align-items: center;
  min-height: 25px;
  padding: 3px var(--mrr-space-2);
  font-size: var(--mrr-font-size-xs);
  font-weight: 550;
  color: var(--mrr-secondary-foreground);
  background: var(--mrr-secondary);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-pill);
}

.dashboard-workspace {
  display: grid;
  gap: var(--mrr-space-4);
  min-width: 0;
}

.dashboard-workspace__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
}

/* Keep the header geometry stable; keyboard focus and explicit editing always reveal actions.
 * Coarse-pointer devices have no reliable hover, so retain visible controls there.
 */
@media (hover: hover) and (pointer: fine) {
  .dashboard-workspace__actions,
  .dashboard-widget__arrow,
  .dashboard-widget .pin-badge {
    opacity: 0;
    transition: opacity var(--mrr-motion-feedback) var(--mrr-ease-out);
  }

  .dashboard-workspace__actions {
    pointer-events: none;
  }

  .dashboard-header:hover .dashboard-workspace__actions,
  .dashboard-header:focus-within .dashboard-workspace__actions,
  .dashboard-header.is-editing .dashboard-workspace__actions,
  .dashboard-header.is-managing .dashboard-workspace__actions {
    pointer-events: auto;
  }

  .dashboard-header:hover .dashboard-workspace__actions,
  .dashboard-header:focus-within .dashboard-workspace__actions,
  .dashboard-header.is-editing .dashboard-workspace__actions,
  .dashboard-header.is-managing .dashboard-workspace__actions,
  .dashboard-widget:hover .dashboard-widget__arrow,
  .dashboard-widget:focus-within .dashboard-widget__arrow,
  .dashboard-widget:hover .pin-badge,
  .dashboard-widget:focus-within .pin-badge,
  .dashboard-widget.is-editing .pin-badge {
    opacity: 1;
  }

  @media (prefers-reduced-motion: reduce) {
    .dashboard-workspace__actions,
    .dashboard-widget__arrow,
    .dashboard-widget .pin-badge {
      transition: none;
    }
  }
}

.dashboard-workspace__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.dashboard-workspace__notice {
  justify-self: start;
  border-style: dashed;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  grid-auto-rows: minmax(84px, 1fr);
  grid-auto-flow: dense;
  gap: var(--mrr-space-4);
}

.dashboard-grid.has-data-widgets {
  grid-auto-rows: minmax(84px, auto);
}

.dashboard-widget {
  --widget-accent: var(--mrr-primary);
  --widget-surface: var(--mrr-card);
  --widget-border: var(--mrr-border);
  --widget-icon-size: 48px;
  --widget-glyph-size: 24px;

  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100%;
  overflow: hidden;
  color: var(--mrr-card-foreground);
  cursor: pointer;
  background: linear-gradient(rgb(var(--widget-tint-rgb), var(--widget-tint-alpha)), rgb(var(--widget-tint-rgb), var(--widget-tint-alpha))) var(--widget-surface);
  border: 1px solid var(--widget-border);
  border-radius: var(--mrr-radius-xl);
  box-shadow: var(--mrr-shadow-xs);
  transition:
    background-color var(--mrr-motion-feedback) var(--mrr-ease-out),
    border-color var(--mrr-motion-feedback) var(--mrr-ease-out),
    box-shadow var(--mrr-motion-feedback) var(--mrr-ease-out),
    opacity var(--mrr-motion-feedback) var(--mrr-ease-out),
    transform var(--mrr-motion-press) var(--mrr-ease-out);
}

.dashboard-widget--small {
  grid-row: span 1;
  grid-column: span 1;
}

.dashboard-widget--wide {
  grid-row: span 1;
  grid-column: span 2;
}

.dashboard-widget--large {
  grid-row: span 2;
  grid-column: span 2;
}

/* 查询卡固定跨两行，保留用户选择的列宽；单列移动端自然排版。 */
@media (min-width: 641px) {
  .dashboard-widget.is-action {
    grid-row: span 2;
  }
}

.dashboard-widget:hover {
  border-color: var(--mrr-border-strong);
  box-shadow: var(--mrr-shadow-sm);
}

.dashboard-widget:active:not(.is-editing) {
  transform: var(--mrr-motion-decor, translateY(1px));
}

.dashboard-widget:focus-visible {
  outline: 2px solid var(--widget-accent);
  outline-offset: 2px;
}

.dashboard-widget.is-editing {
  cursor: grab;
  border-style: dashed;
}

.dashboard-widget.is-editing:active {
  cursor: grabbing;
}

.dashboard-widget.is-dragging {
  cursor: grabbing;
  user-select: none;
  opacity: 0.45;
}

.dashboard-grid.is-live-dragging .dashboard-widget.is-dragging {
  background: var(--mrr-card);
  border: 1px dashed var(--widget-accent);
  box-shadow: none;
  opacity: 0.5;
}

.dashboard-grid.is-live-dragging .dashboard-widget.is-dragging > * {
  opacity: 0;
}

.dashboard-widget.is-drag-over {
  border-color: var(--widget-accent);
  box-shadow: 0 0 0 2px var(--widget-icon-surface);
}

/* 不可放置：中性虚线 + 禁止光标，与「可放置」的主色高亮明确区分。 */
.dashboard-widget.is-drop-invalid {
  cursor: no-drop;
  border-color: var(--mrr-border-strong);
  opacity: 0.72;
}

/* 重排位移：卡片换位走 move 档，覆盖卡片自身 transition（同优先级，后声明生效）。 */
.dashboard-widget-move {
  transition: transform var(--mrr-motion-move) var(--mrr-ease-out);
}

@media (prefers-reduced-motion: reduce) {
  .dashboard-widget-move { transition: none; }
}

.dashboard-widget.has-content .dashboard-widget__header {
  flex: 0 0 auto;
  padding-bottom: var(--mrr-space-2);
}

.dashboard-widget__content {
  min-width: 0;
  padding: 0 var(--mrr-space-4) var(--mrr-space-4);
  cursor: default;
}
.dashboard-widget.has-content:not(.is-editing) { cursor: default; }
.dashboard-widget.has-content:active:not(.is-editing) { transform: none; }
.dashboard-widget.is-compact .dashboard-widget__header { gap: var(--mrr-space-2); padding: var(--mrr-space-2); }
.dashboard-widget.is-compact .dashboard-widget__content { padding: 0 var(--mrr-space-2) var(--mrr-space-2); }
.widget-editor-preview { display: grid; gap: var(--mrr-space-2); padding: var(--mrr-space-3); margin-bottom: var(--mrr-space-4); background: linear-gradient(rgb(var(--widget-tint-rgb), var(--widget-tint-alpha)), rgb(var(--widget-tint-rgb), var(--widget-tint-alpha))) var(--widget-surface, var(--mrr-card)); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-lg); }
.widget-editor-preview p { margin: 0; font-size: var(--mrr-font-size-xs); color: var(--mrr-muted-foreground); }

.archive-header-actions {
  display: flex;
  gap: var(--mrr-space-1);
  align-items: center;
  align-self: start;
}

.archive-header-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.dashboard-widget__header {
  display: grid;
  flex: 1;
  grid-template-columns: var(--widget-icon-size) minmax(0, 1fr) auto;
  gap: var(--mrr-space-4);
  align-items: center;
  min-width: 0;
  padding: var(--mrr-space-4);
  user-select: none;
  -webkit-touch-callout: none;
}

.dashboard-widget__icon,
.widget-manager-item__icon {
  display: grid;
  flex: 0 0 32px;
  place-items: center;
  width: 32px;
  height: 32px;
  font-size: 15px;
  color: var(--widget-accent, var(--mrr-primary));
  background: var(--widget-icon-surface, var(--mrr-tone-primary-soft));
  border: 1px solid transparent;
  border-radius: var(--mrr-radius-md);
}

/* 悬停时图标块与箭头同步染上强调色：一张卡只有一个「可进入」信号，但它会随指针亮起。 */
.dashboard-widget__icon {
  width: var(--widget-icon-size);
  height: var(--widget-icon-size);
  font-size: var(--widget-glyph-size);
  border-radius: var(--mrr-radius-lg);
  transition:
    color var(--mrr-motion-feedback) var(--mrr-ease-out),
    background-color var(--mrr-motion-feedback) var(--mrr-ease-out),
    border-color var(--mrr-motion-feedback) var(--mrr-ease-out);
}

.dashboard-widget__heading {
  min-width: 0;
}

.dashboard-widget__title-row {
  display: flex;
  gap: var(--mrr-space-2);
  align-items: center;
  min-width: 0;
}

.dashboard-widget__title-row strong {
  min-width: 0;
  font-size: var(--mrr-font-size-lg);
  font-weight: 650;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.pin-badge {
  display: inline-flex;
  flex: 0 0 auto;
  gap: 3px;
  align-items: center;
  padding: 2px var(--mrr-space-1);
  font-size: var(--mrr-font-size-xs);
  font-weight: 650;

  /* 用户可自定义主色，主色直接压在主色淡底上会跌破 4.5:1（默认主题实测 4.49:1）；
     文字改用前景色，科室/组件身份仍由淡底与图标承载 */
  color: var(--mrr-foreground);
  background: var(--widget-icon-surface);
  border-radius: var(--mrr-radius-pill);
}

.dashboard-widget__tools {
  display: flex;
  gap: 1px;
  align-items: center;
}

.dashboard-widget__tools :deep(.el-button + .el-button) {
  margin-left: 0;
}

.drag-handle {
  cursor: grab;
}

.dashboard-widget__arrow {
  flex: 0 0 auto;
  font-size: 16px;
  color: var(--mrr-muted-foreground);
  transition: opacity var(--mrr-motion-feedback) var(--mrr-ease-out), color var(--mrr-motion-feedback) var(--mrr-ease-out), transform var(--mrr-motion-feedback) var(--mrr-ease-out);
}

.dashboard-widget:hover .dashboard-widget__arrow,
.dashboard-widget:focus-visible .dashboard-widget__arrow {
  color: var(--widget-accent);
  transform: var(--mrr-motion-decor, translateX(2px));
}

.dashboard-widget__description {
  margin: var(--mrr-space-1) 0 0;
  font-size: var(--mrr-font-size-sm);
  line-height: 1.5;
  color: var(--mrr-muted-foreground);
  overflow-wrap: anywhere;
}

.dashboard-empty {
  display: grid;
  place-content: center;
  place-items: center;
  min-height: 260px;
  padding: var(--mrr-space-6);
  text-align: center;
  background: color-mix(in srgb, var(--mrr-muted) 22%, transparent);
  border: 1px dashed var(--mrr-border);
  border-radius: var(--mrr-radius-xl);
}

.dashboard-empty > :first-child {
  margin-bottom: var(--mrr-space-3);
  font-size: 42px;
  color: var(--mrr-muted-foreground);
}

.dashboard-empty strong {
  font-size: var(--mrr-font-size-md);
}

.dashboard-empty p {
  margin: var(--mrr-space-2) 0 var(--mrr-space-4);
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-muted-foreground);
}

.dialog-description {
  margin: 0 0 var(--mrr-space-3);
  font-size: var(--mrr-font-size-xs);
  line-height: 1.6;
  color: var(--mrr-muted-foreground);
}

.dashboard-appearance-controls {
  display: grid;
  flex-shrink: 0;
  gap: var(--mrr-space-2);
  padding: 0 0 var(--mrr-space-3);
  font-size: var(--mrr-font-size-sm);
}

.dashboard-appearance-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-3);
  align-items: center;
}

.dashboard-appearance-row :deep(.el-slider) {
  flex: 1;
  min-width: 100px;
  max-width: 280px;
}

.dashboard-appearance-controls small {
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-muted-foreground);
}

.appearance-strength {
  min-width: 36px;
  font-variant-numeric: tabular-nums;
}

.widget-manager-list {
  display: grid;
  max-height: min(62vh, 620px);
  overflow-y: auto;
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-lg);
}

.widget-manager-item {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) 96px 110px auto auto auto;
  gap: var(--mrr-space-3);
  align-items: center;
  min-width: 0;
  padding: var(--mrr-space-3) var(--mrr-space-3);
  border-bottom: 1px solid var(--mrr-shell-divider);
}

.widget-manager-item:last-child {
  border-bottom: 0;
}

.widget-manager-item__icon--blue {
  --widget-accent: var(--el-color-primary);
}

.widget-manager-item__icon--green {
  --widget-accent: var(--el-color-success);
}

.widget-manager-item__icon--orange {
  --widget-accent: var(--el-color-warning);
}

.widget-manager-item__icon--red {
  --widget-accent: var(--el-color-danger);
}

.widget-manager-item__icon--purple {
  --widget-accent: var(--mrr-tone-violet);
}

.widget-manager-item__copy {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.widget-manager-item__copy strong,
.widget-manager-item__copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.widget-manager-item__copy strong {
  font-size: var(--mrr-font-size-sm);
}

.widget-manager-item__copy small {
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-muted-foreground);
}

.widget-size-select {
  width: 96px;
}

.widget-color-select {
  width: 110px;
}

.color-option {
  display: inline-flex;
  gap: var(--mrr-space-2);
  align-items: center;
}

.color-option__swatch,
.color-picker-option__swatch {
  width: 14px;
  height: 14px;
  background: var(--swatch-color, var(--mrr-card));
  border: 1px solid var(--mrr-border);
  border-radius: 50%;
}

.color-option__swatch--blue,
.color-picker-option--blue {
  --swatch-color: var(--el-color-primary);
}

.color-option__swatch--green,
.color-picker-option--green {
  --swatch-color: var(--el-color-success);
}

.color-option__swatch--orange,
.color-picker-option--orange {
  --swatch-color: var(--el-color-warning);
}

.color-option__swatch--red,
.color-picker-option--red {
  --swatch-color: var(--el-color-danger);
}

.color-option__swatch--purple,
.color-picker-option--purple {
  --swatch-color: var(--mrr-tone-violet);
}

.color-picker-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--mrr-space-2);
  width: 100%;
}

.color-picker-option {
  display: flex;
  gap: var(--mrr-space-2);
  align-items: center;
  height: var(--mrr-control-height);
  min-height: var(--mrr-control-height);
  padding: 0 var(--mrr-space-3);
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-foreground);
  cursor: pointer;
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
}

.color-picker-option:focus-visible {
  outline: 2px solid var(--mrr-ring);
  outline-offset: 2px;
}

.color-picker-option:hover,
.color-picker-option.is-selected {
  border-color: var(--swatch-color, var(--mrr-primary));
}

.color-picker-option.is-selected {
  background: color-mix(in srgb, var(--swatch-color, var(--mrr-primary)) 7%, var(--mrr-card));
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--swatch-color, var(--mrr-primary)) 16%, transparent);
}

.widget-editor-form {
  margin-top: var(--mrr-space-2);
}

.widget-manager-view,
.widget-editor-form.is-manager-editor {
  height: min(62vh, 620px);
  min-height: 0;
  overflow-y: auto;
}

.widget-manager-view {
  display: flex;
  flex-direction: column;
}

.widget-manager-view .widget-manager-list {
  flex: 1;
  min-height: 0;
}

.widget-editor-form.is-manager-editor {
  max-width: 540px;
  padding: 2px var(--mrr-space-2);
  margin: 0 auto;
}

@media (max-width: 1180px) {
  .dashboard-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .widget-manager-item {
    grid-template-columns: 32px minmax(0, 1fr) 92px 104px auto auto;
  }

  .widget-manager-item :deep(.el-switch) {
    grid-column: 6;
  }
}

@media (max-width: 900px) {
  .dashboard-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .widget-manager-item {
    grid-template-columns: 32px minmax(0, 1fr) auto;
  }

  .widget-manager-item__copy {
    grid-column: 2 / 4;
  }

  .widget-size-select,
  .widget-color-select {
    width: 100%;
  }

  .widget-size-select {
    grid-column: 2;
  }

  .widget-color-select {
    grid-column: 3;
  }

  .widget-manager-item :deep(.el-switch) {
    grid-column: 3;
  }
}

@media (max-width: 640px) {
  .dashboard-widget {
    --widget-icon-size: 44px;
    --widget-glyph-size: 22px;
  }

  .dashboard-widget__header {
    gap: var(--mrr-space-3);
    padding: var(--mrr-space-3);
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
    grid-auto-rows: auto;

    &.has-data-widgets {
      grid-auto-rows: auto;
    }
  }

  .dashboard-widget--small,
  .dashboard-widget--wide,
  .dashboard-widget--large {
    grid-row: auto;
    grid-column: span 1;
    min-height: 84px;
  }

  .dashboard-widget--large {
    min-height: 172px;
  }

  .widget-manager-item {
    grid-template-columns: 32px minmax(0, 1fr) auto;
  }

  .color-picker-options {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

<style>
:root {
  --widget-violet-accent: var(--mrr-tone-violet);
  --widget-rgb-default: 37, 99, 235;
  --widget-rgb-blue: 37, 99, 235;
  --widget-rgb-green: 21, 128, 61;
  --widget-rgb-orange: 182, 101, 24;
  --widget-rgb-red: 194, 65, 65;
  --widget-rgb-purple: 124, 58, 237;
  --widget-rgb-cyan: 8, 123, 148;
  --widget-rgb-teal: 8, 125, 102;
  --widget-rgb-pink: 181, 45, 112;
  --widget-rgb-amber: 153, 99, 11;
  --widget-rgb-indigo: 79, 70, 184;
  --widget-rgb-slate: 82, 100, 123;
  --widget-cyan: #087b94;
  --widget-cyan-soft: #edf8fb;
  --widget-teal: #087d66;
  --widget-teal-soft: #edf8f3;
  --widget-pink: #b52d70;
  --widget-pink-soft: #fceff5;
  --widget-amber: #99630b;
  --widget-amber-soft: #fbf5e6;
  --widget-indigo: #4f46b8;
  --widget-indigo-soft: #f0effc;
  --widget-slate: #52647b;
  --widget-slate-soft: #eff3f8;
}

html.dark {
  --widget-violet-accent: #c0a4f8;
  --widget-rgb-default: 139, 171, 255;
  --widget-rgb-blue: 139, 171, 255;
  --widget-rgb-green: 115, 207, 142;
  --widget-rgb-orange: 229, 183, 124;
  --widget-rgb-red: 245, 145, 145;
  --widget-rgb-purple: 192, 164, 248;
  --widget-rgb-cyan: 105, 200, 223;
  --widget-rgb-teal: 101, 209, 180;
  --widget-rgb-pink: 240, 147, 189;
  --widget-rgb-amber: 232, 189, 104;
  --widget-rgb-indigo: 178, 169, 246;
  --widget-rgb-slate: 184, 201, 221;
  --widget-cyan: #69c8df;
  --widget-cyan-soft: #20363e;
  --widget-teal: #65d1b4;
  --widget-teal-soft: #203a32;
  --widget-pink: #f093bd;
  --widget-pink-soft: #3e2937;
  --widget-amber: #e8bd68;
  --widget-amber-soft: #3d3425;
  --widget-indigo: #b2a9f6;
  --widget-indigo-soft: #322e48;
  --widget-slate: #b8c9dd;
  --widget-slate-soft: #2b3542;
}

.widget-drag-preview {
  position: fixed;
  inset: 0 auto auto 0;
  z-index: 5000;
  display: flex;
  gap: var(--mrr-space-2);
  align-items: center;
  width: 192px;
  max-width: calc(100vw - 16px);
  padding: var(--mrr-space-2) var(--mrr-space-3);
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-card-foreground);
  pointer-events: none;
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
  box-shadow: var(--mrr-shadow-md);
}

.widget-drag-preview__icon {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 30px;
  height: 30px;
  font-size: 18px;
  color: var(--widget-accent);
  background: var(--widget-icon-surface);
  border-radius: var(--mrr-radius-md);
}

.widget-drag-preview strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
