<script setup lang="ts" generic="Entry extends StorageEntry">
import type { StorageEntry } from './presentation'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowUp, ChevronDown, ChevronRight, ChevronUp, Copy, File, FileArchive, FileImage, FileText, Folder, Info, LayoutGrid, List, Maximize, Minimize, RefreshCw, Search } from 'lucide-vue-next'
import { computed, nextTick, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { entryType, formatDate, formatFileSize, isImage } from './presentation'

const props = defineProps<{
  entries: readonly Entry[]
  location: (entry: Entry) => string
  breadcrumbs: { label: string, path: string }[]
  selected?: Entry | null
  pageLabel: string
  summary?: string
  sourceDetail?: { label: string, value: string }
  loading: boolean
  available: boolean
  canGoBack: boolean
  canGoUp: boolean
  canPreviousPage: boolean
  canNextPage: boolean
  locationLabel: string
  error?: string
  unavailableText: string
}>()

const emit = defineEmits<{
  back: []
  up: []
  refresh: []
  retry: []
  navigate: [path: string]
  select: [entry: Entry | undefined]
  open: [entry: Entry]
  previous: []
  next: []
}>()

const address = defineModel<string>('address', { required: true })
const filter = defineModel<string>('filter', { required: true })
const columns = [
  { key: 'name', label: '名称' },
  { key: 'type', label: '类型' },
  { key: 'size', label: '大小' },
  { key: 'modified', label: '修改时间' },
] as const
type SortKey = typeof columns[number]['key']

const names = new Intl.Collator('zh-CN', { numeric: true, sensitivity: 'base' })
const archiveTypes = new Set(['ZIP', '7Z', 'RAR', 'GZ', 'TAR'])
const documentTypes = new Set(['PDF', 'TXT', 'CSV', 'DOC', 'DOCX', 'XLS', 'XLSX'])
const mode = ref<'icons' | 'details'>('details')
const sort = ref<{ key: SortKey, ascending: boolean }>({ key: 'name', ascending: true })
const filePane = ref<HTMLElement>()
const keyword = computed(() => filter.value.trim().toLocaleLowerCase())
const visibleEntries = computed(() => {
  const entries = keyword.value
    ? props.entries.filter(entry => entry.name.toLocaleLowerCase().includes(keyword.value))
    : [...props.entries]
  const { key, ascending } = sort.value
  return entries.sort((a, b) => {
    if (a.directory !== b.directory) {
      return Number(b.directory) - Number(a.directory)
    }
    const order = compareColumn(key, a, b) || names.compare(a.name, b.name)
    return ascending ? order : -order
  })
})
const selected = computed(() => props.selected && visibleEntries.value.includes(props.selected) ? props.selected : undefined)

function compareColumn(key: SortKey, a: StorageEntry, b: StorageEntry) {
  switch (key) {
    case 'type':
      return names.compare(entryType(a), entryType(b))
    case 'size':
      return a.size - b.size
    case 'modified':
      return (Date.parse(a.lastModified ?? '') || 0) - (Date.parse(b.lastModified ?? '') || 0)
    default:
      return names.compare(a.name, b.name)
  }
}

function sortBy(key: SortKey) {
  sort.value = { key, ascending: sort.value.key !== key || !sort.value.ascending }
}

function icon(entry: StorageEntry) {
  if (entry.directory) {
    return Folder
  }
  if (isImage(entry)) {
    return FileImage
  }
  const type = entryType(entry)
  if (archiveTypes.has(type)) {
    return FileArchive
  }
  return documentTypes.has(type) ? FileText : File
}

function onNavigationKey(event: KeyboardEvent) {
  if (!event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) {
    return
  }
  if (event.key === 'ArrowLeft' && props.canGoBack && !props.loading) {
    event.preventDefault()
    emit('back')
  }
  if (event.key === 'ArrowUp' && props.canGoUp) {
    event.preventDefault()
    emit('up')
  }
}

function onEntryKey(event: KeyboardEvent, index: number) {
  if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) {
    return
  }
  const grid = mode.value === 'icons'
  const keys = grid ? ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Home', 'End'] : ['ArrowUp', 'ArrowDown', 'Home', 'End']
  if (!keys.includes(event.key)) {
    return
  }
  event.preventDefault()
  const parent = (event.currentTarget as HTMLElement).parentElement
  const stride = grid && parent ? getComputedStyle(parent).gridTemplateColumns.split(' ').length : 1
  const lastIndex = visibleEntries.value.length - 1
  const offsets: Record<string, number> = { ArrowUp: -stride, ArrowDown: stride, ArrowLeft: -1, ArrowRight: 1 }
  const nextIndex = event.key === 'Home' ? 0 : event.key === 'End' ? lastIndex : index + offsets[event.key]
  if (nextIndex < 0 || nextIndex > lastIndex || nextIndex === index) {
    return
  }
  emit('select', visibleEntries.value[nextIndex])
  filePane.value?.querySelectorAll<HTMLButtonElement>('button[data-file-entry]')[nextIndex]?.focus()
}

