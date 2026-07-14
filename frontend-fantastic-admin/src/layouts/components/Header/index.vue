<script setup lang="ts">
import { useSlots } from '@/slots'
import Logo from '../Logo/index.vue'

defineOptions({
  name: 'LayoutHeader',
})

const settingsStore = useSettingsStore()
const menuStore = useMenuStore()

const { switchTo } = useMenu()
</script>

<template>
  <Transition name="header">
    <header v-if="settingsStore.mode === 'pc' && settingsStore.settings.menu.mode === 'head'">
      <div class="header-container">
        <component :is="useSlots('header-start')" />
        <Logo class="title" />
        <component :is="useSlots('header-after-logo')" />
        <FaScrollArea :scrollbar="false" mask horizontal gradient-color="var(--mrr-navigation-bg-solid)" class="menu-container h-full flex-1 overscroll-contain">
          <!-- 顶部模式 -->
          <div class="menu h-full flex of-hidden transition-all">
            <template v-for="(item, index) in menuStore.allMenus" :key="index">
              <div
                class="menu-item relative transition-all" :class="{
                  active: index === menuStore.actived,
                }"
              >
                <div
                  v-if="item.children && item.children.length !== 0" class="menu-item-container relative h-full w-full flex cursor-pointer items-center justify-between gap-1" :class="{
                    active: index === menuStore.actived,
                  }" :title="typeof item.meta?.title === 'function' ? item.meta?.title() : item.meta?.title" @click="switchTo(index)"
                >
                  <div class="inline-flex flex-1 items-center justify-center gap-2">
                    <FaIcon v-if="item.meta?.icon" :name="item.meta?.icon" class="menu-item-container-icon" />
                    <span class="w-full flex-1 truncate">
                      {{ typeof item.meta?.title === 'function' ? item.meta?.title() : item.meta?.title }}
                    </span>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </FaScrollArea>
        <component :is="useSlots('header-after-menu')" />
        <div class="account-slot flex-center">
          <AccountButton only-avatar dropdown-side="bottom" class="size-10 p-1.5" />
        </div>
        <component :is="useSlots('header-end')" />
      </div>
    </header>
  </Transition>
</template>

<style scoped>
header {
  position: fixed;
  top: 0;
  right: var(--scrollbar-width, 0);
  left: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  width: calc(100% - var(--scrollbar-width, 0px));
  height: var(--g-header-height);
  margin: 0 auto;
  color: var(--text-primary);
  background: var(--mrr-navigation-bg);
  border-bottom: 1px solid var(--mrr-navigation-border);
  backdrop-filter: blur(18px) saturate(140%);
  transition: background-color 220ms ease, border-color 220ms ease;

  .header-container {
    display: flex;
    gap: 18px;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    height: 100%;
    padding: 0 18px;
    margin: 0 auto;

    :deep(a.title) {
      position: relative;
      flex: 0;
      width: inherit;
      height: inherit;
      padding: 0;
      background: transparent;

      .logo {
        width: initial;
        max-width: initial;
        height: min(62%, 42px);
      }

      span {
        font-size: 17px;
        font-weight: 700;
        color: var(--text-primary);
        letter-spacing: -0.01em;
      }
    }

    .menu-container {
      .menu {
        display: inline-flex;
        gap: 4px;
        align-items: center;
        height: 100%;

        :deep(.menu-item) {
          display: flex;
          align-items: center;
          height: 100%;

          .menu-item-container {
            min-height: 36px;
            padding: 0 12px;
            font-size: 13px;
            font-weight: 550;
            color: var(--text-secondary);
            border: 1px solid transparent;
            border-radius: var(--mrr-radius-md);
            transition: color 140ms ease, background-color 140ms ease, border-color 140ms ease;

            &:hover {
              color: var(--text-primary);
              background: var(--mrr-navigation-hover);
            }

            .menu-item-container-icon {
              width: 16px;
              height: 16px;
              font-size: 16px !important;
            }

            &.active {
              color: var(--color-primary);
              background: var(--mrr-navigation-active);
              border-color: var(--mrr-navigation-active-border);
            }
          }
        }
      }
    }

    .account-slot {
      padding-left: 12px;
      border-left: 1px solid var(--mrr-navigation-border);
    }
  }
}

.header-enter-active,
.header-leave-active {
  transition: transform 220ms ease, opacity 220ms ease;
}

.header-enter-from,
.header-leave-to {
  opacity: 0;
  transform: translateY(calc(var(--g-header-height) * -1));
}
</style>