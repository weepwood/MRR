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
      <Logo :show-title="false" class="sidebar-logo" />
      <component :is="useSlots('main-sidebar-after-logo')" />
      <FaScrollArea :scrollbar="false" mask gradient-color="var(--mrr-navigation-bg-solid)" class="menu flex-1 overscroll-contain">
        <!-- 侧边栏模式（含主导航） -->
        <div class="w-full flex flex-col of-hidden py-1 transition-all">
          <template v-for="(item, index) in menuStore.allMenus" :key="index">
            <div
              class="menu-item relative px-2 py-1 transition-all" :class="{
                active: index === menuStore.actived,
              }"
            >
              <div
                v-if="item.children && item.children.length !== 0" class="group menu-item-container relative h-full w-full flex cursor-pointer items-center justify-between gap-1 rounded-lg py-4 text-[var(--g-main-sidebar-menu-color)] transition-colors px-2!" :class="{
                  active: index === menuStore.actived,
                }" :title="typeof item.meta?.title === 'function' ? item.meta?.title() : item.meta?.title" @click="switchTo(index)"
              >
                <div class="w-full inline-flex flex-1 flex-col items-center justify-center gap-[3px]">
                  <FaIcon v-if="item.meta?.icon" :name="item.meta?.icon" class="menu-item-container-icon" />
                  <span class="w-full flex-1 truncate text-center transition-height transition-opacity transition-width">
                    {{ typeof item.meta?.title === 'function' ? item.meta?.title() : item.meta?.title }}
                  </span>
                </div>
              </div>
            </div>
          </template>
        </div>
      </FaScrollArea>
      <component :is="useSlots('main-sidebar-after-menu')" />
      <div class="account-area flex-center px-3 py-3">
        <AccountButton only-avatar :button-variant="settingsStore.settings.menu.mode === 'side' ? 'secondary' : 'ghost'" class="size-10 p-1.5" />
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
  background: color-mix(in srgb, var(--mrr-navigation-bg-solid) 96%, var(--color-primary) 4%);
  border-right: 1px solid var(--mrr-navigation-border);
  transition: background-color 220ms ease, color 220ms ease;

  .sidebar-logo {
    min-height: var(--g-sidebar-logo-height);
    background: transparent;
    border-bottom: 1px solid var(--mrr-navigation-border);
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
        transition: color 140ms ease, background-color 140ms ease, border-color 140ms ease;

        &:hover {
          color: var(--text-primary) !important;
          background: var(--mrr-navigation-hover);
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
        color: var(--color-primary) !important;
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
.main-sidebar-enter-active,
.main-sidebar-leave-active {
  transition: transform 220ms ease, opacity 220ms ease;
}

.main-sidebar-enter-from,
.main-sidebar-leave-to {
  opacity: 0;
  transform: translateX(calc(var(--g-main-sidebar-width) * -1));
}
</style>
