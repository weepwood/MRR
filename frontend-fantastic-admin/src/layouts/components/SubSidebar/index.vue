<script setup lang="ts">
import { useSlots } from '@/slots'
import Logo from '../Logo/index.vue'
import Menu from '../Menu/index.vue'

defineOptions({
  name: 'SubSidebar',
})

const route = useRoute()

const settingsStore = useSettingsStore()
const menuStore = useMenuStore()

const enableSidebar = computed(() => {
  return settingsStore.mode === 'mobile' || (
    menuStore.sidebarMenus.length !== 0
    && !menuStore.sidebarMenus.every(item => item.meta?.menu === false)
  )
})

const transitionName = ref('')
watch(() => menuStore.actived, (val, oldVal) => {
  if (settingsStore.mode === 'mobile' || settingsStore.settings.menu.mode === 'side') {
    if (val > oldVal) {
      transitionName.value = 'sub-sidebar-y-start'
    }
    else {
      transitionName.value = 'sub-sidebar-y-end'
    }
  }
  else if (settingsStore.settings.menu.mode === 'head') {
    if (val > oldVal) {
      transitionName.value = 'sub-sidebar-x-start'
    }
    else {
      transitionName.value = 'sub-sidebar-x-end'
    }
  }
})
</script>

<template>
  <Transition name="sub-sidebar">
    <div
      v-if="enableSidebar" class="sub-sidebar-container" :class="{
        'is-collapse': settingsStore.mode === 'pc' && settingsStore.settings.menu.subMenuCollapse,
      }"
    >
      <component :is="useSlots('sub-sidebar-top')" />
      <Logo
        v-if="['side', 'single'].includes(settingsStore.settings.menu.mode)" :show-logo="settingsStore.settings.menu.mode === 'single'" class="sidebar-logo" :class="{
          'single': settingsStore.settings.menu.mode === 'single',
          'side-mode-logo': settingsStore.settings.menu.mode === 'side',
        }"
      />
      <component :is="useSlots('sub-sidebar-after-logo')" />
      <FaScrollArea :scrollbar="false" mask gradient-color="var(--mrr-navigation-bg-solid)" class="flex-1 overscroll-contain">
        <TransitionGroup :name="transitionName">
          <template v-for="(mainItem, mainIndex) in menuStore.allMenus" :key="mainIndex">
            <div v-show="mainIndex === menuStore.actived">
              <Menu
                :menu="mainItem.children"
                :value="route.meta.activeMenu || route.path"
                :default-openeds="menuStore.defaultOpenedPaths"
                :accordion="settingsStore.settings.menu.subMenuUniqueOpened"
                :collapse="settingsStore.mode === 'pc' && settingsStore.settings.menu.subMenuCollapse"
                :leading-expand-indicator="settingsStore.settings.menu.mode === 'single'"
                class="menu"
                :class="{ 'menu--grouped-single': settingsStore.settings.menu.mode === 'single' }"
              />
            </div>
          </template>
        </TransitionGroup>
      </FaScrollArea>
      <div v-if="settingsStore.mode === 'pc' && settingsStore.settings.menu.enableSubMenuCollapseButton" class="collapse-area relative flex items-center px-3 py-3" :class="[settingsStore.settings.menu.subMenuCollapse ? 'justify-center' : 'justify-end']">
        <FaButton variant="ghost" size="icon" class="h-8 w-8 transition" :class="{ '-rotate-z-180': settingsStore.settings.menu.subMenuCollapse }" @click="settingsStore.toggleSidebarCollapse()">
          <FaIcon name="toolbar-collapse" class="size-4" />
        </FaButton>
      </div>
      <component :is="useSlots('sub-sidebar-after-menu')" />
      <div v-if="settingsStore.settings.menu.mode === 'single'" class="account-area flex-center px-3 pb-3">
        <AccountButton :only-avatar="settingsStore.settings.menu.subMenuCollapse" dropdown-align="center" :dropdown-side="settingsStore.settings.menu.subMenuCollapse ? 'right' : 'top'" button-variant="secondary" :class="{ 'w-full': !settingsStore.settings.menu.subMenuCollapse }" />
      </div>
      <component :is="useSlots('sub-sidebar-bottom')" />
    </div>
  </Transition>
</template>

