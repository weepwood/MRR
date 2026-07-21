<script setup lang="ts">
import type {
  DashboardWidgetColor,
  DashboardWidgetDefinition,
  DashboardWidgetPreference,
  DashboardWidgetSize,
} from './dashboard-widgets'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import { useUserStore } from '@/store/modules/user'
import { hasPermission } from '@/utils/session'
import {
  createDefaultWidgetPreferences,
  mergeWidgetPreferences,
  moveWidget,
  setWidgetPinned,
  sortWidgetPreferences,
  updateWidgetPreference,
} from './dashboard-widgets'

defineOptions({ name: 'DashboardWorkspace' })

type DashboardWidgetView = DashboardWidgetDefinition & DashboardWidgetPreference

const router = useRouter()
const userStore = useUserStore()

const sizeOptions: Array<{ label: string, value: DashboardWidgetSize }> = [
  { label: '标准', value: 'small' },
  { label: '宽', value: 'wide' },
  { label: '大', value: 'large' },
]

const colorOptions: Array<{ label: string, value: DashboardWidgetColor }> = [
  { label: '默认', value: 'default' },
  { label: '蓝色', value: 'blue' },
  { label: '绿色', value: 'green' },
  { label: '橙色', value: 'orange' },
  { label: '红色', value: 'red' },
  { label: '紫色', value: 'purple' },
]

const widgetDefinitions: DashboardWidgetDefinition[] = [
  {
    id: 'archive',
    title: '影像档案袋',
    description: '查询并浏览病案影像、缩略图和患者信息。',
    icon: 'i-ant-design:folder-open-twotone',
    path: '/archive/embed',
    permission: 'record:read',
    defaultSize: 'wide',
    defaultPinned: true,
  },
  {
    id: 'records',
    title: '记录管理',
    description: '维护病案扫描记录及其基础数据。',
    icon: 'i-ant-design:database-twotone',
    path: '/records',
    permission: 'record:read',
    defaultSize: 'small',
  },
  {
    id: 'statistics',
    title: '病案扫描统计',
    description: '按年份、科室和扫描状态查看统计结果。',
    icon: 'i-ant-design:area-chart-outlined',
    path: '/statistics',
    permission: 'statistics:read',
    defaultSize: 'wide',
  },
  {
    id: 'monitoring',
    title: '系统监控',
    description: '查看服务、数据库、内存和运行指标。',
    icon: 'i-ant-design:dashboard-twotone',
    path: '/monitoring',
    permission: 'system:read',
    defaultSize: 'small',
  },
  {
    id: 'statistics-detail',
    title: '统计明细',
    description: '查看病案扫描与档案记录的详细数据。',
    icon: 'i-ant-design:profile-twotone',
    path: '/statistics-detail',
    permission: 'statistics:read',
    defaultSize: 'small',
  },
  {
    id: 'audit-images',
    title: '病案图片访问审计',
    description: '查询病案影像查看、下载和访问记录。',
    icon: 'i-ant-design:security-scan-outlined',
    path: '/audit-images',
    permission: 'log:read',
    defaultSize: 'small',
  },
  {
    id: 'patients',
    title: '患者管理',
    description: '查看患者与住院病案的关联信息。',
    icon: 'i-ant-design:team-outlined',
    path: '/patients',
    permission: 'record:read',
    defaultSize: 'small',
    defaultVisible: false,
  },
  {
    id: 'archive-boxes',
    title: '档案装箱',
    description: '管理病案装箱、箱号和档案状态。',
    icon: 'i-ant-design:inbox-outlined',
    path: '/archive-boxes',
    permission: 'record:read',
    defaultSize: 'small',
    defaultVisible: false,
  },
  {
    id: 'oss-migration',
    title: 'OSS 迁移管理',
    description: '查看与管理病案影像迁移任务。',
    icon: 'i-ant-design:cloud-upload-outlined',
    path: '/oss-migration',
    permission: 'record:read',
    defaultSize: 'small',
    defaultVisible: false,
  },
  {
    id: 'data-relations',
    title: '数据关系工作台',
    description: '检查数据库表之间的关联和数据完整性。',
    icon: 'i-ant-design:apartment-outlined',
    path: '/data-relations',
    permission: 'system:read',
    defaultSize: 'wide',
    defaultVisible: false,
  },
  {
    id: 'users',
    title: '用户管理',
    description: '创建用户、审核账号和重置密码。',
    icon: 'i-ant-design:user-outlined',
    path: '/users',
    permission: 'user:manage',
    defaultSize: 'small',
    defaultVisible: false,
  },
  {
    id: 'permissions',
    title: '权限管理',
    description: '维护角色权限和功能访问范围。',
    icon: 'i-ant-design:lock-twotone',
    path: '/permissions',
    permission: 'role:read',
    defaultSize: 'small',
    defaultVisible: false,
  },
  {
    id: 'settings',
    title: '系统设置',
    description: '配置系统信息、档案浏览、安全和界面外观。',
    icon: 'i-ant-design:tool-twotone',
    path: '/settings',
    permission: 'system:read',
    defaultSize: 'small',
    defaultVisible: false,
  },
  {
    id: 'logs',
    title: '日志管理',
    description: '检索系统日志、操作记录和异常信息。',
    icon: 'i-ant-design:file-search-outlined',
    path: '/logs',
    permission: 'log:read',
    defaultSize: 'small',
    defaultVisible: false,
  },
  {
    id: 'status',
    title: '服务状态',
    description: '快速确认系统服务是否正常运行。',
    icon: 'i-ant-design:check-circle-twotone',
    path: '/system-status',
    permission: 'system:read',
    defaultSize: 'small',
    defaultVisible: false,
  },
  {
    id: 'response-analysis',
    title: '接口响应分析',
    description: '分析慢接口、响应时间和近期性能趋势。',
    icon: 'i-ant-design:fund-projection-screen-outlined',
    path: '/response-analysis',
    permission: 'system:read',
    defaultSize: 'wide',
    defaultVisible: false,
  },
  {
    id: 'auth-test',
    title: '认证接口测试',
    description: '验证登录、鉴权和外部系统接入流程。',
    icon: 'i-ant-design:api-twotone',
    path: '/auth-test',
    permission: 'user:manage',
    defaultSize: 'small',
    defaultVisible: false,
  },
  {
    id: 'help',
    title: '帮助与文档',
    description: '查看功能说明、使用流程和常见问题。',
    icon: 'i-ant-design:read-outlined',
    path: '/help',
    defaultSize: 'small',
    defaultVisible: false,
  },
]

