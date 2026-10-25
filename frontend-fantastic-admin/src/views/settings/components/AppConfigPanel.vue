<script setup lang="ts">
import type { Settings } from '#/global'
import settingsDefault from '@/settings.default'
import { notify } from '@/utils/notification'
import { diffTwoObj } from '@/utils/object'

defineOptions({ name: 'AppConfigPanel' })

const props = withDefaults(defineProps<{
  drawer?: boolean
}>(), {
  drawer: false,
})

const settingsStore = useSettingsStore()
const autoSaveState = ref<'idle' | 'saving' | 'saved'>('idle')
const activeGroup = ref('theme')
const importInput = ref<HTMLInputElement>()
let autoSaveTimer: ReturnType<typeof setTimeout> | undefined

const appRadius = computed<number[]>({
  get() {
    return [settingsStore.settings.app.radius]
  },
  set(value) {
    settingsStore.settings.app.radius = value[0]
  },
})

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

defineExpose({ autoSaveLabel, syncScopeDescription })

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
    <div class="config-summary">
      <div>
        <strong>浏览器外观配置</strong>
        <p>{{ syncScopeDescription }}</p>
      </div>
      <div class="config-summary-actions">
        <span class="autosave-state">
          <FaIcon name="i-ri:save-3-line" />
          {{ autoSaveLabel }}
        </span>
        <el-button @click="handleReset">
          <FaIcon name="i-ri:restart-line" />
          恢复默认
        </el-button>
        <el-button @click="handleExport">
          <FaIcon name="i-ri:download-2-line" />
          导出配置
        </el-button>
        <el-button @click="triggerImport">
          <FaIcon name="i-ri:upload-2-line" />
          导入配置
        </el-button>
      </div>
    </div>
    <div v-if="settingsStore.uiSettingsSyncState === 'local-only'" class="sync-notice">
      <FaIcon name="i-ri:information-line" />
      <span>当前账号没有「系统管理」权限，外观修改仅在本机生效，不会影响其他终端。</span>
    </div>
    <div v-else-if="settingsStore.uiSettingsSyncState === 'failed'" class="sync-notice sync-notice--warning">
      <FaIcon name="i-ri:error-warning-line" />
      <span>同步到全站失败，修改已保存在本机。请检查网络后重试。</span>
    </div>
    <input ref="importInput" class="import-input" type="file" accept="application/json,.json" @change="handleImport">

    <el-collapse v-model="activeGroup" accordion class="config-groups">
      <el-collapse-item name="theme">
        <template #title>
          <div class="collapse-title">
            <span class="collapse-icon"><FaIcon name="i-ri:paint-brush-line" /></span>
            <div>
              <strong>主题与页面样式</strong>
              <small>颜色模式、圆角与页面标题展示</small>
            </div>
          </div>
        </template>

        <div class="config-section">
          <div class="setting-item setting-item--stack">
            <div class="label-copy">
              <strong>颜色主题</strong>
              <small>可固定明亮、暗黑模式，或跟随操作系统。</small>
            </div>
            <FaTabs
              v-model="settingsStore.settings.app.colorScheme"
              :list="[
                { icon: 'i-ri:sun-line', label: '明亮', value: 'light' },
                { icon: 'i-ri:moon-line', label: '暗黑', value: 'dark' },
                { icon: 'i-codicon:color-mode', label: '系统', value: '' },
              ]"
              class="theme-tabs"
            />
          </div>

          <div class="setting-item">
            <div class="label-copy">
              <strong>主题主色</strong>
              <small>菜单、主要按钮和选中状态会同步使用此颜色。</small>
            </div>
            <el-color-picker
              v-model="settingsStore.settings.app.themeColor"
              color-format="hex"
              :predefine="['#2563EB', '#0891B2', '#16803C', '#B45F06', '#7C3AED', '#DC2626']"
            />
          </div>

          <div class="setting-item">
            <div class="label-copy">
              <strong>圆角系数</strong>
              <small>统一调整页面卡片与面板的圆角程度。</small>
            </div>
            <FaSlider v-model="appRadius" :min="0" :max="1" :step="0.25" class="slider-field" />
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
        </div>
      </el-collapse-item>

      <el-collapse-item name="navigation">
        <template #title>
          <div class="collapse-title">
            <span class="collapse-icon"><FaIcon name="i-ri:menu-2-line" /></span>
            <div>
              <strong>导航与顶栏</strong>
              <small>导航结构、点击方式与顶栏定位</small>
            </div>
          </div>
        </template>

        <div class="config-section">
          <div v-if="settingsStore.mode === 'pc'" class="setting-item setting-item--stack">
            <div class="label-copy">
              <strong>导航栏模式</strong>
              <small>选择适合当前工作区的菜单布局。</small>
            </div>
            <div class="menu-mode">
              <FaTooltip text="侧边栏模式（含主导航）" :delay="500">
                <button type="button" class="mode mode-side" :class="{ active: settingsStore.settings.menu.mode === 'side' }" @click="settingsStore.settings.menu.mode = 'side'">
                  <span class="mode-container" />
                </button>
              </FaTooltip>
              <FaTooltip text="顶部模式" :delay="500">
                <button type="button" class="mode mode-head" :class="{ active: settingsStore.settings.menu.mode === 'head' }" @click="settingsStore.settings.menu.mode = 'head'">
                  <span class="mode-container" />
                </button>
              </FaTooltip>
              <FaTooltip text="侧边栏模式（不含主导航）" :delay="500">
                <button type="button" class="mode mode-single" :class="{ active: settingsStore.settings.menu.mode === 'single' }" @click="settingsStore.settings.menu.mode = 'single'">
                  <span class="mode-container" />
                </button>
              </FaTooltip>
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
                size="sm"
                @click="settingsStore.settings.topbar.mode = (item.value as any)"
              >
                {{ item.label }}
              </FaButton>
            </div>
          </div>
        </div>
      </el-collapse-item>

      <el-collapse-item name="workspace">
        <template #title>
          <div class="collapse-title">
            <span class="collapse-icon"><FaIcon name="i-ri:layout-masonry-line" /></span>
            <div>
              <strong>工作区组件</strong>
              <small>标签栏、工具栏、主页与导航搜索</small>
            </div>
          </div>
        </template>

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
      </el-collapse-item>

      <el-collapse-item name="other">
        <template #title>
          <div class="collapse-title">
            <span class="collapse-icon"><FaIcon name="i-ri:more-2-fill" /></span>
            <div>
              <strong>主页、版权与其他</strong>
              <small>主页入口、版权信息和辅助功能</small>
            </div>
          </div>
        </template>

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
      </el-collapse-item>
    </el-collapse>
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

