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
})

onBeforeUnmount(() => {
  eventBus.off('global-hotkeys-intro-toggle', toggleHotkeysIntro)
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
        <div class="sidebar-container" :class="{ show: settingsStore.mode === 'mobile' && !settingsStore.settings.menu.subMenuCollapse }">
          <MainSidebar />
          <SubSidebar />
        </div>
        <button type="button" class="invisible fixed inset-0 z-1009 bg-black/50 op-0 backdrop-blur-sm transition-opacity" :class="{ 'op-100! visible!': settingsStore.mode === 'mobile' && !settingsStore.settings.menu.subMenuCollapse }" @click="settingsStore.toggleSidebarCollapse()" />
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
  .sidebar-container {
    transform: translateX(calc((var(--g-main-sidebar-width) + var(--g-sub-sidebar-width)) * -1));

    &.show {
      box-shadow: 20px 0 48px rgb(15 23 42 / 18%);
      transform: translateX(0);
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
  transition: padding-top 220ms ease;

  .sidebar-container {
    position: fixed;
    top: var(--g-header-actual-height);
    bottom: 0;
    z-index: 1010;
    display: flex;
    width: calc(var(--g-main-sidebar-actual-width) + var(--g-sub-sidebar-actual-width));
    overflow: hidden;
    background: var(--mrr-navigation-bg);
    border-right: 1px solid var(--mrr-navigation-border);
    backdrop-filter: blur(18px) saturate(140%);
    transition: width 220ms ease, transform 220ms ease, top 220ms ease, box-shadow 220ms ease;

    &:has(> .main-sidebar-container.main-sidebar-enter-active),
    &:has(> .main-sidebar-container.main-sidebar-leave-active),
    &:has(> .sub-sidebar-container.sub-sidebar-enter-active),
    &:has(> .sub-sidebar-container.sub-sidebar-leave-active) {
      overflow: hidden;
    }
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
    transition: margin-left 220ms ease;

    .main {
      position: relative;
      flex: auto;
      min-width: 0;
      min-height: calc(100vh - var(--g-header-actual-height));
      padding: clamp(18px, 2vw, 28px);
      margin: calc(var(--g-tabbar-actual-height) + var(--g-toolbar-actual-height)) 0 0;
      overflow: hidden;
      background:
        radial-gradient(circle at 100% 0%, color-mix(in srgb, var(--color-primary) 4%, transparent), transparent 26%),
        var(--mrr-workspace-bg);
    }
  }
}

@media (width <= 760px) {
  .wrapper .main-container .main {
    padding: 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .wrapper,
  .sidebar-container,
  .main-container {
    transition: none;
  }
}
</style>