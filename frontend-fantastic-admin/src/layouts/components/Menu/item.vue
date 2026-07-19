<script setup lang="ts">
import type { SubMenuItemProps } from './types'
import { cn } from '@/utils'
import { rootMenuInjectionKey } from './types'

defineOptions({
  name: 'SubMenuItem',
})

const props = withDefaults(
  defineProps<SubMenuItemProps>(),
  {
    level: 0,
    subMenu: false,
    expand: false,
  },
)

const rootMenu = inject(rootMenuInjectionKey)!

const itemRef = ref<HTMLElement>()

const isActived = computed(() => {
  return props.subMenu
    ? rootMenu.subMenus[props.uniqueKey.at(-1)!].active
    : rootMenu.activeIndex === props.uniqueKey.at(-1)!
})

const isItemActive = computed(() => {
  return isActived.value && (!props.subMenu || rootMenu.isMenuPopup)
})

defineExpose({
  ref: itemRef,
})
</script>

<template>
  <div
    ref="itemRef" :class="cn('menu-item relative', {
      'active': isItemActive,
      'py-[2px] px-1.5': (rootMenu.isMenuPopup && rootMenu.props.mode === 'vertical') || (rootMenu.isMenuPopup && level !== 0 && rootMenu.props.mode === 'horizontal') || !rootMenu.isMenuPopup,
      'px-1 py-1.5': rootMenu.isMenuPopup && level === 0 && rootMenu.props.mode === 'horizontal',
    })"
  >
    <router-link v-slot="{ href, navigate }" custom :to="uniqueKey.at(-1) ?? ''">
      <FaTooltip :disabled="!rootMenu.isMenuPopup || level !== 0 || subMenu" :text="typeof item.meta?.title === 'function' ? item.meta?.title() : item.meta?.title" :side="rootMenu.props.mode === 'vertical' ? 'right' : 'bottom'" class="h-full w-full">
        <component
          :is="subMenu ? 'div' : 'a'" v-bind="{
            ...(!subMenu && {
              href: item.meta?.link ? item.meta.link : href,
              target: item.meta?.link ? '_blank' : '_self',
              class: 'no-underline',
            }),
          }" :class="cn('group menu-item-container relative h-full w-full flex cursor-pointer items-center justify-between gap-1 rounded-md px-3 py-2 transition-colors', {
            'active': isItemActive,
            'px-2.5': rootMenu.isMenuPopup && level === 0,
          })" :title="typeof item.meta?.title === 'function' ? item.meta?.title() : item.meta?.title" v-on="{
            ...(!subMenu && {
              click: navigate,
            }),
          }"
        >
          <div
            :class="cn('inline-flex flex-1 items-center justify-center gap-[10px] pl-[calc(var(--indent-level)*16px)]', {
              'flex-col': rootMenu.isMenuPopup && level === 0 && rootMenu.props.mode === 'vertical',
              'gap-1': rootMenu.isMenuPopup && level === 0 && rootMenu.props.showCollapseName,
              'w-full': rootMenu.isMenuPopup && level === 0 && rootMenu.props.showCollapseName && rootMenu.props.mode === 'vertical',
            })" :style="{
              '--indent-level': !rootMenu.isMenuPopup ? props.level ?? 0 : 0,
            }"
          >
            <FaIcon v-if="props.item.meta?.icon" :name="props.item.meta.icon" class="menu-item-container-icon size-4" />
            <span
              v-if="!(rootMenu.isMenuPopup && level === 0 && !rootMenu.props.showCollapseName)" :class="cn('w-0 flex-1 truncate text-[13px] transition-height transition-opacity transition-width', {
                'opacity-0 w-0 h-0': rootMenu.isMenuPopup && level === 0 && !rootMenu.props.showCollapseName,
                'w-full text-center': rootMenu.isMenuPopup && level === 0 && rootMenu.props.showCollapseName,
              })"
            >
              {{ typeof item.meta?.title === 'function' ? item.meta?.title() : item.meta?.title }}
            </span>
          </div>
          <i
            v-if="subMenu && !(rootMenu.isMenuPopup && level === 0)" :class="cn('relative ms-1 w-[10px] after:absolute before:absolute after:h-[1.5px] after:w-[6px] before:h-[1.5px] before:w-[6px] after:bg-current before:bg-current after:transition-transform-200 before:transition-transform-200 after:content-empty before:content-empty after:-translate-y-[1px] before:-translate-y-[1px]', {
              [expand ? 'before:-rotate-45 before:-translate-x-[2px] after:rotate-45 after:translate-x-[2px]' : 'before:rotate-45 before:-translate-x-[2px] after:-rotate-45 after:translate-x-[2px]']: true,
              'opacity-0': rootMenu.isMenuPopup && level === 0,
              '-rotate-90 -top-[1.5px]': rootMenu.isMenuPopup && level !== 0,
            })"
          />
        </component>
      </FaTooltip>
    </router-link>
  </div>
</template>

<style scoped>
.menu-item-container {
  min-height: 36px;
  font-weight: 500;
  color: var(--text-secondary);
  background: transparent;
  border: 1px solid transparent;
  transition: color 140ms ease, background-color 140ms ease, border-color 140ms ease;
}

.menu-item-container:hover {
  color: var(--text-primary);
  background: var(--mrr-navigation-hover);
}

.menu-item-container.active,
.menu-item.active .menu-item-container {
  font-weight: 600;
  color: var(--color-primary);
  background: var(--mrr-navigation-active);
  border-color: var(--mrr-navigation-active-border);
}

.menu-item-container-icon {
  flex: 0 0 auto;
  color: currentcolor;
  transform: none !important;
}
</style>
