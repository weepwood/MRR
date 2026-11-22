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

const emit = defineEmits<{ click: [event: MouseEvent] }>()

const title = computed(() => {
  const title = props.item.meta?.title
  return typeof title === 'function' ? title() : title
})

const rootMenu = inject(rootMenuInjectionKey)!

const itemRef = ref<HTMLElement>()
function handleClick(event: MouseEvent, navigate: (event?: MouseEvent) => void) {
  if (!props.subMenu) {
    navigate(event)
  }
  emit('click', event)
}

const isActived = computed(() => {
  return props.subMenu
    ? rootMenu.subMenus[props.uniqueKey.at(-1)!].active
    : rootMenu.activeIndex === props.uniqueKey.at(-1)!
})

const isItemActive = computed(() => {
  return isActived.value && (!props.subMenu || rootMenu.isMenuPopup)
})

const useLeadingExpandIndicator = computed(() => {
  return props.subMenu
    && rootMenu.props.leadingExpandIndicator
    && props.level === 0
    && !rootMenu.isMenuPopup
})

/**
 * 运行错误中心角标：近 24 小时活跃 ERROR 错误组数。
 * 只挂在叶子菜单项上，子菜单容器不重复显示。
 */
const badgeStore = useRuntimeErrorsBadgeStore()
const badgeCount = computed(() => {
  return !props.subMenu && props.item.path === '/runtime-errors'
    ? badgeStore.recentErrorGroups
    : 0
})

defineExpose({
  ref: itemRef,
})
</script>

<template>
  <div
    ref="itemRef" :class="cn('menu-item relative select-none', {
      'active': isItemActive,
      'py-[2px] px-1.5': (rootMenu.isMenuPopup && rootMenu.props.mode === 'vertical') || (rootMenu.isMenuPopup && level !== 0 && rootMenu.props.mode === 'horizontal') || !rootMenu.isMenuPopup,
      'px-1 py-1.5': rootMenu.isMenuPopup && level === 0 && rootMenu.props.mode === 'horizontal',
    })"
  >
    <router-link v-slot="{ href, navigate }" custom :to="subMenu ? '' : uniqueKey.at(-1) ?? ''">
      <FaTooltip :disabled="!rootMenu.isMenuPopup || level !== 0 || subMenu" :text="title" :side="rootMenu.props.mode === 'vertical' ? 'right' : 'bottom'" class="h-full w-full">
        <component
          :is="subMenu ? 'button' : 'a'" v-bind="{
            ...(subMenu && { 'type': 'button', 'aria-expanded': expand, 'aria-controls': popupId }),
            ...(!subMenu && {
              href: item.meta?.link ? item.meta.link : href,
              target: item.meta?.link ? '_blank' : '_self',
              class: 'no-underline',
            }),
          }" :class="cn('group mrr-navigation-control menu-item-container relative h-full w-full flex cursor-pointer items-center justify-between gap-1 rounded-md px-3 py-2 transition-colors', {
            'active': isItemActive,
            'px-2.5': rootMenu.isMenuPopup && level === 0,
          })" :aria-current="!subMenu && isItemActive ? 'page' : undefined" :aria-label="subMenu || (rootMenu.isMenuPopup && level === 0 && !rootMenu.props.showCollapseName) ? title : undefined" :title="title" @click="handleClick($event, navigate)"
        >
          <span
            :class="cn('relative z-10 text-start inline-flex flex-1 items-center justify-center gap-[10px] pl-[calc(var(--indent-level)*16px)]', {
              'flex-col': rootMenu.isMenuPopup && level === 0 && rootMenu.props.mode === 'vertical',
              'gap-1': rootMenu.isMenuPopup && level === 0 && rootMenu.props.showCollapseName,
              'w-full': rootMenu.isMenuPopup && level === 0 && rootMenu.props.showCollapseName && rootMenu.props.mode === 'vertical',
            })" :style="{
              '--indent-level': !rootMenu.isMenuPopup ? props.level ?? 0 : 0,
            }"
          >
            <span v-if="useLeadingExpandIndicator" class="menu-leading-control" aria-hidden="true">
              <FaIcon v-if="props.item.meta?.icon" :name="props.item.meta.icon" class="menu-item-container-icon menu-leading-control-icon size-4" />
              <i class="menu-leading-control-arrow" :class="{ 'is-expanded': expand }" />
            </span>
            <FaIcon v-else-if="props.item.meta?.icon" :name="props.item.meta.icon" class="menu-item-container-icon size-4" />
            <span
              v-if="!(rootMenu.isMenuPopup && level === 0 && !rootMenu.props.showCollapseName)" :class="cn('w-0 flex-1 truncate text-[13px] transition-height transition-opacity transition-width', {
                'opacity-0 w-0 h-0': rootMenu.isMenuPopup && level === 0 && !rootMenu.props.showCollapseName,
                'w-full text-center': rootMenu.isMenuPopup && level === 0 && rootMenu.props.showCollapseName,
              })"
            >
              {{ title }}
            </span>
            <span v-if="badgeCount > 0" class="menu-error-badge" :title="`近 24 小时 ${badgeCount} 组 ERROR 错误`">
              {{ badgeCount > 99 ? '99+' : badgeCount }}
            </span>
          </span>
          <i
            v-if="subMenu && !useLeadingExpandIndicator && !(rootMenu.isMenuPopup && level === 0)"
            aria-hidden="true" :class="cn('relative ms-1 w-[10px] after:absolute before:absolute after:h-[1.5px] after:w-[6px] before:h-[1.5px] before:w-[6px] after:bg-current before:bg-current after:transition-transform-200 before:transition-transform-200 after:content-empty before:content-empty after:-translate-y-[1px] before:-translate-y-[1px]', {
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
  transition: color var(--mrr-motion-feedback) var(--mrr-ease-out), background-color var(--mrr-motion-feedback) var(--mrr-ease-out), border-color var(--mrr-motion-feedback) var(--mrr-ease-out);
}

