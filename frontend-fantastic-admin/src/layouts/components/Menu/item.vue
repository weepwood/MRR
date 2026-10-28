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
const ripple = ref<{ key: number, x: number, y: number, diameter: number } | null>(null)
let rippleKey = 0
let rippleTimer: ReturnType<typeof setTimeout> | undefined

const canRipple = computed(() => !props.subMenu && props.level > 0 && !rootMenu.isMenuPopup)

function startRipple(element: HTMLElement, clientX: number, clientY: number) {
  const rect = element.getBoundingClientRect()
  const x = Math.min(Math.max(clientX - rect.left, 0), rect.width)
  const y = Math.min(Math.max(clientY - rect.top, 0), rect.height)
  const radius = Math.max(
    Math.hypot(x, y),
    Math.hypot(rect.width - x, y),
    Math.hypot(x, rect.height - y),
    Math.hypot(rect.width - x, rect.height - y),
  )
  const maxRippleDiameter = Math.max(rect.height * 1.2, 28)
  const key = ++rippleKey

  ripple.value = { key, x, y, diameter: Math.ceil(Math.min(radius * 2, maxRippleDiameter)) }

  if (rippleTimer) {
    clearTimeout(rippleTimer)
  }

  rippleTimer = setTimeout(() => {
    if (ripple.value?.key === key) {
      ripple.value = null
    }
  }, 700)
}

function handlePointerDown(event: PointerEvent) {
  if (!canRipple.value || event.button !== 0 || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) {
    return
  }

  startRipple(event.currentTarget as HTMLElement, event.clientX, event.clientY)
}

function handleClick(event: MouseEvent, navigate: (event?: MouseEvent) => void) {
  if (!props.subMenu) {
    if (canRipple.value && event.detail === 0) {
      const element = event.currentTarget as HTMLElement
      const rect = element.getBoundingClientRect()
      startRipple(element, rect.left + rect.width / 2, rect.top + rect.height / 2)
    }

    navigate(event)
  }
}

onBeforeUnmount(() => {
  if (rippleTimer) {
    clearTimeout(rippleTimer)
  }
})

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
          })" :title="typeof item.meta?.title === 'function' ? item.meta?.title() : item.meta?.title" @pointerdown="handlePointerDown" @click="handleClick($event, navigate)"
        >
          <span
            v-if="ripple && canRipple" :key="ripple.key" class="menu-item-ripple-viewport" aria-hidden="true"
          >
            <i
              v-for="wave in 2" :key="wave" class="menu-item-ripple" :class="{ 'menu-item-ripple--delayed': wave === 2 }" :style="{
                '--ripple-x': `${ripple.x}px`,
                '--ripple-y': `${ripple.y}px`,
                '--ripple-diameter': `${ripple.diameter}px`,
              }"
            />
          </span>
          <div
            :class="cn('relative z-10 inline-flex flex-1 items-center justify-center gap-[10px] pl-[calc(var(--indent-level)*16px)]', {
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
              {{ typeof item.meta?.title === 'function' ? item.meta?.title() : item.meta?.title }}
            </span>
          </div>
          <i
            v-if="subMenu && !useLeadingExpandIndicator && !(rootMenu.isMenuPopup && level === 0)" :class="cn('relative ms-1 w-[10px] after:absolute before:absolute after:h-[1.5px] after:w-[6px] before:h-[1.5px] before:w-[6px] after:bg-current before:bg-current after:transition-transform-200 before:transition-transform-200 after:content-empty before:content-empty after:-translate-y-[1px] before:-translate-y-[1px]', {
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
  transition: color var(--mrr-motion-fast) ease, background-color var(--mrr-motion-fast) ease, border-color var(--mrr-motion-fast) ease;
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
  transform-origin: center;
  transition: color var(--mrr-motion-fast) ease, transform var(--mrr-motion-fast) var(--mrr-ease-out);
}

.menu-item-container:hover .menu-item-container-icon {
  transform: scale(1.04);
}

.menu-item-container.active .menu-item-container-icon,
.menu-item.active .menu-item-container-icon {
  transform: scale(1.06);
}

.menu-item-container:active .menu-item-container-icon {
  transform: scale(0.92);
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
  transition: opacity var(--mrr-motion-normal) var(--mrr-ease-out), transform var(--mrr-motion-normal) var(--mrr-ease-out);
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

.menu-item-ripple-viewport {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  border-radius: inherit;
}

.menu-item-ripple {
  position: absolute;
  top: var(--ripple-y);
  left: var(--ripple-x);
  width: var(--ripple-diameter);
  height: var(--ripple-diameter);
  background: rgb(255 255 255 / 18%);
  border: 1.5px solid var(--color-primary);
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgb(255 255 255 / 14%);
  opacity: 0;
  transform: translate(-50%, -50%) scale(0.08);
  animation: menu-item-ripple 540ms cubic-bezier(0.2, 0.5, 0.3, 1) forwards;
}

.menu-item-ripple--delayed {
  animation-delay: 110ms;
}

@keyframes menu-item-ripple {
  0% {
    opacity: 0.36;
    transform: translate(-50%, -50%) scale(0.08);
  }

  24% {
    opacity: 0.34;
  }

  68% {
    opacity: 0.16;
  }

  100% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(1.08);
  }
}

.menu-item-container:hover .menu-leading-control-icon {
  opacity: 0;
  transform: scale(0.82) translateX(-3px) !important;
}

.menu-item-container:hover .menu-leading-control-arrow {
  opacity: 1;
  transform: rotate(-45deg);
}

.menu-item-container:hover .menu-leading-control-arrow.is-expanded {
  transform: translateY(-1px) rotate(45deg);
}

@media (prefers-reduced-motion: reduce) {
  .menu-item-ripple {
    animation: none;
  }

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
