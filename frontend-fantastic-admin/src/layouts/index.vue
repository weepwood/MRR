<script setup lang="ts">
import { useSlots } from '@/slots'
import eventBus from '@/utils/eventBus'

import Header from './components/Header/index.vue'
import MainSidebar from './components/MainSidebar/index.vue'
import SubSidebar from './components/SubSidebar/index.vue'
import Topbar from './components/Topbar/index.vue'

defineOptions({
  name: 'Layout',
})

const HotkeysIntro = defineAsyncComponent(() => import('./components/HotkeysIntro/index.vue'))
const LinkView = defineAsyncComponent(() => import('./components/views/link.vue'))

const routeInfo = useRoute()
const settingsStore = useSettingsStore()
const keepAliveStore = useKeepAliveStore()
const menuStore = useMenuStore()
const hotkeysIntroVisible = ref(false)

// 头部是否隐藏
const isHeaderHide = computed(() => {
  return ['single', 'side'].includes(settingsStore.settings.menu.mode) || settingsStore.mode === 'mobile'
})

// 侧边栏主导航是否隐藏
const isMainSidebarHide = computed(() => {
  return settingsStore.settings.menu.mode === 'single'
    || (settingsStore.settings.menu.mode === 'head' && settingsStore.mode !== 'mobile')
})

// 侧边栏次导航是否隐藏
const isSubSidebarHide = computed(() => {
  return menuStore.sidebarMenus.every(item => item.meta?.menu === false)
})

// 标签栏是否隐藏
const isTabbarHide = computed(() => {
  return !settingsStore.settings.tabbar.enable
})

// 工具栏是否隐藏
const isToolbarHide = computed(() => {
  return !settingsStore.settings.toolbar.enable
    || !Object.keys(settingsStore.settings.toolbar).some((key) => {
      if (settingsStore.settings.app.routeBaseOn === 'filesystem' && key === 'breadcrumb') {
        return false
      }
      return settingsStore.settings.toolbar[key as keyof typeof settingsStore.settings.toolbar]
    })
})

const sidebar = useTemplateRef('sidebar')
const isMobileSidebarOpen = computed(() => settingsStore.mode === 'mobile' && !settingsStore.settings.menu.subMenuCollapse)

function closeMobileSidebar(event: KeyboardEvent) {
  if (isMobileSidebarOpen.value) {
    event.stopPropagation()
    settingsStore.toggleSidebarCollapse()
  }
}

watch(isMobileSidebarOpen, (open) => {
  if (!open && sidebar.value?.contains(document.activeElement)) {
    document.getElementById('mrr-mobile-sidebar-toggle')?.focus()
  }
})

const isLink = computed(() => !!routeInfo.meta.link)

function toggleHotkeysIntro() {
  hotkeysIntroVisible.value = !hotkeysIntroVisible.value
}

watch(() => settingsStore.settings.menu.subMenuCollapse, (val) => {
  if (settingsStore.mode === 'mobile') {
    if (!val) {
      document.body.classList.add('overflow-hidden')
    }
    else {
      document.body.classList.remove('overflow-hidden')
    }
  }
})

watch(() => routeInfo.path, () => {
  if (settingsStore.mode === 'mobile') {
    settingsStore.$patch((state) => {
      state.settings.menu.subMenuCollapse = true
    })
  }
})

onMounted(() => {
  eventBus.on('global-hotkeys-intro-toggle', toggleHotkeysIntro)
  // 运行错误中心角标轮询：有 system:error:read 权限的会话才开始拉取。
  useRuntimeErrorsBadgeStore().start()
})

onBeforeUnmount(() => {
  eventBus.off('global-hotkeys-intro-toggle', toggleHotkeysIntro)
  useRuntimeErrorsBadgeStore().stop()
})
</script>

<template>
  <div
    class="layout" :style="{
      '--g-header-actual-height': isHeaderHide ? '0px' : 'var(--g-header-height)',
      '--g-main-sidebar-actual-width': isMainSidebarHide ? '0px' : 'var(--g-main-sidebar-width)',
      '--g-sub-sidebar-actual-width': isSubSidebarHide ? '0px' : (settingsStore.settings.menu.subMenuCollapse && settingsStore.mode !== 'mobile' ? 'var(--g-sub-sidebar-collapse-width)' : 'var(--g-sub-sidebar-width)'),
      '--g-tabbar-actual-height': isTabbarHide ? '0px' : 'var(--g-tabbar-height)',
      '--g-toolbar-actual-height': isToolbarHide ? '0px' : 'var(--g-toolbar-height)',
    }"
  >
    <div id="app-main">
      <Header />
      <div class="wrapper">
        <div id="mrr-sidebar" ref="sidebar" class="sidebar-container" :class="{ show: isMobileSidebarOpen }" @keydown.esc="closeMobileSidebar">
          <MainSidebar />
          <SubSidebar />
        </div>
        <!-- 遮罩与抽屉同节奏进出：visibility 延迟到淡出结束再隐藏，关闭时不再「啪」地消失。 -->
        <button type="button" aria-label="关闭侧栏导航" tabindex="-1" class="mobile-sidebar-mask" :class="{ 'is-visible': isMobileSidebarOpen }" @click="settingsStore.toggleSidebarCollapse()" />
        <div class="main-container pb-[var(--g-main-container-padding-bottom)]">
          <Topbar />
          <div class="main">
            <RouterView v-slot="{ Component, route }">
              <!-- 主内容不使用离场过渡，避免新旧页面在重页面切换时叠加闪烁。 -->
              <KeepAlive :include="keepAliveStore.list" :max="10">
                <component :is="Component" v-show="!isLink" :key="route.name ?? route.path" />
              </KeepAlive>
            </RouterView>
            <LinkView v-if="isLink" />
          </div>
          <FaCopyright />
        </div>
      </div>
    </div>
    <HotkeysIntro v-if="hotkeysIntroVisible" v-model="hotkeysIntroVisible" />
    <component :is="useSlots('free-position')" />
  </div>