const definitionsById = new Map(widgetDefinitions.map(definition => [definition.id, definition]))
const preferences = ref<DashboardWidgetPreference[]>(createDefaultWidgetPreferences(widgetDefinitions))
const editMode = ref(false)
const managerVisible = ref(false)
const editorVisible = ref(false)
const editingWidgetId = ref('')
const draggedWidgetId = ref('')
const dragOverWidgetId = ref('')
const editorForm = reactive({
  title: '',
  description: '',
  size: 'small' as DashboardWidgetSize,
  color: 'default' as DashboardWidgetColor,
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

const visibleWidgets = computed<DashboardWidgetView[]>(() => sortWidgetPreferences(preferences.value).flatMap((preference) => {
  const definition = definitionsById.get(preference.id)
  if (!definition || !preference.visible || !canAccessWidget(definition)) {
    return []
  }
  return [{ ...definition, ...preference }]
}))

const accessibleWidgetCount = computed(() => accessibleDefinitions.value.length)
const hiddenWidgetCount = computed(() => accessibleDefinitions.value.filter(definition => !isWidgetVisible(definition.id)).length)
const welcomeDescription = computed(() => `欢迎回来${userStore.profile?.displayName ? `，${userStore.profile.displayName}` : ''}。可以按自己的工作习惯调整小组件内容、颜色、大小和位置。`)

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
  if (editMode.value) {
    return
  }
  void router.push(widget.path)
}

function handleWidgetKeydown(event: KeyboardEvent, widget: DashboardWidgetView) {
  if (editMode.value || (event.key !== 'Enter' && event.key !== ' ')) {
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

function handleDragEnter(widgetId: string) {
  if (editMode.value && draggedWidgetId.value && draggedWidgetId.value !== widgetId) {
    dragOverWidgetId.value = widgetId
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
  if (value === 'default' || value === 'blue' || value === 'green' || value === 'orange' || value === 'red' || value === 'purple') {
    applyPreferences(updateWidgetPreference(preferences.value, widgetId, { color: value }))
  }
}

function openWidgetEditor(widgetId: string) {
  const preference = getWidgetPreference(widgetId)
  const definition = definitionsById.get(widgetId)
  if (!preference || !definition) {
    return
  }
  editingWidgetId.value = widgetId
  editorForm.title = preference.title
  editorForm.description = preference.description
  editorForm.size = preference.size
  editorForm.color = preference.color
  editorVisible.value = true
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
  }))
  editorVisible.value = false
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

async function resetDashboard() {
  try {
    await ElMessageBox.confirm(
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
  ElMessage.success('管理概览已恢复默认布局')
}

watch(storageKey, loadPreferences, { immediate: true })
</script>

<template>
  <MrrPageShell width="fluid">
    <MrrPageHeader
      eyebrow="Medical Record Repository"
      title="管理概览"
      :description="welcomeDescription"
    >
      <template #badge>
        <span class="overview-count">
          <span class="overview-count__dot" />
          {{ visibleWidgets.length }} / {{ accessibleWidgetCount }} 个小组件
        </span>
      </template>

      <template #actions>
        <el-button @click="managerVisible = true">
          <FaIcon name="i-ri:layout-grid-line" />
          管理小组件
          <span v-if="hiddenWidgetCount" class="hidden-count">{{ hiddenWidgetCount }}</span>
        </el-button>
        <el-button :type="editMode ? 'primary' : undefined" @click="editMode = !editMode">
          <FaIcon :name="editMode ? 'i-ri:check-line' : 'i-ri:drag-move-2-line'" />
          {{ editMode ? '完成调整' : '调整布局' }}
        </el-button>
        <el-button v-if="editMode" @click="resetDashboard">
          <FaIcon name="i-ri:restart-line" />
          恢复默认
        </el-button>
      </template>
    </MrrPageHeader>

    <section class="dashboard-workspace" aria-label="我的工作台">
      <div v-if="editMode" class="dashboard-workspace__notice">
        <FaIcon name="i-ri:drag-move-2-line" />
        拖动卡片调整顺序；右上角菜单可修改大小、颜色、内容和置顶状态。
      </div>

      <div v-if="visibleWidgets.length" class="dashboard-grid" :class="{ 'is-editing': editMode }">
        <article
          v-for="widget in visibleWidgets"
          :key="widget.id"
          class="dashboard-widget"
          :class="[
            `dashboard-widget--${widget.size}`,
            `dashboard-widget--color-${widget.color}`,
            {
              'is-editing': editMode,
              'is-pinned': widget.pinned,
              'is-dragging': draggedWidgetId === widget.id,
              'is-drag-over': dragOverWidgetId === widget.id,
            },
          ]"
          :draggable="editMode"
          :role="editMode ? 'group' : 'link'"
          :aria-label="`${widget.title}${widget.pinned ? '，已置顶' : ''}`"
          :aria-grabbed="editMode ? draggedWidgetId === widget.id : undefined"
          tabindex="0"
          @click="openWidget(widget)"
          @keydown="handleWidgetKeydown($event, widget)"
          @dragstart="handleDragStart($event, widget.id)"
          @dragenter.prevent="handleDragEnter(widget.id)"
          @dragover.prevent
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
                  <FaIcon name="i-ri:pushpin-2-fill" />
                  置顶
                </span>
              </div>
            </div>

            <div v-if="editMode" class="dashboard-widget__tools" @click.stop>
              <el-tooltip content="拖动小组件" placement="top">
                <el-button text circle size="small" class="drag-handle" aria-label="拖动小组件">
                  <FaIcon name="i-ri:draggable" />
                </el-button>
              </el-tooltip>
              <el-dropdown trigger="click" @command="handleWidgetCommand($event, widget)">
                <el-button text circle size="small" aria-label="小组件设置">
                  <FaIcon name="i-ri:more-2-fill" />
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit">
                      <FaIcon name="i-ri:edit-line" />编辑设置
                    </el-dropdown-item>
                    <el-dropdown-item command="pin">
                      <FaIcon :name="widget.pinned ? 'i-ri:unpin-line' : 'i-ri:pushpin-2-line'" />
                      {{ widget.pinned ? '取消置顶' : '置顶' }}
                    </el-dropdown-item>
                    <el-dropdown-item divided command="size:small">标准大小</el-dropdown-item>
                    <el-dropdown-item command="size:wide">宽卡片</el-dropdown-item>
                    <el-dropdown-item command="size:large">大卡片</el-dropdown-item>
                    <el-dropdown-item divided command="hide">
                      <FaIcon name="i-ri:eye-off-line" />隐藏组件
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
            <FaIcon v-else name="i-ri:arrow-right-s-line" class="dashboard-widget__arrow" />
          </header>

          <div class="dashboard-widget__body">
            <p>{{ widget.description }}</p>
            <div class="dashboard-widget__footer">
              <span>{{ editMode ? '拖动调整位置' : '进入模块' }}</span>
              <FaIcon :name="editMode ? 'i-ri:drag-move-2-line' : 'i-ri:arrow-right-line'" />
            </div>
          </div>
        </article>
      </div>

      <div v-else class="dashboard-empty">
        <FaIcon name="i-ant-design:appstore-add-outlined" />
        <strong>当前没有显示的小组件</strong>
        <p>可以从“小组件管理”中重新启用需要的模块。</p>
        <el-button type="primary" @click="managerVisible = true">管理小组件</el-button>
      </div>
    </section>

    <el-dialog v-model="managerVisible" title="管理小组件" width="860px">
      <p class="dialog-description">
        选择首页需要展示的模块，并设置大小、颜色和置顶状态。配置只保存在当前浏览器和当前账号下。
      </p>
      <div class="widget-manager-list">
        <div v-for="definition in accessibleDefinitions" :key="definition.id" class="widget-manager-item">
          <span class="widget-manager-item__icon" :class="`widget-manager-item__icon--${getWidgetColor(definition.id)}`">
            <FaIcon :name="definition.icon" />
          </span>
          <div class="widget-manager-item__copy">
            <strong>{{ getWidgetPreference(definition.id)?.title || definition.title }}</strong>
            <small>{{ definition.description }}</small>
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
            aria-label="卡片颜色"
            @change="handleColorChange(definition.id, $event)"
          >
            <el-option v-for="option in colorOptions" :key="option.value" :label="option.label" :value="option.value">
              <span class="color-option">
                <span class="color-option__swatch" :class="`color-option__swatch--${option.value}`" />
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
            <FaIcon :name="isWidgetPinned(definition.id) ? 'i-ri:pushpin-2-fill' : 'i-ri:pushpin-2-line'" />
            {{ isWidgetPinned(definition.id) ? '已置顶' : '置顶' }}
          </el-button>
          <el-button text :disabled="!isWidgetVisible(definition.id)" @click="openWidgetEditor(definition.id)">
            <FaIcon name="i-ri:edit-line" />编辑
          </el-button>
          <el-switch
            :model-value="isWidgetVisible(definition.id)"
            :aria-label="`${isWidgetVisible(definition.id) ? '隐藏' : '显示'}${definition.title}`"
            @change="handleVisibilityChange(definition.id, $event)"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="resetDashboard">恢复默认</el-button>
        <el-button type="primary" @click="managerVisible = false">完成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editorVisible" title="编辑小组件" width="540px">
      <el-form label-position="top" class="widget-editor-form">
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
        <el-form-item label="卡片颜色">
          <div class="color-picker-options">
            <button
              v-for="option in colorOptions"
              :key="option.value"
              type="button"
              class="color-picker-option"
              :class="[
                `color-picker-option--${option.value}`,
                { 'is-selected': editorForm.color === option.value },
              ]"
              :aria-pressed="editorForm.color === option.value"
              @click="editorForm.color = option.value"
            >
              <span class="color-picker-option__swatch" />
              {{ option.label }}
            </button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="restoreEditorDefaults">恢复该组件默认值</el-button>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" @click="saveWidgetEditor">保存</el-button>
      </template>
    </el-dialog>
  </MrrPageShell>
</template>

<style scoped>
.overview-count,
.dashboard-workspace__notice {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  min-height: 25px;
  padding: 3px 9px;
  font-size: 12px;
  font-weight: 550;
  color: var(--mrr-secondary-foreground);
  background: var(--mrr-secondary);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-pill);
}

.overview-count__dot {
  width: 6px;
  height: 6px;
  background: var(--mrr-primary);
  border-radius: 50%;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--mrr-primary) 10%, transparent);
}