function selectRow(entry: Entry, event: MouseEvent) {
  emit('select', entry)
  const row = event.currentTarget as HTMLElement
  row.querySelector('button')?.focus()
}

watch(() => props.entries, async () => {
  const pane = filePane.value
  if (!pane?.contains(document.activeElement)) {
    return
  }
  await nextTick()
  if (document.activeElement === document.body) {
    const target = pane.querySelector<HTMLElement>('button[data-file-entry]') ?? pane
    target.focus()
  }
})

async function copyLocation(entry: Entry) {
  try {
    await navigator.clipboard.writeText(props.location(entry))
    ElMessage.success('路径已复制')
  }
  catch {
    ElMessage.error('复制失败，请选中路径后手动复制')
  }
}

/*
 * 占满内容区：工作区脱离文档流，铺满右侧内容区（左侧导航保留），顶边直接贴到视口顶部，
 * 把站点头部、标签栏和工具栏一并让给文件列表。横向从内容区左边界量起、右边界贴视口，
 * 所以侧边栏折叠（走动画）、窗口缩放时都能收口，不依赖写死的 CSS 变量。
 *
 * 同时锁住文档滚动：占满层是 fixed，滚轮在列表滚到底后会把滚动链传给背后页面，
 * 造成「文件列表没动、整页却在滚」。锁的是 body 的 overflow（与 el-dialog 同一套做法），
 * 退出时恢复原值，并修正被浏览器钳制过的滚动位置。
 */
const root = ref<HTMLElement>()
const maximizeAnchor = ref<HTMLElement>()
const maximized = ref(false)
const overlayStyle = ref<{ top: string, left: string, width: string, height: string, zIndex: string }>()
let overlayObserver: ResizeObserver | undefined
let scrollLock: { bodyOverflow: string, bodyPaddingRight: string, scrollY: number } | undefined

function measureOverlay() {
  const main = root.value?.closest<HTMLElement>('.main')
  if (!main) {
    overlayStyle.value = { top: '0px', left: '0px', width: `${window.innerWidth}px`, height: `${window.innerHeight}px`, zIndex: '1000' }
    return
  }
  const rect = main.getBoundingClientRect()
  const contentLeft = Math.max(0, Math.round(rect.left))
  // 窗口窄到右侧内容区不足以放下左右两栏时（本项目不启用移动端自适应，侧栏在窄屏仍常驻），
  // 让占满层连侧栏一起盖住，并把层级提到侧栏之上，否则文件区会被挤成一条窄缝。
  const takeover = window.innerWidth - contentLeft < 560
  const left = takeover ? 0 : contentLeft
  overlayStyle.value = {
    top: '0px',
    left: `${left}px`,
    // 宽度按视口算而不是 .main 的宽度：滚动条被锁掉后内容区会宽 15px 左右，
    // 用视口收口可以始终铺满，也不跟着这一次重排抖动。
    width: `${Math.max(window.innerWidth - left, 320)}px`,
    height: `${window.innerHeight}px`,
    zIndex: takeover ? '1012' : '1000',
  }
}

