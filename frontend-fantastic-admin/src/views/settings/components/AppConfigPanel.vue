<script setup lang="ts">
import type { Settings } from '#/global'
import { useId } from 'vue'
import settingsDefault from '@/settings.default'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/ui/components/FaTabs/tabs'
import { notify } from '@/utils/notification'
import { diffTwoObj } from '@/utils/object'
import { normalizeThemeColor, readableInkOn, themeColorPresets } from '@/utils/theme-color'
import { gr8rPrimary, normalizeThemePalette, themePalettePresets } from '@/utils/theme-palette'

defineOptions({ name: 'AppConfigPanel' })

const props = withDefaults(defineProps<{
  drawer?: boolean
}>(), {
  drawer: false,
})

const settingsStore = useSettingsStore()
const autoSaveState = ref<'idle' | 'saving' | 'saved'>('idle')
const activeGroup = ref<string | number>('theme')
const importInput = ref<HTMLInputElement>()
const radiusLabelId = useId()
let autoSaveTimer: ReturnType<typeof setTimeout> | undefined

const appearanceGroups = [
  { key: 'theme', label: '主题与页面样式', icon: 'i-lucide:paintbrush' },
  { key: 'navigation', label: '导航与顶栏', icon: 'i-lucide:menu' },
  { key: 'workspace', label: '工作区组件', icon: 'i-lucide:layout-dashboard' },
  { key: 'other', label: '主页、版权与其他', icon: 'i-lucide:house' },
]

/** 当前主色，统一规范化后与预设比对，避免大小写或 `#RGB` 写法导致选中态丢失。 */
const activeThemeColor = computed(() => normalizeThemeColor(settingsStore.settings.app.themeColor))
const activeThemePalette = computed(() => normalizeThemePalette(settingsStore.settings.app.themePalette))
const gr8rAccent = computed(() => gr8rPrimary(settingsStore.currentColorScheme ?? 'light'))

/** 预设色块的墨色随底色深浅自动切换，自定义成亮黄/近白时对勾仍可见。 */
function swatchInk(color: string): string {
  return readableInkOn(color)
}

const navigationModes = [
  { value: 'side', label: '双层侧栏' },
  { value: 'head', label: '顶部导航' },
  { value: 'single', label: '单侧栏' },
] as const

const radiusShapes = [
  { value: 'round', label: '标准圆弧' },
  { value: 'squircle', label: 'G2 连续曲线' },
] as const

/** 超椭圆圆角依赖 CSS corner-shape（Chrome/Edge 139+），不支持时保留圆弧。 */
const supportsCornerShape = typeof CSS !== 'undefined'
  && typeof CSS.supports === 'function'
  && CSS.supports('corner-shape', 'squircle')

const appRadius = computed<number[]>({
  get() {
    return [settingsStore.settings.app.radius]
  },
  set(value) {
    settingsStore.settings.app.radius = value[0]
  },
})

/** 旧版本地配置可能没有该字段，统一按标准圆弧处理。 */
const radiusShape = computed<'round' | 'squircle'>(() => settingsStore.settings.app.radiusShape === 'squircle' ? 'squircle' : 'round')

watch(() => settingsStore.settings, () => {
  autoSaveState.value = 'saving'
  if (autoSaveTimer) {
    clearTimeout(autoSaveTimer)
  }
  autoSaveTimer = setTimeout(() => {
    settingsStore.saveAppSettings()
    autoSaveState.value = 'saved'
  }, 350)
}, { deep: true })

const autoSaveLabel = computed(() => {
  if (autoSaveState.value === 'saving') {
    return '正在自动保存…'
  }
  switch (settingsStore.uiSettingsSyncState) {
    case 'failed':
      return '已保存到本机，同步全站失败'
    case 'local-only':
      return '已保存到本机（当前账号无全站同步权限）'
    case 'synced':
      return '已自动保存并同步到全站'
    default:
      return autoSaveState.value === 'saved' ? '已自动保存' : '修改后自动保存'
  }
})

/** 生效范围说明：与自动保存标签一起，避免向无权限的账号承诺「同步到全站」。 */
const syncScopeDescription = computed(() => {
  switch (settingsStore.uiSettingsSyncState) {
    case 'local-only':
      return '这些选项保存在本机浏览器。当前账号缺少系统管理权限，修改不会同步到全站。'
    case 'failed':
      return '这些选项已保存在本机浏览器，但写入系统数据库失败，尚未同步到全站。'
    default:
      return '这些选项同时保存在本机并同步到系统数据库，作为全站默认外观。'
  }
})

function openGroup(group: string) {
  activeGroup.value = group
}

defineExpose({ openGroup })

onBeforeUnmount(() => {
  if (autoSaveTimer) {
    clearTimeout(autoSaveTimer)
  }
})