.hidden-count {
  display: inline-grid;
  min-width: 18px;
  height: 18px;
  margin-left: 2px;
  padding-inline: 4px;
  font-size: 10px;
  color: var(--mrr-primary-foreground);
  background: var(--mrr-primary);
  border-radius: var(--mrr-radius-pill);
  place-items: center;
}

.dashboard-workspace {
  display: grid;
  gap: var(--mrr-space-4);
  min-width: 0;
}

.dashboard-workspace__notice {
  justify-self: start;
  border-style: dashed;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  grid-auto-flow: dense;
  grid-auto-rows: 132px;
  gap: var(--mrr-space-4);
}

.dashboard-widget {
  --widget-accent: var(--mrr-primary);
  --widget-surface: var(--mrr-card);
  --widget-border: var(--mrr-border);

  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100%;
  overflow: hidden;
  color: var(--mrr-card-foreground);
  cursor: pointer;
  background: var(--widget-surface);
  border: 1px solid var(--widget-border);
  border-radius: var(--mrr-radius-xl);
  box-shadow: var(--mrr-shadow-xs);
  transition:
    background-color var(--mrr-motion-fast) ease,
    border-color var(--mrr-motion-fast) ease,
    box-shadow var(--mrr-motion-fast) ease,
    opacity var(--mrr-motion-fast) ease,
    transform var(--mrr-motion-fast) var(--mrr-ease-out);
}