.app-config-panel--drawer .config-summary > div:first-child {
  flex-basis: auto;
}

.app-config-panel--drawer .autosave-state {
  width: 100%;
}

.config-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  margin-bottom: 12px;
  background: var(--el-fill-color-extra-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--mrr-radius-card);
}

.config-summary > div:first-child {
  flex: 1 1 260px;
  min-width: 0;
}

.config-summary strong,
.config-summary p {
  display: block;
}

.config-summary strong {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.config-summary p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.autosave-state {
  display: inline-flex;
  flex: 0 0 auto;
  gap: 6px;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.config-summary-actions {
  display: flex;
  flex: 0 1 auto;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
  max-width: 100%;
}

.config-summary-actions :deep(.el-button) {
  gap: 6px;
  margin-left: 0;
}

.import-input {
  display: none;
}

.sync-notice {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 10px 14px;
  margin-bottom: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.sync-notice--warning {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
  border-color: var(--el-color-warning-light-7);
}

.config-groups {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--mrr-radius-card);
}

.config-groups :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 66px;
  padding: 10px 16px;
  line-height: 1.4;
  background: var(--el-bg-color);
}

.config-groups :deep(.el-collapse-item__content) {
  padding: 0 16px 18px;
}

.config-groups :deep(.el-collapse-item__wrap) {
  background: var(--el-bg-color);
}

.collapse-title {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
}

.collapse-icon {
  display: grid;
  flex: 0 0 34px;
  place-items: center;
  width: 34px;
  height: 34px;
  font-size: 16px;
  color: var(--el-color-primary);
  background: color-mix(in srgb, var(--el-color-primary) 10%, var(--el-bg-color));
  border-radius: var(--mrr-radius-md);
}

.collapse-title strong,
.collapse-title small {
  display: block;
}

.collapse-title strong {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.collapse-title small {
  margin-top: 3px;
  font-size: 11px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.config-section {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--mrr-radius-card);
}

.config-section--nested {
  border-radius: var(--mrr-radius-md);
}

.setting-item {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
  align-items: center;
  justify-content: space-between;
  min-height: 66px;
  padding: 12px 14px;
}

.setting-item + .setting-item {
  border-top: 1px solid var(--el-border-color-extra-light);
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

.title-style-tabs {
  width: min(360px, 55%);
}

.slider-field {
  width: min(360px, 48%);
}

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
  display: flex;
  gap: 14px;
  align-items: center;
  justify-content: flex-end;
}

.mode {
  position: relative;
  width: 64px;
  height: 48px;
  padding: 0;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: var(--mrr-radius-md);
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

.mode.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--el-color-primary) 18%, transparent);
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

@media (max-width: 720px) {
  .config-summary > div:first-child,
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
  .slider-field {
    width: 100%;
  }

  .menu-mode,
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

  .autosave-state {
    width: 100%;
  }
}
</style>