function handleExport() {
  const data = JSON.stringify(diffTwoObj(settingsDefault, settingsStore.settings), null, 2)
  const url = URL.createObjectURL(new Blob([data], { type: 'application/json' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `mrr-interface-config-${new Date().toISOString().slice(0, 10)}.json`
  link.click()
  URL.revokeObjectURL(url)
  notify.success('界面外观配置已导出')
}

function triggerImport() {
  importInput.value?.click()
}

async function handleImport(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }

  try {
    const data: unknown = JSON.parse(await file.text())
    if (!data || typeof data !== 'object' || Array.isArray(data)) {
      throw new TypeError('配置文件必须是 JSON 对象')
    }
    settingsStore.updateSettings(data as Settings.all)
    notify.success('界面外观配置已导入')
  }
  catch {
    notify.error('导入失败，请选择有效的配置 JSON 文件')
  }
  finally {
    input.value = ''
  }
}

function handleReset() {
  settingsStore.resetAppSettings()
  notify.success('已恢复默认应用配置')
}
</script>

<template>
  <div class="app-config-panel" :class="{ 'app-config-panel--drawer': props.drawer }">
    <div class="config-summary" :class="{ 'config-summary--warning': settingsStore.uiSettingsSyncState === 'failed' }">
      <div class="config-save-feedback" role="status">
        <strong class="autosave-state">
          <FaIcon :name="settingsStore.uiSettingsSyncState === 'failed' ? 'i-lucide:triangle-alert' : 'i-lucide:save'" aria-hidden="true" />
          {{ autoSaveLabel }}
        </strong>
        <p>{{ syncScopeDescription }}</p>
      </div>
      <div class="config-summary-actions">
        <el-button @click="handleReset">
          <FaIcon name="i-lucide:rotate-cw" />
          恢复默认
        </el-button>
        <el-button @click="handleExport">
          <FaIcon name="i-lucide:download" />
          导出配置
        </el-button>
        <el-button @click="triggerImport">
          <FaIcon name="i-lucide:upload" />
          导入配置
        </el-button>
      </div>
    </div>
    <input ref="importInput" class="import-input" type="file" accept="application/json,.json" @change="handleImport">

    <Tabs v-model="activeGroup">
      <TabsList class="appearance-tabs" aria-label="外观设置分类">
        <TabsTrigger
          v-for="group in appearanceGroups"
          :key="group.key"
          :value="group.key"
          :data-settings-search-target="group.key"
          class="appearance-tab"
        >
          <span class="appearance-tab-label">
            <FaIcon :name="group.icon" aria-hidden="true" />
            {{ group.label }}
          </span>
        </TabsTrigger>
      </TabsList>
      <TabsContent value="theme" class="appearance-tab-content">
        <div class="config-section">
          <div class="setting-item setting-item--stack">
            <div class="label-copy">
              <strong>整体配色方案</strong>
              <small>选择整套页面、侧栏、卡片和状态色；暖色扁平风格还会一并调整控件高度、圆角与描边层次。不改变布局结构与业务功能。</small>
            </div>
            <div class="theme-palette-options" role="group" aria-label="整体配色方案">
              <button
                v-for="palette in themePalettePresets"
                :key="palette.id"
                type="button"
                class="theme-palette-option"
                :aria-pressed="activeThemePalette === palette.id"
                @click="settingsStore.settings.app.themePalette = palette.id"
              >
                <span
                  class="theme-palette-preview"
                  aria-hidden="true"
                  :style="{ backgroundColor: palette.preview.canvas, borderColor: palette.preview.line }"
                >
                  <span class="theme-palette-preview__sidebar" :style="{ backgroundColor: palette.preview.sidebar, borderColor: palette.preview.line }">
                    <span :style="{ backgroundColor: palette.preview.ink, opacity: 0.5 }" />
                    <span :style="{ backgroundColor: palette.preview.ink, opacity: 0.25 }" />
                    <span :style="{ backgroundColor: palette.preview.accent, opacity: 0.75 }" />
                  </span>
                  <span class="theme-palette-preview__panel" :style="{ backgroundColor: palette.preview.surface, borderColor: palette.preview.line }">
                    <span class="theme-palette-preview__heading" :style="{ backgroundColor: palette.preview.ink }" />
                    <span class="theme-palette-preview__line" :style="{ backgroundColor: palette.preview.ink, opacity: 0.2 }" />
                    <span class="theme-palette-preview__line theme-palette-preview__line--short" :style="{ backgroundColor: palette.preview.ink, opacity: 0.2 }" />
                    <span class="theme-palette-preview__action" :style="{ backgroundColor: palette.preview.accent }" />
                  </span>
                </span>
                <span class="theme-palette-option__name">
                  <strong>{{ palette.name }}</strong>
                  <FaIcon v-if="activeThemePalette === palette.id" name="i-lucide:check-circle-2" aria-hidden="true" />
                </span>
                <small>{{ palette.description }}</small>
              </button>
            </div>
            <!-- 主题不只换色：把同时变化的结构项说清楚，避免用户以为是页面「变紧/变松」出了问题。 -->
            <p v-if="activeThemePalette === 'gr8r'" class="theme-palette-note">
              当前主题同时调整：控件高度 30px、表格行高 40px、卡片改为 1px 描边且去投影、圆角上限 10px。
            </p>
          </div>
          <div class="setting-item setting-item--stack">
            <div class="label-copy">
              <strong>颜色主题</strong>
              <small>可固定明亮、暗黑模式，或跟随操作系统。</small>
            </div>
            <FaTabs
              v-model="settingsStore.settings.app.colorScheme"
              :list="[
                { icon: 'i-lucide:sun', label: '明亮', value: 'light' },
                { icon: 'i-lucide:moon', label: '暗黑', value: 'dark' },
                { icon: 'i-lucide:sun-moon', label: '跟随系统', value: '' },
              ]"
              class="theme-tabs"
            />
          </div>

          <div class="setting-item">
            <div class="label-copy">
              <strong>主题主色</strong>
              <small>{{ activeThemePalette === 'gr8r' ? '暖色扁平风格使用固定配套主色，随明暗模式自动调整；切回 MRR 经典可自由选色。' : '菜单、主要按钮和选中状态会同步使用此颜色。' }}</small>
            </div>
            <div v-if="activeThemePalette === 'mrr'" class="theme-colors" role="group" aria-label="主题主色">
              <button
                v-for="preset in themeColorPresets"
                :key="preset.color"
                type="button"
                class="theme-swatch"
                :style="{ '--swatch-color': preset.color, '--swatch-ink': swatchInk(preset.color) }"
                :title="preset.label"
                :aria-label="preset.label"
                :aria-pressed="activeThemeColor === preset.color"
                @click="settingsStore.settings.app.themeColor = preset.color"
              >
                <FaIcon v-if="activeThemeColor === preset.color" name="i-lucide:check" aria-hidden="true" />
              </button>
              <div class="custom-color">
                <el-color-picker
                  v-model="settingsStore.settings.app.themeColor"
                  color-format="hex"
                  aria-label="自定义主题主色"
                />
                <span>自定义</span>
              </div>
            </div>
            <div v-else class="theme-palette-accent" aria-label="暖色扁平风格当前主色">
              <span class="theme-palette-accent__swatch" :style="{ backgroundColor: gr8rAccent }" aria-hidden="true" />
              <span class="theme-palette-accent__copy">
                <strong>{{ gr8rAccent }}</strong>
                <small>原 MRR 主色设置已保留</small>
              </span>
              <button type="button" class="theme-palette-accent__reset" @click="settingsStore.settings.app.themePalette = 'mrr'">
                自定义主色
              </button>
            </div>
          </div>

          <div class="setting-item">
            <div class="label-copy">
              <strong :id="radiusLabelId">圆角系数</strong>
              <small>{{ activeThemePalette === 'gr8r' ? '统一调整页面卡片与面板的圆角程度；暖色扁平风格会把上限收敛到 10px。' : '统一调整页面卡片与面板的圆角程度。' }}</small>
            </div>
            <div class="radius-control">
              <FaSlider v-model="appRadius" :aria-labelledby="radiusLabelId" :min="0" :max="1" :step="0.25" class="slider-field" />
              <span class="radius-value">{{ settingsStore.settings.app.radius }}</span>
            </div>
          </div>

          <div class="setting-item setting-item--stack">
            <div class="label-copy">
              <strong>圆角曲线</strong>
              <small>G2 连续曲线的曲率平滑过渡，观感接近 iOS 图标；标准圆弧为等半径圆角。差异随圆角系数增大而更明显。开关、滑块与圆形图标不属于圆角矩形，两种模式下都保持标准圆弧。</small>
            </div>
            <div class="corner-options" role="group" aria-label="圆角曲线">
              <button
                v-for="shape in radiusShapes"
                :key="shape.value"
                type="button"
                class="corner-option"
                :aria-pressed="radiusShape === shape.value"
                @click="settingsStore.settings.app.radiusShape = shape.value"
              >
                <span class="corner-preview" :class="`corner-preview--${shape.value}`" aria-hidden="true" />
                {{ shape.label }}
              </button>
            </div>
            <p v-if="!supportsCornerShape" class="corner-hint">
              <FaIcon name="i-lucide:info" aria-hidden="true" />
              当前浏览器未实现 CSS corner-shape，本机仍按标准圆弧绘制；设置会保存并对支持的浏览器生效。
            </p>
          </div>

          <div class="setting-item setting-item--stack">
            <div class="label-copy">
              <strong>页面标题风格</strong>
              <small>页面同级更简洁，卡片风格层次更突出。</small>
            </div>
            <FaTabs
              v-model="settingsStore.settings.app.pageTitleStyle"
              :list="[
                { label: '页面同级', value: 'plain' },
                { label: '卡片', value: 'card' },
              ]"
              class="title-style-tabs"
            />
          </div>

          <div class="setting-item">
            <div class="label-copy">
              <strong>动态效果</strong>
              <small>默认启用界面过渡和动效。关闭后会停用动态效果，仅保存在当前浏览器。</small>
            </div>
            <FaSwitch v-model="settingsStore.settings.app.enableMotion" aria-label="动态效果" />
          </div>
        </div>
      </TabsContent>

      <TabsContent value="navigation" class="appearance-tab-content">
        <div class="config-section">
          <div v-if="settingsStore.mode === 'pc'" class="setting-item setting-item--stack">
            <div class="label-copy">
              <strong>导航栏模式</strong>
              <small>选择适合当前工作区的菜单布局。</small>
            </div>
            <div class="menu-mode" role="group" aria-label="导航栏模式">
              <button
                v-for="item in navigationModes"
                :key="item.value"
                type="button"
                class="mode-option"
                :aria-pressed="settingsStore.settings.menu.mode === item.value"
                @click="settingsStore.settings.menu.mode = item.value"
              >
                <span class="mode" :class="`mode-${item.value}`" aria-hidden="true">
                  <span class="mode-container" />
                </span>
                {{ item.label }}
              </button>
            </div>
          </div>

          <div class="setting-item">
            <div class="label-copy">
              <strong>主导航点击模式</strong>
              <small>智能选择会根据可访问菜单数量自动判断。</small>
            </div>
            <div class="button-options">
              <FaButton
                v-for="(item, index) in [
                  { label: '切换', value: 'switch' },
                  { label: '跳转', value: 'jump' },
                  { label: '智能选择', value: 'smart' },
                ]"
                :key="index"
                :variant="settingsStore.settings.menu.mainMenuClickMode === item.value ? 'default' : 'outline'"
                :aria-pressed="settingsStore.settings.menu.mainMenuClickMode === item.value"
                size="sm"
                @click="settingsStore.settings.menu.mainMenuClickMode = (item.value as any)"
              >
                {{ item.label }}
              </FaButton>
            </div>
          </div>

          <div class="setting-item">
            <div class="label-copy">
              <strong>次导航只展开一项</strong>
              <small>展开新菜单时自动收起其他菜单。</small>
            </div>
            <FaSwitch v-model="settingsStore.settings.menu.subMenuUniqueOpened" />
          </div>

          <div class="setting-item">
            <div class="label-copy">
              <strong>默认折叠次导航</strong>
              <small>进入系统时以紧凑状态显示次导航。</small>
            </div>
            <FaSwitch v-model="settingsStore.settings.menu.subMenuCollapse" />
          </div>

          <div v-if="settingsStore.mode === 'pc'" class="setting-item">
            <div class="label-copy">
              <strong>显示折叠按钮</strong>
              <small>允许用户手动展开或折叠次导航。</small>
            </div>
            <FaSwitch v-model="settingsStore.settings.menu.enableSubMenuCollapseButton" />
          </div>

          <div class="setting-item">
            <div class="label-copy">
              <strong>导航快捷键</strong>
              <small>单栏导航模式下不可用。</small>
            </div>
            <FaSwitch v-model="settingsStore.settings.menu.enableHotkeys" :disabled="['single'].includes(settingsStore.settings.menu.mode)" />
          </div>

          <div class="setting-item">
            <div class="label-copy">
              <strong>顶栏定位</strong>
              <small>控制滚动页面时顶栏的显示方式。</small>
            </div>
            <div class="button-options">
              <FaButton
                v-for="(item, index) in [
                  { label: '静止', value: 'static' },
                  { label: '固定', value: 'fixed' },
                  { label: '粘性', value: 'sticky' },
                ]"
                :key="index"
                :variant="settingsStore.settings.topbar.mode === item.value ? 'default' : 'outline'"
                :aria-pressed="settingsStore.settings.topbar.mode === item.value"
                size="sm"
                @click="settingsStore.settings.topbar.mode = (item.value as any)"
              >
                {{ item.label }}
              </FaButton>
            </div>
          </div>
        </div>
      </TabsContent>

      <TabsContent value="workspace" class="appearance-tab-content">
        <div class="config-subsection">
          <div class="subsection-heading">
            <strong>标签栏</strong>
            <small>管理已打开页面及其快捷操作。</small>
          </div>
          <div class="config-section config-section--nested">
            <div class="setting-item">
              <div class="label-copy">
                <strong>启用标签栏</strong>
              </div>
              <FaSwitch v-model="settingsStore.settings.tabbar.enable" />
            </div>
            <div class="setting-item">
              <div class="label-copy">
                <strong>显示页面图标</strong>
              </div>
              <FaSwitch v-model="settingsStore.settings.tabbar.enableIcon" :disabled="!settingsStore.settings.tabbar.enable" />
            </div>
            <div class="setting-item">
              <div class="label-copy">
                <strong>标签栏快捷键</strong>
              </div>
              <FaSwitch v-model="settingsStore.settings.tabbar.enableHotkeys" :disabled="!settingsStore.settings.tabbar.enable" />
            </div>
          </div>
        </div>

        <div class="config-subsection">
          <div class="subsection-heading">
            <strong>工具栏</strong>
            <small>控制页面顶部常用工具的显示。</small>
          </div>
          <div class="config-section config-section--nested">
            <div class="setting-item">
              <div class="label-copy">
                <strong>启用工具栏</strong>
              </div>
              <FaSwitch v-model="settingsStore.settings.toolbar.enable" />
            </div>
            <div v-if="settingsStore.mode === 'pc'" class="setting-item">
              <div class="label-copy">
                <strong>面包屑导航</strong>
              </div>
              <FaSwitch v-model="settingsStore.settings.toolbar.breadcrumb" :disabled="!settingsStore.settings.toolbar.enable" />
            </div>
            <div class="setting-item">
              <div class="label-copy">
                <strong>导航搜索</strong>
              </div>
              <FaSwitch v-model="settingsStore.settings.toolbar.navSearch" :disabled="!settingsStore.settings.toolbar.enable" />
            </div>
            <div v-if="settingsStore.mode === 'pc'" class="setting-item">
              <div class="label-copy">
                <strong>全屏入口</strong>
              </div>
              <FaSwitch v-model="settingsStore.settings.toolbar.fullscreen" :disabled="!settingsStore.settings.toolbar.enable" />
            </div>
            <div class="setting-item">
              <div class="label-copy">
                <strong>页面刷新入口</strong>
              </div>
              <FaSwitch v-model="settingsStore.settings.toolbar.pageReload" :disabled="!settingsStore.settings.toolbar.enable" />
            </div>
            <div class="setting-item">
              <div class="label-copy">
                <strong>主题切换入口</strong>
              </div>
              <FaSwitch v-model="settingsStore.settings.toolbar.colorScheme" :disabled="!settingsStore.settings.toolbar.enable" />
            </div>
          </div>
        </div>

        <div class="config-subsection">
          <div class="subsection-heading">
            <strong>快捷键</strong>
            <small>控制主页和导航搜索的键盘操作。</small>
          </div>
          <div class="config-section config-section--nested">
            <div class="setting-item">
              <div class="label-copy">
                <strong>主页快捷键</strong>
              </div>
              <FaSwitch v-model="settingsStore.settings.mainPage.enableHotkeys" :disabled="!settingsStore.settings.toolbar.enable" />
            </div>
            <div class="setting-item">
              <div class="label-copy">
                <strong>导航搜索快捷键</strong>
              </div>
              <FaSwitch v-model="settingsStore.settings.navSearch.enableHotkeys" :disabled="!settingsStore.settings.toolbar.navSearch" />
            </div>
          </div>
        </div>
      </TabsContent>

      <TabsContent value="other" class="appearance-tab-content">
        <div class="config-subsection">
          <div class="subsection-heading">
            <strong>主页</strong>
            <small>控制登录后的默认入口与名称。</small>
          </div>
          <div class="config-section config-section--nested">
            <div class="setting-item">
              <div class="label-copy">
                <strong>启用主页</strong>
              </div>
              <FaSwitch v-model="settingsStore.settings.home.enable" />
            </div>
            <div class="setting-item">
              <div class="label-copy">
                <strong>主页名称</strong>
              </div>
              <FaInput v-model="settingsStore.settings.home.title" />
            </div>
          </div>
        </div>

        <div class="config-subsection">
          <div class="subsection-heading">
            <strong>底部版权</strong>
            <small>设置页脚展示的版权与备案信息。</small>
          </div>
          <div class="config-section config-section--nested">
            <div class="setting-item">
              <div class="label-copy">
                <strong>启用底部版权</strong>
              </div>
              <FaSwitch v-model="settingsStore.settings.copyright.enable" />
            </div>
            <div class="setting-item">
              <div class="label-copy">
                <strong>日期</strong>
              </div>
              <FaInput v-model="settingsStore.settings.copyright.dates" :disabled="!settingsStore.settings.copyright.enable" />
            </div>
            <div class="setting-item">
              <div class="label-copy">
                <strong>公司</strong>
              </div>
              <FaInput v-model="settingsStore.settings.copyright.company" :disabled="!settingsStore.settings.copyright.enable" />
            </div>
            <div class="setting-item">
              <div class="label-copy">
                <strong>网址</strong>
              </div>
              <FaInput v-model="settingsStore.settings.copyright.website" :disabled="!settingsStore.settings.copyright.enable" />
            </div>
            <div class="setting-item">
              <div class="label-copy">
                <strong>备案号</strong>
              </div>
              <FaInput v-model="settingsStore.settings.copyright.beian" :disabled="!settingsStore.settings.copyright.enable" />
            </div>
          </div>
        </div>

        <div class="config-subsection">
          <div class="subsection-heading">
            <strong>其他功能</strong>
            <small>权限、载入反馈和辅助显示选项。</small>
          </div>
          <div class="config-section config-section--nested">
            <div class="setting-item">
              <div class="label-copy">
                <strong>启用权限控制</strong>
              </div>
              <FaSwitch v-model="settingsStore.settings.app.enablePermission" />
            </div>
            <div class="setting-item">
              <div class="label-copy">
                <strong>显示载入进度条</strong>
              </div>
              <FaSwitch v-model="settingsStore.settings.app.enableProgress" />
            </div>
            <div class="setting-item">
              <div class="label-copy">
                <strong>色弱模式</strong>
              </div>
              <FaSwitch v-model="settingsStore.settings.app.enableColorAmblyopiaMode" />
            </div>
            <div class="setting-item">
              <div class="label-copy">
                <strong>动态页面标题</strong>
              </div>
              <FaSwitch v-model="settingsStore.settings.app.enableDynamicTitle" />
            </div>
          </div>
        </div>
      </TabsContent>
    </Tabs>
  </div>