<style scoped>
.sub-sidebar-container {
  position: absolute;
  inset-inline-start: 0;
  top: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  width: var(--g-sub-sidebar-width);
  color: var(--text-primary);
  background: var(--mrr-navigation-bg-solid);
  transition: background-color 220ms ease, inset-inline-start 220ms ease, width 220ms ease;

  &.is-collapse {
    width: var(--g-sub-sidebar-collapse-width);

    .sidebar-logo {
      &:not(.single) {
        display: none;
      }

      :deep(span) {
        display: none;
      }
    }

    .menu {
      padding-inline: 4px;
    }
  }

  .sidebar-logo {
    min-height: var(--g-sidebar-logo-height);
    background: transparent;
    border-bottom: 1px solid var(--mrr-navigation-border);

    &.side-mode-logo,
    &.single {
      --g-sidebar-logo-height: var(--g-toolbar-height);
    }
  }

  .menu {
    width: 100%;
    padding: 4px 8px 16px;

    :deep(.menu-item) {
      padding: 2px 4px;

      .menu-item-container {
        min-height: 38px;
        padding: 8px 10px !important;
        font-size: 13px;
        font-weight: 500;
        color: var(--text-secondary) !important;
        border: 1px solid transparent;
        border-radius: var(--mrr-radius-md);
        transition: color 140ms ease, background-color 140ms ease, border-color 140ms ease;

        &:hover {
          color: var(--text-primary) !important;
          background: var(--mrr-navigation-hover);
        }

        .menu-item-container-icon {
          width: 17px;
          height: 17px;
          font-size: 17px !important;
          transform: none !important;
        }
      }

      &.active .menu-item-container {
        font-weight: 600;
        color: var(--color-primary) !important;
        background: var(--mrr-navigation-active) !important;
        border-color: var(--mrr-navigation-active-border);
      }
    }
  }

  .menu--grouped-single {
    padding-top: 10px;

    & > :deep(.menu-item) {
      padding: 2px 0;
    }

    & > :deep(.menu-item > .menu-item-container) {
      min-height: 40px;
      padding-inline: 10px !important;
      font-weight: 620;
      color: var(--text-primary) !important;
      border-color: transparent;
      border-radius: var(--mrr-radius-md);
    }

    & > :deep(.sub-menu) {
      margin: 2px 0 8px;
      padding-inline-start: 2px;
    }

    :deep(.menu-item-container) {
      background: color-mix(in srgb, var(--mrr-navigation-hover) 52%, transparent);
    }

    :deep(.menu-item-container:hover) {
      background: var(--mrr-navigation-hover);
    }

    :deep(.menu-item.active .menu-item-container) {
      background: var(--mrr-navigation-active) !important;
    }
  }

  .collapse-area {
    border-top: 1px solid var(--mrr-navigation-border);
  }
}

/* 次导航内容切换 */
.sub-sidebar-x-start-enter-active,
.sub-sidebar-x-end-enter-active,
.sub-sidebar-y-start-enter-active,
.sub-sidebar-y-end-enter-active {
  transition: opacity 160ms ease, transform 160ms ease;
}

.sub-sidebar-x-start-enter-from,
.sub-sidebar-x-start-leave-active {
  opacity: 0;
  transform: translateX(8px);
}

.sub-sidebar-x-end-enter-from,
.sub-sidebar-x-end-leave-active {
  opacity: 0;
  transform: translateX(-8px);
}

.sub-sidebar-y-start-enter-from,
.sub-sidebar-y-start-leave-active {
  opacity: 0;
  transform: translateY(8px);
}

.sub-sidebar-y-end-enter-from,
.sub-sidebar-y-end-leave-active {
  opacity: 0;
  transform: translateY(-8px);
}

.sub-sidebar-x-start-leave-active,
.sub-sidebar-x-end-leave-active,
.sub-sidebar-y-start-leave-active,
.sub-sidebar-y-end-leave-active {
  position: absolute;
}

.sub-sidebar-enter-active,
.sub-sidebar-leave-active {
  transition: transform 220ms ease, opacity 220ms ease;
}

.sub-sidebar-enter-from,
.sub-sidebar-leave-to {
  opacity: 0;
  transform: translateX(calc(var(--g-sub-sidebar-width) * -1));
}
</style>