.menu-item-container:hover {
  color: var(--text-primary);
  background: var(--mrr-navigation-hover);
}

.menu-item-container.active,
.menu-item.active .menu-item-container {
  font-weight: 600;
  color: var(--mrr-navigation-active-foreground);
  background: var(--mrr-navigation-active);
  border-color: var(--mrr-navigation-active-border);
}

.menu-item-container-icon {
  flex: 0 0 auto;
  color: currentcolor;
  transform-origin: center;
  transition: color var(--mrr-motion-feedback) var(--mrr-ease-out), transform var(--mrr-motion-feedback) var(--mrr-ease-out);
}

.menu-error-badge {
  flex: 0 0 auto;
  min-width: 18px;
  padding: 0 5px;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  color: #fff;
  text-align: center;
  background: var(--el-color-danger);
  border-radius: 9px;
}

.menu-item-container:hover .menu-item-container-icon {
  transform: var(--mrr-motion-decor, scale(1.04));
}

.menu-item-container.active .menu-item-container-icon,
.menu-item.active .menu-item-container-icon {
  transform: var(--mrr-motion-decor, scale(1.06));
}

.menu-item-container:active .menu-item-container-icon {
  transform: var(--mrr-motion-decor, scale(0.92));
}

.menu-leading-control {
  position: relative;
  flex: 0 0 17px;
  width: 17px;
  height: 17px;
}

.menu-leading-control-icon,
.menu-leading-control-arrow {
  position: absolute;
  inset: 0;
  margin: auto;
  transition: opacity var(--mrr-motion-move) var(--mrr-ease-out), transform var(--mrr-motion-move) var(--mrr-ease-out);
}

.menu-leading-control-arrow {
  width: 7px;
  height: 7px;
  color: currentcolor;
  border-right: 1.5px solid currentcolor;
  border-bottom: 1.5px solid currentcolor;
  opacity: 0;
  transform: translateX(-3px) rotate(-45deg);
}

.menu-leading-control-arrow.is-expanded {
  transform: translateX(-3px) translateY(-1px) rotate(45deg);
}

.menu-item-container:hover .menu-leading-control-icon {
  opacity: 0;

  /* 只有缩放是装饰；-3px 位移是箭头与图标的重合对位，必须保留。 */
  transform: scale(0.82) translateX(-3px) !important;
}

/*
 * 展开箭头不是「图标 + 位移动效」：它用右边框 + 下边框拼出来，靠 rotate() 才成
 * 箭头形状，rotate 本身就是形态，关动效时不能清零，所以不套 --mrr-motion-decor。
 */
.menu-item-container:hover .menu-leading-control-arrow {
  opacity: 1;
  transform: rotate(-45deg);
}

.menu-item-container:hover .menu-leading-control-arrow.is-expanded {
  transform: translateY(-1px) rotate(45deg);
}

@media (prefers-reduced-motion: reduce) {
  .menu-leading-control-icon,
  .menu-leading-control-arrow {
    transition: none;
  }

  .menu-item-container:hover .menu-leading-control-icon {
    transform: scale(0.82) !important;
  }

  .menu-leading-control-arrow {
    transform: rotate(-45deg);
  }

  .menu-leading-control-arrow.is-expanded {
    transform: translateY(-1px) rotate(45deg);
  }
}
</style>