</template>

<style scoped>
.app-config-panel {
  max-width: 1080px;
}

.app-config-panel--drawer {
  max-width: none;
}

.app-config-panel--drawer .config-summary {
  flex-direction: column;
  align-items: stretch;
}

.app-config-panel--drawer .config-summary-actions {
  flex-wrap: wrap;
  justify-content: flex-start;
}

.app-config-panel--drawer .config-save-feedback {
  flex-basis: auto;
}

.config-summary {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-3);
  align-items: center;
  justify-content: space-between;
  padding: var(--mrr-space-3) var(--mrr-space-4);
  margin-bottom: var(--mrr-space-4);
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-card);
}

.config-save-feedback {
  flex: 1 1 240px;
  min-width: 0;
}

.config-save-feedback p {
  margin: var(--mrr-space-1) 0 0;
  font-size: 12px;
  color: var(--mrr-muted-foreground);
}

.autosave-state {
  display: inline-flex;
  gap: var(--mrr-space-2);
  align-items: center;
  font-size: 13px;
  font-weight: 650;
}

.config-summary--warning .autosave-state { color: var(--mrr-destructive); }

.config-summary-actions {
  display: flex;
  flex: 0 1 auto;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
  justify-content: flex-end;
  max-width: 100%;
}

.config-summary-actions :deep(.el-button) {
  gap: 6px;
  margin-left: 0;
}