</template>

<style scoped>
[data-mode="mobile"] {
  /* 选择器带到 .wrapper，是为了让这里的声明压过下面 .wrapper .sidebar-container 的同优先级简写。 */
  .wrapper .sidebar-container {
    visibility: hidden;
    transform: translateX(calc((var(--g-main-sidebar-width) + var(--g-sub-sidebar-width)) * -1));

    &.show {
      visibility: visible;
      box-shadow: 20px 0 48px rgb(15 23 42 / 18%);
      transform: translateX(0);

      /* 展开方向不等延迟：延迟只留给收起，让滑出动画在隐藏前播完。 */
      transition-delay: 0s;
    }
  }

  .main-container {
    margin-left: 0 !important;
  }

  &[data-menu-mode="single"] {
    .sidebar-container {
      transform: translateX(calc(var(--g-sub-sidebar-width) * -1));

      &.show {
        transform: translateX(0);
      }
    }
  }
}

.layout {
  min-height: 100%;
  background: var(--mrr-app-shell-bg);
}

#app-main {
  width: 100%;
  min-height: 100%;
  margin: 0 auto;
}

.wrapper {
  position: relative;
  width: 100%;
  min-height: 100%;
  padding-top: var(--g-header-actual-height);
  transition: padding-top var(--mrr-motion-move) var(--mrr-ease-out);

  .sidebar-container {
    position: fixed;
    top: var(--g-header-actual-height);
    bottom: 0;
    z-index: var(--mrr-z-sidebar);
    display: flex;
    width: calc(var(--g-main-sidebar-actual-width) + var(--g-sub-sidebar-actual-width));
    overflow: hidden;
    background: var(--mrr-navigation-bg);
    border-right: 1px solid var(--mrr-navigation-border);

    /*
     * 桌面端收起/展开属于「展开收起」，走 move 档；移动端抽屉属于浮层，走 overlay 档。
     * visibility 参与过渡（0 时长 + overlay 延迟），收起时元素先滑出再退出无障碍树。
     */
    transition:
      width var(--mrr-motion-move) var(--mrr-ease-out),
      top var(--mrr-motion-move) var(--mrr-ease-out),
      transform var(--mrr-motion-overlay) var(--mrr-ease-out),
      box-shadow var(--mrr-motion-overlay) var(--mrr-ease-out),
      visibility 0s linear var(--mrr-motion-overlay);
  }

  .main-sidebar-container:not(.main-sidebar-leave-active) + .sub-sidebar-container {
    left: var(--g-main-sidebar-width);
  }

  .main-container {
    display: flex;
    flex-direction: column;
    min-height: 100%;
    margin-left: calc(var(--g-main-sidebar-actual-width) + var(--g-sub-sidebar-actual-width));
    background: transparent;
    transition: margin-left var(--mrr-motion-move) var(--mrr-ease-out);

    .main {
      position: relative;
      flex: auto;
      min-width: 0;
      min-height: calc(100vh - var(--g-header-actual-height));

      /* 工作区留白与页面间距同一档（16–24px），不再出现 18/28 这类网格外数值。 */
      padding: clamp(var(--mrr-space-4), 2vw, var(--mrr-page-gap));
      margin: calc(var(--g-tabbar-actual-height) + var(--g-toolbar-actual-height)) 0 0;
      overflow: hidden;

      /* overflow: clip 同样裁剪内容，但不产生滚动容器——页面内 position: sticky
       * 才能相对窗口滚动生效（如 UI 组件页左侧目录）。Chrome 86 不支持 clip，
       * 回退到 hidden，sticky 失效为既有状态。 */
      overflow: clip;
      background-color: var(--mrr-workspace-bg);
    }
  }
}

@media (max-width: 760px) {
  .wrapper .main-container .main {
    padding: var(--mrr-space-4);
  }
}

.mobile-sidebar-mask {
  position: fixed;
  inset: 0;
  z-index: var(--mrr-z-sidebar-mask);
  visibility: hidden;
  padding: 0;
  background: rgb(0 0 0 / 50%);
  border: 0;
  opacity: 0;
  transition:
    opacity var(--mrr-motion-overlay) var(--mrr-ease-out),
    visibility 0s linear var(--mrr-motion-overlay);

  &.is-visible {
    visibility: visible;
    opacity: 1;
    transition-delay: 0s;
  }
}
</style>