function lockPageScroll() {
  if (scrollLock) {
    return
  }
  const body = document.body
  const scrollbar = window.innerWidth - document.documentElement.clientWidth
  scrollLock = {
    bodyOverflow: body.style.overflow,
    bodyPaddingRight: body.style.paddingRight,
    scrollY: window.scrollY,
  }
  body.style.overflow = 'hidden'
  // 隐藏滚动条会让整页横向扩宽，补上等宽内边距，打开瞬间背后内容不横向跳动。
  if (scrollbar > 0) {
    body.style.paddingRight = `${scrollbar}px`
  }
}

function unlockPageScroll() {
  if (!scrollLock) {
    return
  }
  const { bodyOverflow, bodyPaddingRight, scrollY } = scrollLock
  scrollLock = undefined
  document.body.style.overflow = bodyOverflow
  document.body.style.paddingRight = bodyPaddingRight
  // 占满期间内容脱离文档流，浏览器可能把滚动位置钳到新的可滚范围；退出时还原。
  if (Math.abs(window.scrollY - scrollY) > 1) {
    window.scrollTo(0, scrollY)
  }
}

function stopTracking() {
  window.removeEventListener('resize', measureOverlay)
  overlayObserver?.disconnect()
  overlayObserver = undefined
}

function exitMaximize(restoreFocus = true) {
  if (!maximized.value) {
    return
  }
  maximized.value = false
  overlayStyle.value = undefined
  stopTracking()
  unlockPageScroll()
  if (restoreFocus) {
    maximizeAnchor.value?.querySelector('button')?.focus()
  }
}

function toggleMaximize() {
  if (maximized.value) {
    exitMaximize()
    return
  }
  // 先按当前文档流位置量一次，避免 class 生效的首帧没有定位信息而闪一下。
  measureOverlay()
  lockPageScroll()
  maximized.value = true
  window.addEventListener('resize', measureOverlay)
  const main = root.value?.closest<HTMLElement>('.main')
  if (main && typeof ResizeObserver !== 'undefined') {
    overlayObserver = new ResizeObserver(measureOverlay)
    // 工作区脱离文档流后 .main 可能变矮（滚动条消失、页面回弹），侧边栏折叠也会改宽度，
    // 靠观察器重新收口；页面滚动不参与，占满层在视口里的位置保持不动。
    overlayObserver.observe(main)
  }
  void nextTick(measureOverlay)
}

function hasOpenOverlay() {
  // el-dialog 的 .el-overlay 常驻 DOM，只有 v-show 切换显示，必须按实际可见性判断。
  return Array.from(document.querySelectorAll<HTMLElement>('.el-overlay')).some((node) => {
    const style = getComputedStyle(node)
    return style.display !== 'none' && style.visibility !== 'hidden'
  })
}

function onMaximizeKeydown(event: KeyboardEvent) {
  if (!maximized.value || event.key !== 'Escape' || event.defaultPrevented) {
    return
  }
  const target = event.target as HTMLElement | null
  // 对话框、下拉和输入框先消化自己的 Esc，不能顺手把占满也退掉。
  if (hasOpenOverlay() || target?.closest('input, textarea, .el-select, .el-popper')) {
    return
  }
  event.preventDefault()
  exitMaximize()
}

onMounted(() => {
  window.addEventListener('keydown', onMaximizeKeydown)
})

onDeactivated(() => {
  exitMaximize(false)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onMaximizeKeydown)
  exitMaximize(false)
})
</script>