.import-input { display: none; }

.appearance-tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--mrr-space-1);
  width: 100%;
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-card);
}

.app-config-panel--drawer .appearance-tabs {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.appearance-tab {
  min-width: 0;
  min-height: 38px;
  padding-inline: var(--mrr-space-2);
  border: 1px solid transparent;
  border-radius: var(--mrr-radius-md);
  transition: background-color var(--mrr-motion-feedback) ease, border-color var(--mrr-motion-feedback) ease;
}

.appearance-tab[data-state="active"] {
  font-weight: 600;
  color: var(--mrr-navigation-active-foreground, var(--mrr-primary));
  background: var(--mrr-card);
  border-color: var(--mrr-border);
}

.appearance-tab :deep(.truncate) { white-space: normal; }

.appearance-tab-label {
  display: flex;
  gap: 6px;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  line-height: 1.35;
}

.appearance-tab-label :deep(svg) { flex-shrink: 0; }
.appearance-tab-content { margin-top: var(--mrr-space-4); }

.config-section {
  overflow: hidden;
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-card);
}

.config-section--nested {
  border-radius: var(--mrr-radius-md);
}

.setting-item {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-3) var(--mrr-space-5);
  align-items: center;
  justify-content: space-between;
  min-height: 66px;
  padding: 12px 14px;
}