.dashboard-widget--color-blue {
  --widget-accent: var(--el-color-primary);
  --widget-surface: color-mix(in srgb, var(--el-color-primary) 7%, var(--mrr-card));
  --widget-border: color-mix(in srgb, var(--el-color-primary) 24%, var(--mrr-border));
}

.dashboard-widget--color-green {
  --widget-accent: var(--el-color-success);
  --widget-surface: color-mix(in srgb, var(--el-color-success) 7%, var(--mrr-card));
  --widget-border: color-mix(in srgb, var(--el-color-success) 24%, var(--mrr-border));
}

.dashboard-widget--color-orange {
  --widget-accent: var(--el-color-warning);
  --widget-surface: color-mix(in srgb, var(--el-color-warning) 8%, var(--mrr-card));
  --widget-border: color-mix(in srgb, var(--el-color-warning) 28%, var(--mrr-border));
}

.dashboard-widget--color-red {
  --widget-accent: var(--el-color-danger);
  --widget-surface: color-mix(in srgb, var(--el-color-danger) 7%, var(--mrr-card));
  --widget-border: color-mix(in srgb, var(--el-color-danger) 24%, var(--mrr-border));
}

.dashboard-widget--color-purple {
  --widget-accent: color-mix(in srgb, #8b5cf6 86%, var(--mrr-foreground));
  --widget-surface: color-mix(in srgb, #8b5cf6 8%, var(--mrr-card));
  --widget-border: color-mix(in srgb, #8b5cf6 26%, var(--mrr-border));
}

.dashboard-widget--small {
  grid-column: span 1;
  grid-row: span 1;
}

.dashboard-widget--wide {
  grid-column: span 2;
  grid-row: span 1;
}

.dashboard-widget--large {
  grid-column: span 2;
  grid-row: span 2;
}

.dashboard-widget:hover {
  border-color: color-mix(in srgb, var(--widget-accent) 48%, var(--mrr-border));
  box-shadow: var(--mrr-shadow-sm);
}

.dashboard-widget:active:not(.is-editing) {
  transform: translateY(1px);
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

.dashboard-widget.is-pinned {
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--widget-accent) 16%, transparent), var(--mrr-shadow-xs);
}

.dashboard-widget.is-dragging {
  opacity: 0.45;
}

.dashboard-widget.is-drag-over {
  border-color: var(--widget-accent);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--widget-accent) 18%, transparent);
}