<template>
  <section
    ref="root"
    class="storage-browser"
    :class="{ 'storage-browser--maximized': maximized }"
    :style="maximized ? overlayStyle : undefined"
    aria-label="文件浏览工作区"
  >
    <div class="source-bar">
      <div class="source-identity">
        <slot name="source" />
        <el-tooltip v-if="sourceDetail" :content="sourceDetail.value" placement="top">
          <button type="button" class="source-info" :aria-label="sourceDetail.label">
            <Info :size="16" aria-hidden="true" />
          </button>
        </el-tooltip>
      </div>
      <div class="source-actions">
        <span ref="maximizeAnchor" class="maximize-anchor">
          <el-button
            :icon="maximized ? Minimize : Maximize"
            :aria-pressed="maximized"
            :aria-label="maximized ? '退出占满区域' : '占满右侧内容区'"
            :title="maximized ? '还原到页面布局（Esc）' : '占满右侧内容区（Esc 退出）'"
            @click="toggleMaximize"
          >
            {{ maximized ? '还原' : '占满' }}
          </el-button>
        </span>
        <el-button :icon="RefreshCw" :loading="loading" @click="emit('refresh')">
          刷新
        </el-button>
      </div>
    </div>

    <div class="browser-body">
      <div class="navigation-bar">
        <form class="location-bar" @submit.prevent="emit('navigate', address)">
          <el-button-group>
            <el-button :icon="ArrowLeft" native-type="button" aria-label="返回" title="返回（Alt + ←）" :disabled="loading || !canGoBack" @click="emit('back')" />
            <el-button :icon="ArrowUp" native-type="button" aria-label="上一级" title="上一级（Alt + ↑）" :disabled="!canGoUp" @click="emit('up')" />
          </el-button-group>
          <el-input v-model="address" :disabled="!available" aria-label="目录路径" placeholder="输入目录路径">
            <template #prefix>
              <el-icon><Folder /></el-icon>
            </template>
          </el-input>
          <el-button native-type="submit" :disabled="!available">
            前往
          </el-button>
        </form>
        <el-input v-model="filter" :disabled="!available" class="page-filter" clearable aria-label="筛选当前页" placeholder="筛选当前页">
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <div class="listing-toolbar">
        <nav aria-label="当前位置" class="breadcrumbs">
          <template v-for="(crumb, index) in breadcrumbs" :key="crumb.path">
            <ChevronRight v-if="index" class="crumb-divider" aria-hidden="true" />
            <span v-if="index === breadcrumbs.length - 1" aria-current="location">{{ crumb.label }}</span>
            <button v-else type="button" class="crumb-button" @click="emit('navigate', crumb.path)">
              {{ crumb.label }}
            </button>
          </template>
        </nav>
        <div class="listing-controls">
          <el-button-group aria-label="显示方式">
            <el-button :icon="LayoutGrid" aria-label="图标视图" title="图标视图" :aria-pressed="mode === 'icons'" :type="mode === 'icons' ? 'primary' : 'default'" plain @click="mode = 'icons'">
              图标
            </el-button>
            <el-button :icon="List" title="详细列表" aria-label="列表视图" :aria-pressed="mode === 'details'" :type="mode === 'details' ? 'primary' : 'default'" plain @click="mode = 'details'">
              详细列表
            </el-button>
          </el-button-group>
        </div>
      </div>

      <div v-if="error" class="browser-notice" role="status">
        <span>{{ error }}</span>
        <el-button size="small" :disabled="loading" @click="emit('retry')">
          重试
        </el-button>
      </div>

      <div class="explorer-layout">
        <section ref="filePane" v-loading="loading" class="file-pane" tabindex="-1" aria-label="目录内容" :aria-busy="loading" @keydown="onNavigationKey">
          <div v-if="!visibleEntries.length" class="empty-pane">
            <el-empty v-if="!loading && !error" :image-size="64" :description="!available ? unavailableText : keyword ? '当前页没有匹配的条目' : '此目录为空'">
              <el-button v-if="available && keyword" @click="filter = ''">
                清除筛选
              </el-button>
            </el-empty>
          </div>
          <div v-else-if="mode === 'icons'" class="file-grid">
            <button
              v-for="(entry, index) in visibleEntries"
              :key="location(entry)"
              type="button"
              class="file-tile"
              :class="{ selected: selected === entry }"
              :aria-pressed="selected === entry"
              :tabindex="entry === (selected ?? visibleEntries[0]) ? 0 : -1"
              data-file-entry
              @click="emit('select', entry)"
              @dblclick="emit('open', entry)"
              @keydown="onEntryKey($event, index)"
              @keydown.enter.prevent="emit('open', entry)"
            >
              <component :is="icon(entry)" class="entry-icon" aria-hidden="true" />
              <span class="file-tile-name" :title="entry.name">{{ entry.name }}</span>
            </button>
          </div>
          <table v-else class="file-table" aria-label="文件列表">
            <thead>
              <tr>
                <th
                  v-for="column in columns"
                  :key="column.key"
                  scope="col"
                  :class="`column-${column.key}`"
                  :aria-sort="sort.key === column.key ? (sort.ascending ? 'ascending' : 'descending') : undefined"
                >
                  <button type="button" class="sort-button" title="按当前页排序" @click="sortBy(column.key)">
                    {{ column.label }}
                    <component :is="sort.ascending ? ChevronUp : ChevronDown" v-if="sort.key === column.key" class="sort-indicator" aria-hidden="true" />
                  </button>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(entry, index) in visibleEntries"
                :key="location(entry)"
                :class="{ selected: selected === entry }"
                @click="selectRow(entry, $event)"
                @dblclick="emit('open', entry)"
              >
                <td>
                  <button
                    type="button"
                    class="entry-name"
                    :title="entry.name"
                    :aria-pressed="selected === entry"
                    :tabindex="entry === (selected ?? visibleEntries[0]) ? 0 : -1"
                    data-file-entry
                    @click.stop="emit('select', entry)"
                    @keydown="onEntryKey($event, index)"
                    @keydown.enter.prevent="emit('open', entry)"
                  >
                    <component :is="icon(entry)" class="entry-icon" aria-hidden="true" />
                    <span>{{ entry.name }}</span>
                  </button>
                </td>
                <td class="column-type">
                  {{ entryType(entry) }}
                </td>
                <td>{{ entry.directory ? '—' : formatFileSize(entry.size) }}</td>
                <td class="column-modified">
                  {{ formatDate(entry.lastModified) }}
                </td>
              </tr>
            </tbody>
          </table>
        </section>

        <aside class="details-pane" aria-label="文件详情">
          <div class="details-heading">
            详情
          </div>
          <template v-if="selected">
            <h3 class="selection-name">
              <component :is="icon(selected)" class="entry-icon" aria-hidden="true" />
              {{ selected.name }}
            </h3>
            <slot v-if="!selected.directory" name="preview" :entry="selected" />
            <dl>
              <dt>类型</dt>
              <dd>{{ entryType(selected) }}</dd>
              <dt v-if="!selected.directory">
                大小
              </dt>
              <dd v-if="!selected.directory">
                {{ formatFileSize(selected.size) }}
              </dd>
              <dt>修改时间</dt>
              <dd>{{ formatDate(selected.lastModified) }}</dd>
              <slot name="metadata" :entry="selected" />
            </dl>
            <div class="entry-location">
              <span>{{ locationLabel }}</span>
              <code>{{ location(selected) }}</code>
            </div>
            <div class="details-actions">
              <slot name="actions" :entry="selected" />
              <el-button :icon="Copy" @click="copyLocation(selected)">
                复制路径
              </el-button>
            </div>
          </template>
          <p v-else class="details-hint">
            选择文件或目录查看详情
          </p>
        </aside>
      </div>

      <div class="browser-footer">
        <div class="page-statistics" role="status">
          <strong>{{ pageLabel }}</strong>
          <span v-if="keyword">匹配 {{ visibleEntries.length }} / {{ entries.length }} 项</span>
          <span v-else-if="summary">{{ summary }}</span>
          <span v-if="selected" class="selection-status">已选择 1 项</span>
        </div>
        <el-button-group aria-label="分页">
          <el-button :disabled="loading || !canPreviousPage" @click="emit('previous')">
            上一页
          </el-button>
          <el-button :disabled="loading || !canNextPage" @click="emit('next')">
            下一页
          </el-button>
        </el-button-group>
      </div>
    </div>
  </section>