.setting-item + .setting-item {
  border-top: 1px solid var(--mrr-border);
}

.setting-item--stack {
  align-items: flex-start;
}

.label-copy {
  flex: 1 1 180px;
  min-width: 0;
  max-width: 420px;
}

.label-copy strong,
.label-copy small {
  display: block;
}

.label-copy strong {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.label-copy small {
  margin-top: 4px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.theme-tabs {
  width: 240px;
}

/* 配色预览始终显示方案自身的真实色值，而非当前主题的颜色。 */
.theme-palette-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--mrr-space-3);
  width: 100%;
}

.theme-palette-option {
  display: grid;
  gap: var(--mrr-space-2);
  min-width: 0;
  padding: 12px;
  text-align: start;
  cursor: pointer;
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-card);
  transition: border-color var(--mrr-motion-feedback) ease, box-shadow var(--mrr-motion-feedback) ease;
}

.theme-palette-option[aria-pressed="true"] {
  border-color: var(--mrr-primary);
  box-shadow: inset 0 0 0 1px var(--mrr-primary);
}

.theme-palette-option:focus-visible,
.theme-palette-accent__reset:focus-visible {
  outline: 2px solid var(--mrr-navigation-active-foreground, var(--mrr-primary));
  outline-offset: 2px;
}

@media (hover: hover) {
  .theme-palette-option:not([aria-pressed="true"]):hover {
    border-color: var(--mrr-control-border-hover);
  }
}

