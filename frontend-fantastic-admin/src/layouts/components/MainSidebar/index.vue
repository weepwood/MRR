<script setup lang="ts">
import hotkeys from 'hotkeys-js'
import { useSlots } from '@/slots'
import Logo from '../Logo/index.vue'

defineOptions({
  name: 'MainSidebar',
})

const settingsStore = useSettingsStore()
const menuStore = useMenuStore()

const { switchTo } = useMenu()

onMounted(() => {
  hotkeys('alt+`', (e) => {
    if (settingsStore.settings.menu.enableHotkeys && ['side', 'head'].includes(settingsStore.settings.menu.mode)) {
      e.preventDefault()
      switchTo(menuStore.actived + 1 < menuStore.allMenus.length ? menuStore.actived + 1 : 0)
    }
  })
  hotkeys('alt+shift+`', (e) => {
    if (settingsStore.settings.menu.enableHotkeys && ['side', 'head'].includes(settingsStore.settings.menu.mode)) {
      e.preventDefault()
      switchTo(menuStore.actived - 1 >= 0 ? menuStore.actived - 1 : menuStore.allMenus.length - 1)
    }
  })
})
onUnmounted(() => {
  hotkeys.unbind('alt+`')
  hotkeys.unbind('alt+shift+`')
})
</script>

<template>
  <Transition name="main-sidebar">
    <div v-if="settingsStore.settings.menu.mode === 'side' || (settingsStore.mode === 'mobile' && settingsStore.settings.menu.mode !== 'single')" class="main-sidebar-container">
      <component :is="useSlots('main-sidebar-top')" />
      <Logo :show-title="false" class="sidebar-logo" :class="{ 'side-mode-logo': settingsStore.settings.menu.mode === 'side' }" />
      <component :is="useSlots('main-sidebar-after-logo')" />
      <FaScrollArea :scrollbar="false" mask gradient-color="var(--mrr-navigation-bg-solid)" class="menu flex-1 overscroll-contain">
        <!-- 侧边栏模式（含主导航） -->
        <nav aria-label="主导航" class="w-full flex flex-col of-hidden py-1">
          <template v-for="(item, index) in menuStore.allMenus" :key="index">
            <div
              class="menu-item relative px-2 py-1" :class="{
                active: index === menuStore.actived,
              }"
            >
              <button
                v-if="item.children && item.children.length !== 0" type="button" aria-controls="mrr-sub-navigation" :aria-pressed="index === menuStore.actived" class="mrr-navigation-control group menu-item-container relative h-full w-full flex cursor-pointer items-center justify-between gap-1 rounded-lg py-4 text-[var(--g-main-sidebar-menu-color)] transition-colors px-2!" :class="{
                  active: index === menuStore.actived,
                }" :title="typeof item.meta?.title === 'function' ? item.meta?.title() : item.meta?.title" @click="switchTo(index)"
              >
                <span class="w-full inline-flex flex-1 flex-col items-center justify-center gap-[3px]">
                  <FaIcon v-if="item.meta?.icon" :name="item.meta?.icon" class="menu-item-container-icon" />
                  <span class="w-full flex-1 truncate text-center transition-height transition-opacity transition-width">
                    {{ typeof item.meta?.title === 'function' ? item.meta?.title() : item.meta?.title }}
                  </span>
                </span>
              </button>
            </div>
          </template>
        </nav>
      </FaScrollArea>
      <component :is="useSlots('main-sidebar-after-menu')" />
      <div class="account-area flex-center px-3 py-3">
        <AccountButton only-avatar :button-variant="settingsStore.settings.menu.mode === 'side' ? 'secondary' : 'ghost'" class="p-1.5" />
      </div>
      <component :is="useSlots('main-sidebar-bottom')" />
    </div>
  </Transition>
</template>

<style scoped>
.main-sidebar-container {
  position: relative;
  z-index: 10;
  display: flex;
  flex-direction: column;
  width: var(--g-main-sidebar-width);
  color: var(--text-secondary);
  background: var(--mrr-navigation-rail-bg);
  border-right: 1px solid var(--mrr-navigation-border);
  transition: background-color var(--mrr-motion-feedback) ease, color var(--mrr-motion-feedback) ease;

  .sidebar-logo {
    min-height: var(--g-sidebar-logo-height);
    background: transparent;
    border-bottom: 1px solid var(--mrr-navigation-border);

    &.side-mode-logo {
      --g-sidebar-logo-height: var(--g-toolbar-height);
    }
  }

  .menu {
    padding-top: 12px;

    :deep(.menu-item) {
      padding: 3px 8px;

      .menu-item-container {
        min-height: 56px;
        padding: 8px 5px !important;
        font-size: 11px;
        font-weight: 550;
        line-height: 1.2;
        color: var(--text-secondary) !important;
        border: 1px solid transparent;
        border-radius: var(--mrr-radius-lg);
        transition: color var(--mrr-motion-feedback) var(--mrr-ease-out), background-color var(--mrr-motion-feedback) var(--mrr-ease-out), border-color var(--mrr-motion-feedback) var(--mrr-ease-out);

        /* 窄栏自身是灰底，悬停必须比底色再深一档，否则悬停毫无可见变化。 */
        &:hover {
          color: var(--text-primary) !important;
          background: var(--mrr-navigation-rail-hover);
        }

        .menu-item-container-icon {
          width: 19px;
          height: 19px;
          font-size: 19px !important;
          transform: none !important;
        }

        span {
          font-size: 11px;
        }
      }

      &.active .menu-item-container,
      .menu-item-container.active {
        color: var(--mrr-navigation-active-foreground) !important;
        background: var(--mrr-navigation-active) !important;
        border-color: var(--mrr-navigation-active-border);
        box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--color-primary) 4%, transparent);
      }
    }
  }

  .account-area {
    border-top: 1px solid var(--mrr-navigation-border);
  }
}

/* 主侧边栏动画 */
.main-sidebar-enter-active {
  transition: transform var(--mrr-motion-overlay) var(--mrr-ease-out), opacity var(--mrr-motion-overlay) var(--mrr-ease-out);
}

.main-sidebar-leave-active {
  transition: transform var(--mrr-motion-move) var(--mrr-ease-in), opacity var(--mrr-motion-move) var(--mrr-ease-in);
}

.main-sidebar-enter-from,
.main-sidebar-leave-to {
  opacity: 0;
  transform: translateX(calc(var(--g-main-sidebar-width) * -1));
}
</style>