</template>

<style scoped>
.storage-browser {
  min-width: 0;
  color: var(--mrr-card-foreground);
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-xl);
}

.source-bar,
.source-identity,
.source-actions,
.navigation-bar,
.location-bar,
.listing-toolbar,
.listing-controls,
.browser-notice,
.browser-footer,
.page-statistics,
.details-actions,
.selection-name {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.source-bar,
.listing-toolbar,
.browser-notice,
.browser-footer {
  justify-content: space-between;
}

.source-bar {
  min-height: 56px;
  padding: 10px 16px;
  background: var(--el-fill-color-lighter);
  border-bottom: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-xl) var(--mrr-radius-xl) 0 0;
}

.source-identity {
  flex-wrap: wrap;
  overflow-wrap: anywhere;
}

.source-actions {
  flex: none;
}

.maximize-anchor {
  display: inline-flex;
}

.source-info {
  display: inline-flex;
  padding: 4px;
  color: var(--mrr-muted-foreground);
  cursor: help;
  background: none;
  border: 0;
}

.source-info:focus-visible {
  outline: 2px solid var(--mrr-ring);
}

.browser-body {
  display: grid;
  gap: 12px;
  min-width: 0;
  padding: 16px;
}

.location-bar {
  flex: 1;
}

.location-bar > .el-input {
  flex: 1;
  min-width: 0;
}