.theme-palette-preview {
  display: flex;
  gap: 8px;
  width: 100%;
  height: 84px;
  padding: 8px;
  overflow: hidden;
  border: 1px solid;
  border-radius: var(--mrr-radius-md);
}

.theme-palette-preview__sidebar {
  display: grid;
  flex: 0 0 24%;
  gap: 7px;
  align-content: start;
  padding: 7px;
  border-right: 1px solid;
  border-radius: 4px;
}

.theme-palette-preview__sidebar > span {
  display: block;
  width: 100%;
  height: 5px;
  border-radius: 2px;
}

.theme-palette-preview__panel {
  display: grid;
  flex: 1;
  gap: 7px;
  align-content: start;
  min-width: 0;
  padding: 9px;
  border: 1px solid;
  border-radius: 4px;
}

.theme-palette-preview__heading {
  width: 66%;
  height: 7px;
  border-radius: 2px;
}

.theme-palette-preview__line {
  width: 92%;
  height: 5px;
  border-radius: 2px;
}

.theme-palette-preview__line--short {
  width: 70%;
}

.theme-palette-preview__action {
  width: 39%;
  height: 9px;
  border-radius: 3px;
}

.theme-palette-option__name {
  display: flex;
  gap: var(--mrr-space-2);
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--mrr-foreground);
}