.dashboard-widget__header {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 14px 14px 10px;
}

.dashboard-widget__icon,
.widget-manager-item__icon {
  display: grid;
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  font-size: 15px;
  color: var(--widget-accent, var(--mrr-primary));
  background: color-mix(in srgb, var(--widget-accent, var(--mrr-primary)) 10%, var(--mrr-card));
  border: 1px solid color-mix(in srgb, var(--widget-accent, var(--mrr-primary)) 20%, var(--mrr-border));
  border-radius: var(--mrr-radius-md);
  place-items: center;
}

.dashboard-widget__heading {
  min-width: 0;
}

.dashboard-widget__title-row {
  display: flex;
  gap: 7px;
  align-items: center;
  min-width: 0;
}

.dashboard-widget__title-row strong {
  overflow: hidden;
  font-size: 14px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pin-badge {
  display: inline-flex;
  flex: 0 0 auto;
  gap: 3px;
  align-items: center;
  padding: 2px 6px;
  font-size: 9px;
  font-weight: 650;
  color: var(--widget-accent);
  background: color-mix(in srgb, var(--widget-accent) 10%, var(--mrr-card));
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
  color: var(--mrr-muted-foreground);
  transition: color var(--mrr-motion-fast) ease, transform var(--mrr-motion-fast) var(--mrr-ease-out);
}

.dashboard-widget:hover .dashboard-widget__arrow {
  color: var(--widget-accent);
  transform: translateX(2px);
}

.dashboard-widget__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 0 14px 13px;
}