.location-bar > .el-button-group,
.listing-controls > .el-button-group,
.browser-footer > .el-button-group {
  display: inline-flex;
  flex: none;
}

.breadcrumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 6px;
  align-items: center;
  min-width: 0;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.breadcrumbs [aria-current] {
  font-weight: 600;
}

.crumb-divider {
  flex: none;
  width: 14px;
  height: 14px;
}

.crumb-divider,
.details-hint,
.page-statistics span,
.entry-location > span,
.details-pane dt {
  color: var(--mrr-muted-foreground);
}

.crumb-button,
.file-tile,
.entry-name,
.sort-button {
  padding: 0;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: none;
  border: 0;
}

.crumb-button {
  color: var(--mrr-primary);
  overflow-wrap: anywhere;
}

.crumb-button:hover {
  text-decoration: underline;
}

.crumb-button:focus-visible,
.file-tile:focus-visible,
.file-pane:focus-visible,
.entry-name:focus-visible,
.sort-button:focus-visible {
  outline: 2px solid var(--mrr-ring);
  outline-offset: -2px;
}

.page-filter {
  width: 214px;
}

.browser-notice {
  padding: 8px 12px;
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-7);
  border-radius: var(--mrr-radius-md);
}

.explorer-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 28%);
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-lg);
}

.file-pane {
  min-width: 0;
  min-height: 340px;
}

.empty-pane {
  display: grid;
  place-items: center;
  min-height: 260px;
}

.file-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(106px, 1fr));
  gap: 4px;
  padding: 12px;
}

.file-tile {
  display: grid;
  gap: 8px;
  align-content: start;
  justify-items: center;
  min-width: 0;
  min-height: 86px;
  padding: 10px 8px;
  text-align: center;
  border: 1px solid transparent;
  border-radius: var(--mrr-radius-md);
}

.file-tile:hover,
.file-table tbody tr:hover {
  background: var(--el-fill-color-light);
}

.file-tile.selected,
.file-table tbody tr.selected {
  background: var(--el-color-primary-light-9);
}

.file-tile.selected {
  border-color: var(--el-color-primary-light-5);
}