.theme-palette-option__name :deep(svg) {
  color: var(--mrr-primary);
}

.theme-palette-option > small {
  font-size: 11px;
  line-height: 1.5;
  color: var(--mrr-muted-foreground);
}

/* 主题附带的结构变化说明：弱化处理，不与选项卡片争夺注意力。 */
.theme-palette-note {
  margin: 0;
  font-size: 11px;
  line-height: 1.6;
  color: var(--mrr-muted-foreground);
}

.theme-palette-accent {
  display: flex;
  gap: var(--mrr-space-2);
  align-items: center;
  min-width: 0;
}

.theme-palette-accent__swatch {
  display: block;
  flex: 0 0 32px;
  height: 32px;
  border: 2px solid var(--mrr-card);
  border-radius: 50%;
  box-shadow: 0 0 0 1px var(--mrr-border);
}

.theme-palette-accent__copy {
  display: grid;
  gap: 2px;
  font-size: 12px;
}

.theme-palette-accent__copy small {
  font-size: 11px;
  color: var(--mrr-muted-foreground);
}

.theme-palette-accent__reset {
  padding: 7px 9px;
  margin-inline-start: auto;
  font-size: 12px;
  color: var(--mrr-navigation-active-foreground);
  cursor: pointer;
  background: var(--mrr-muted);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
}

.title-style-tabs {
  width: min(360px, 55%);
}

.radius-control {
  display: flex;
  gap: var(--mrr-space-3);
  align-items: center;
  width: min(360px, 100%);
}

.slider-field { flex: 1; min-width: 0; }
.radius-value { min-width: 4ch; font-size: 12px; font-variant-numeric: tabular-nums; text-align: right; }

.corner-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--mrr-space-2);
  width: min(320px, 100%);
}

.corner-option {
  display: grid;
  gap: var(--mrr-space-1);
  place-items: center;
  min-width: 0;
  padding: var(--mrr-space-2);
  font-size: 12px;
  white-space: nowrap;
  cursor: pointer;
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
  transition: border-color var(--mrr-motion-feedback) ease;
}

.corner-option:hover,
.corner-option[aria-pressed="true"] { border-color: var(--mrr-primary); }
.corner-option[aria-pressed="true"] { color: var(--mrr-navigation-active-foreground, var(--mrr-primary)); }
.corner-option:focus-visible { outline: 2px solid var(--mrr-navigation-active-foreground, var(--mrr-primary)); outline-offset: 2px; }

/*
 * 预览块沿用实际圆角基准，切换曲线时能直接看出差别。
 * 尺寸刻意放大到 56px / 20px 半径：曲线差异只在「半径 / 尺寸」比例较大时
 * 才可感知（12px 半径的大卡片上两者相差不到 1.5px），预览块太小等于看不到。
 */
.corner-preview {
  width: 56px;
  height: 56px;
  background: var(--mrr-muted);
  border: 1px solid var(--mrr-border);
  border-radius: 20px;
}

/*
 * 两个预览块各自锁定曲线，不受全局开关影响，才能并排对比。
 * 选择器带上 .corner-option 提高特异性，压过 corner-shape.css 里
 * `:root[data-radius-shape="squircle"] *` 的默认值。
 */
@supports (corner-shape: squircle) {
  .corner-option .corner-preview--squircle { corner-shape: squircle; }
  :root[data-radius-shape="squircle"] .corner-option .corner-preview--round { corner-shape: round; }
}