.dashboard-widget__body p {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.6;
  color: var(--mrr-muted-foreground);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.dashboard-widget--large .dashboard-widget__body p {
  font-size: 13px;
  -webkit-line-clamp: 7;
}

.dashboard-widget__footer {
  display: flex;
  gap: 6px;
  align-items: center;
  justify-content: flex-end;
  margin-top: auto;
  padding-top: 9px;
  font-size: 10px;
  color: var(--mrr-muted-foreground);
}

.dashboard-widget--small .dashboard-widget__footer {
  display: none;
}

.dashboard-empty {
  display: grid;
  min-height: 260px;
  padding: var(--mrr-space-6);
  text-align: center;
  background: color-mix(in srgb, var(--mrr-muted) 22%, transparent);
  border: 1px dashed var(--mrr-border);
  border-radius: var(--mrr-radius-xl);
  place-content: center;
  place-items: center;
}

.dashboard-empty > :first-child {
  margin-bottom: 12px;
  font-size: 42px;
  color: var(--mrr-muted-foreground);
}

.dashboard-empty strong {
  font-size: 14px;
}

.dashboard-empty p {
  margin: 6px 0 16px;
  font-size: 12px;
  color: var(--mrr-muted-foreground);
}

.dialog-description {
  margin: 0 0 14px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--mrr-muted-foreground);
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
  gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 11px 12px;
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
  --widget-accent: color-mix(in srgb, #8b5cf6 86%, var(--mrr-foreground));
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
  font-size: 13px;
}

.widget-manager-item__copy small {
  font-size: 10px;
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
  gap: 8px;
  align-items: center;
}

.color-option__swatch,
.color-picker-option__swatch {
  width: 14px;
  height: 14px;
  background: var(--swatch-color, var(--mrr-card));
  border: 1px solid color-mix(in srgb, var(--swatch-color, var(--mrr-border)) 44%, var(--mrr-border));
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
  --swatch-color: #8b5cf6;
}

.color-picker-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  width: 100%;
}

.color-picker-option {
  display: flex;
  gap: 8px;
  align-items: center;
  min-height: 38px;
  padding: 8px 10px;
  font-size: 12px;
  color: var(--mrr-foreground);
  cursor: pointer;
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
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
  margin-top: 6px;
}

@media (width <= 1180px) {
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

@media (width <= 900px) {
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

@media (width <= 640px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
    grid-auto-rows: auto;
  }

  .dashboard-widget--small,
  .dashboard-widget--wide,
  .dashboard-widget--large {
    grid-column: span 1;
    grid-row: auto;
    min-height: 132px;
  }

  .dashboard-widget--large {
    min-height: 190px;
  }

  .widget-manager-item {
    grid-template-columns: 32px minmax(0, 1fr) auto;
  }

  .color-picker-options {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (prefers-reduced-motion: reduce) {
  .dashboard-widget,
  .dashboard-widget__arrow {
    transition: none;
  }

  .dashboard-widget:active:not(.is-editing),
  .dashboard-widget:hover .dashboard-widget__arrow {
    transform: none;
  }
}
</style>