.file-tile-name,
.entry-name span {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.entry-icon {
  flex: none;
  width: 20px;
  height: 20px;
  color: var(--mrr-muted-foreground);
  fill: var(--el-fill-color);
  stroke-width: 1.5;
}

.file-tile .entry-icon {
  width: 38px;
  height: 38px;
}

.entry-icon.lucide-folder {
  color: var(--el-color-warning-dark-2);
  fill: var(--el-color-warning-light-5);
}

.entry-icon.lucide-file-image {
  color: var(--el-color-primary-dark-2);
  fill: var(--el-color-primary-light-8);
}

.entry-icon.lucide-file-archive {
  color: var(--el-color-success-dark-2);
  fill: var(--el-color-success-light-8);
}

.entry-icon.lucide-file-text {
  fill: var(--el-fill-color-light);
}

.file-table {
  width: 100%;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  table-layout: fixed;
  border-spacing: 0;
  border-collapse: separate;
}

.file-table th,
.file-table td {
  padding: 0 12px;
  text-align: left;
}

.file-table th {
  padding: 0;
  color: var(--mrr-muted-foreground);
  background: var(--el-fill-color-lighter);
  border-bottom: 1px solid var(--mrr-border);
}

.sort-button {
  display: flex;
  gap: 4px;
  align-items: center;
  width: 100%;
  min-height: 36px;
  padding: 0 12px;
  white-space: nowrap;
}

.sort-button:hover {
  background: var(--el-fill-color-light);
}

.sort-indicator {
  flex: none;
  width: 14px;
  height: 14px;
  color: var(--mrr-primary);
}

.file-table tbody tr {
  cursor: pointer;
}

.file-table th:nth-child(2),
.file-table th:nth-child(3) {
  width: 74px;
}

.file-table th:last-child {
  width: 152px;
}

.entry-name {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
  min-height: 36px;
}

.details-pane {
  min-width: 0;
  padding: 14px;
  background: var(--el-fill-color-lighter);
  border-left: 1px solid var(--mrr-border);
}

.details-heading {
  font-size: 13px;
  font-weight: 600;
}

.details-hint {
  margin: 12px 0 0;
  font-size: 13px;
}

.selection-name {
  margin: 12px 0;
  font-size: 15px;
  overflow-wrap: anywhere;
}

.selection-name .entry-icon {
  width: 28px;
  height: 28px;
}

.details-pane dl {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 8px 12px;
  margin: 0;
  font-size: 13px;
}

.details-pane :deep(dd) {
  margin: 0;
  overflow-wrap: anywhere;
}

.entry-location {
  display: grid;
  gap: 4px;
  margin-top: 12px;
  font-size: 13px;
}

.entry-location code {
  overflow-wrap: anywhere;
  user-select: text;
}

.details-actions {
  flex-wrap: wrap;
  margin-top: 12px;
}

.details-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.browser-footer,
.page-statistics {
  flex-wrap: wrap;
  font-size: 13px;
}

.selection-status {
  padding-left: 8px;
  border-left: 1px solid var(--mrr-border);
}

/*
 * 占满内容区：脱离文档流铺满视口右侧（坐标由脚本测量后写成行内样式），
 * 顶边贴视口顶部，把站点头部与工具栏一并盖住；文件区与详情区改为在盒内滚动、
 * 表头吸顶。铺满后不再有卡片感，边框与圆角归零。
 */
.storage-browser--maximized {
  position: fixed;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 0;
  border-radius: 0;
}

.storage-browser--maximized .source-bar {
  border-radius: 0;
}

.storage-browser--maximized .source-bar,
.storage-browser--maximized .browser-footer {
  flex: none;
}

.storage-browser--maximized .browser-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.storage-browser--maximized .explorer-layout {
  flex: 1;
  grid-template-rows: minmax(0, 1fr);
  min-height: 0;
}

.storage-browser--maximized .file-pane,
.storage-browser--maximized .details-pane {
  min-height: 0;
  overflow: auto;
}

.storage-browser--maximized .file-table th {
  position: sticky;
  top: 0;
  z-index: 1;
}

@media (max-width: 1100px) {
  .explorer-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .details-pane {
    grid-row: 1;
    border-bottom: 1px solid var(--mrr-border);
    border-left: 0;
  }

  /* 单列时详情在上、列表在下：详情拿六成高度、超出部分自己滚动，
   * 列表至少留四成，两个区都不会被压没。 */
  .storage-browser--maximized .explorer-layout {
    grid-template-rows: minmax(0, 60%) minmax(0, 1fr);
  }
}

@media (max-width: 700px) {
  .browser-body {
    padding: 10px;
  }

  .listing-toolbar,
  .navigation-bar {
    flex-wrap: wrap;
  }

  .location-bar {
    flex-basis: 100%;
  }

  .page-filter {
    width: 100%;
    min-width: 0;
  }

  .column-type,
  .column-modified {
    display: none;
  }
}
</style>