.corner-hint {
  display: flex;
  flex-basis: 100%;
  gap: var(--mrr-space-1);
  align-items: center;
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--mrr-muted-foreground);
}

.theme-colors,
.custom-color {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
}

.theme-swatch {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 32px;
  height: 32px;
  font-size: 16px;
  color: var(--swatch-ink, white);
  cursor: pointer;
  background: var(--swatch-color);
  border: 2px solid var(--mrr-card);
  border-radius: 50%;
  box-shadow: 0 0 0 1px var(--mrr-border);
  transition: box-shadow 160ms ease, transform 160ms ease;
}

.theme-swatch:hover { box-shadow: 0 0 0 1px var(--mrr-border), var(--mrr-shadow-xs, 0 2px 6px rgb(0 0 0 / 12%)); transform: var(--mrr-motion-decor, translateY(-1px)); }
.theme-swatch:active { transform: var(--mrr-motion-decor, scale(0.94)); }
.theme-swatch[aria-pressed="true"] { box-shadow: 0 0 0 2px var(--swatch-color); }
.theme-swatch:focus-visible { outline: 2px solid var(--mrr-navigation-active-foreground, var(--mrr-primary)); outline-offset: 3px; }
.custom-color { padding-left: var(--mrr-space-2); font-size: 12px; color: var(--mrr-muted-foreground); border-left: 1px solid var(--mrr-border); }

.setting-item > :not(.label-copy) {
  max-width: 100%;
}

.button-options {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.config-subsection + .config-subsection {
  margin-top: 18px;
}

.subsection-heading {
  margin-bottom: 9px;
}

.subsection-heading strong,
.subsection-heading small {
  display: block;
}

.subsection-heading strong {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.subsection-heading small {
  margin-top: 3px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.menu-mode {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--mrr-space-2);
  width: min(300px, 100%);
}

.mode-option {
  display: grid;
  gap: var(--mrr-space-1);
  place-items: center;
  min-width: 0;
  padding: var(--mrr-space-2);
  font-size: 12px;
  white-space: nowrap;
  cursor: pointer;
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
  transition: border-color var(--mrr-motion-feedback) ease;
}

.mode-option:hover,
.mode-option[aria-pressed="true"] { border-color: var(--mrr-primary); }
.mode-option[aria-pressed="true"] { color: var(--mrr-navigation-active-foreground, var(--mrr-primary)); }
.mode-option:focus-visible { outline: 2px solid var(--mrr-navigation-active-foreground, var(--mrr-primary)); outline-offset: 2px; }

.mode {
  position: relative;
  width: 64px;
  max-width: 100%;
  height: 48px;
  background: var(--mrr-muted);
  border-radius: var(--mrr-radius-md);
}

.mode::before,
.mode::after,
.mode-container {
  position: absolute;
  pointer-events: none;
  content: "";
}

.mode::before {
  background: var(--el-color-primary);
}

.mode::after {
  background: color-mix(in srgb, var(--el-color-primary) 60%, transparent);
}

.mode-container {
  background: color-mix(in srgb, var(--el-color-primary) 12%, transparent);
  border: 1px dashed var(--el-color-primary);
}

.mode-side::before {
  top: 8px;
  bottom: 8px;
  left: 8px;
  width: 8px;
  border-radius: 4px 0 0 4px;
}

.mode-side::after {
  top: 8px;
  bottom: 8px;
  left: 18px;
  width: 12px;
}

.mode-side .mode-container {
  inset: 8px 8px 8px 32px;
  border-radius: 0 4px 4px 0;
}

.mode-head::before {
  top: 8px;
  right: 8px;
  left: 8px;
  height: 8px;
  border-radius: 4px 4px 0 0;
}

.mode-head::after {
  top: 18px;
  bottom: 8px;
  left: 8px;
  width: 12px;
  border-radius: 0 0 0 4px;
}

.mode-head .mode-container {
  inset: 18px 8px 8px 22px;
  border-radius: 0 0 4px;
}

.mode-single::after {
  top: 8px;
  bottom: 8px;
  left: 8px;
  width: 12px;
  border-radius: 4px 0 0 4px;
}

.mode-single .mode-container {
  inset: 8px 8px 8px 22px;
  border-radius: 0 4px 4px 0;
}

@media (max-width: 1200px) {
  .appearance-tabs { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 720px) {
  .config-save-feedback,
  .label-copy {
    flex-basis: auto;
  }

  .config-summary,
  .setting-item {
    flex-direction: column;
    align-items: stretch;
  }

  .theme-tabs,
  .title-style-tabs,
  .radius-control {
    width: 100%;
  }
  .theme-palette-options { grid-template-columns: 1fr; }
  .theme-palette-accent { flex-wrap: wrap; }

  .button-options {
    justify-content: flex-start;
  }

  .config-summary-actions {
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .config-summary-actions :deep(.el-button) {
    flex: 1 1 140px;
  }
}
</style>
