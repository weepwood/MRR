<script setup lang="ts">
import type { SubMenuProps } from './types'
import { useResizeObserver } from '@vueuse/core'
import Item from './item.vue'
import { rootMenuInjectionKey } from './types'

defineOptions({
  name: 'SubMenu',
})

const props = withDefaults(
  defineProps<SubMenuProps>(),
  {
    level: 0,
  },
)

const itemRef = useTemplateRef('itemRef')
const subMenuRef = useTemplateRef('subMenuRef')
const rootMenu = inject(rootMenuInjectionKey)!

const index = props.uniqueKey.at(-1)!
const popupId = useId()
const branch = computed(() => props.uniqueKey.map(key => encodeURIComponent(key)).join(' '))

const opened = computed(() => rootMenu.openedMenus.includes(props.uniqueKey.at(-1)!))

// Keep inline branches mounted so reversing a CSS height transition starts
// at its current rendered height, including on browsers without calc-size().
const inlineBranchRef = ref<HTMLElement>()
const inlineContentRef = ref<HTMLElement>()
const inlineHeight = ref<number>()
useResizeObserver(inlineContentRef, () => {
  inlineHeight.value = inlineContentRef.value?.getBoundingClientRect().height
})
const inlineStyle = computed(() => ({
  height: opened.value ? inlineHeight.value === undefined ? 'auto' : `${inlineHeight.value}px` : '0px',
}))
watch(opened, (value) => {
  if (!value && !rootMenu.isMenuPopup && inlineBranchRef.value?.contains(document.activeElement)) {
    itemRef.value?.ref?.querySelector('button')?.focus({ preventScroll: true })
  }
}, { flush: 'sync' })

const transitionEvent = {
  enter(el: HTMLElement) {
    if (el.offsetHeight > window.innerHeight) {
      el.style.height = `${window.innerHeight}px`
    }
  },
  beforeLeave(el: HTMLElement) {
    el.style.maxHeight = `${el.offsetHeight}px`
    el.style.overflow = 'hidden'
  },
  leave(el: HTMLElement) {
    el.style.maxHeight = '0'
  },
  afterLeave(el: HTMLElement) {
    el.style.maxHeight = ''
    el.style.overflow = ''
  },
}

const transitionClass = {
  enterActiveClass: 'ease-mrr-out duration-mrr-move',
  enterFromClass: 'opacity-0 scale-98 translate-y-1',
  enterToClass: 'opacity-100 scale-100 translate-y-0',
  leaveActiveClass: 'ease-mrr-in duration-mrr-feedback',
  leaveFromClass: 'opacity-100 scale-100 translate-y-0',
  leaveToClass: 'opacity-0 scale-98 translate-y-1',
}

const hasChildren = computed(() => props.menu.children?.some(item => item.meta?.menu !== false) ?? false)

let hoverTimer: number | undefined

async function openPopup(moveFocus = false) {
  const focusedBranch = getMenuBranch(document.activeElement, '.saas-popup-menu')
  if (!moveFocus && focusedBranch && !isInBranch(branch.value, focusedBranch) && !isInBranch(focusedBranch, branch.value)) {
    return
  }

  rootMenu.openMenu(index, props.uniqueKey)
  await nextTick()
  const anchor = itemRef.value?.ref
  const popup = subMenuRef.value?.$el as HTMLElement | undefined
  if (!opened.value || !anchor || !popup) {
    return
  }

  const { top, left, right, bottom } = anchor.getBoundingClientRect()
  const opensToSide = rootMenu.props.mode === 'vertical' || props.level !== 0
  const popupWidth = popup.offsetWidth
  const popupHeight = popup.offsetHeight
  const viewportWidth = document.documentElement.clientWidth
  const popupLeft = opensToSide
    ? right + popupWidth <= viewportWidth ? right : left - popupWidth
    : left
  const popupTop = Math.max(0, opensToSide ? Math.min(top, window.innerHeight - popupHeight) : bottom)
  popup.style.top = `${String(popupTop)}px`
  popup.style.left = `${String(Math.max(0, Math.min(popupLeft, viewportWidth - popupWidth)))}px`
  popup.style.maxHeight = `${String(Math.max(0, window.innerHeight - popupTop))}px`

  if (moveFocus) {
    popup.querySelector<HTMLElement>('.mrr-navigation-control')?.focus()
  }
}

function closePopup(restoreFocus: boolean) {
  clearTimeout(hoverTimer)
  rootMenu.closeMenu(index)
  if (restoreFocus) {
    itemRef.value?.ref?.querySelector('button')?.focus()
  }
}

function handleClick(event: MouseEvent) {
  if (hasChildren.value && rootMenu.isMenuPopup) {
    clearTimeout(hoverTimer)
    if (opened.value && event.detail === 0) {
      void openPopup(true)
    }
    else if (opened.value) {
      closePopup(false)
    }
    else {
      void openPopup(event.detail === 0)
    }
  }
  else if (hasChildren.value) {
    rootMenu.handleSubMenuClick(index, props.uniqueKey)
  }
  else if (!props.menu.meta?.link) {
    rootMenu.handleMenuItemClick(index)
  }
}

function handleEscape(event: KeyboardEvent) {
  if (rootMenu.isMenuPopup && opened.value) {
    event.stopPropagation()
    closePopup(true)
  }
}

function getMenuBranch(target: EventTarget | null, selector: string) {
  return target instanceof Element
    ? target.closest<HTMLElement>(selector)?.dataset.menuBranch ?? ''
    : ''
}

function isInBranch(path: string, branch: string) {
  return path === branch || path.startsWith(`${branch} `)
}

function closeAbandonedMenus(target: EventTarget | null) {
  const focusBranch = getMenuBranch(target, '.saas-popup-menu')
  for (let level = props.uniqueKey.length; level > 0; level--) {
    const prefix = props.uniqueKey.slice(0, level).map(key => encodeURIComponent(key)).join(' ')
    if (isInBranch(rootMenu.mouseInMenu, prefix) || isInBranch(focusBranch, prefix)) {
      return
    }
    rootMenu.closeMenu(props.uniqueKey[level - 1]!)
  }
}

function handleMouseenter(event: MouseEvent) {
  if (!rootMenu.isMenuPopup) {
    return
  }
  rootMenu.mouseInMenu = branch.value
  clearTimeout(hoverTimer)
  if (hasChildren.value && !opened.value && event.currentTarget === itemRef.value?.ref) {
    hoverTimer = window.setTimeout(() => void openPopup(), 240)
  }
}

function handleMouseleave(event: MouseEvent) {
  if (!rootMenu.isMenuPopup) {
    return
  }
  rootMenu.mouseInMenu = getMenuBranch(event.relatedTarget, '[data-menu-branch]')
  clearTimeout(hoverTimer)
  hoverTimer = window.setTimeout(closeAbandonedMenus, 240, document.activeElement)
}

function handleFocusout(event: FocusEvent) {
  if (rootMenu.isMenuPopup) {
    closeAbandonedMenus(event.relatedTarget)
  }
}

onBeforeUnmount(() => clearTimeout(hoverTimer))
</script>

<template>
  <Item
    ref="itemRef" :unique-key="uniqueKey" :item="menu" :level="level" :sub-menu="hasChildren" :expand="opened"
    :popup-id="hasChildren && (!rootMenu.isMenuPopup || opened) ? popupId : undefined" :data-menu-branch="rootMenu.isMenuPopup ? branch : undefined"
    @click="handleClick" @mouseenter="handleMouseenter" @mouseleave="handleMouseleave" @keydown.esc="handleEscape"
  />
  <Teleport v-if="hasChildren && rootMenu.isMenuPopup" to="body">
    <Transition v-bind="transitionClass" v-on="transitionEvent">
      <FaScrollArea
        v-if="opened" :id="popupId" ref="subMenuRef" :data-menu-branch="rootMenu.isMenuPopup ? branch : undefined" :scrollbar="false" :mask="rootMenu.isMenuPopup" class="sub-menu saas-popup-menu fixed z-3000 h-[calc-size(auto,size)] w-[220px] overscroll-contain rounded-lg p-1" @mouseenter="handleMouseenter" @mouseleave="handleMouseleave" @focusout="handleFocusout" @keydown.esc="handleEscape"
      >
        <template v-for="item in menu.children" :key="item.path ?? rootMenu.getUseId(item)">
          <SubMenu v-if="item.meta?.menu !== false" :unique-key="[...uniqueKey, item.path ?? rootMenu.getUseId(item)]" :menu="item" :level="level + 1" />
        </template>
      </FaScrollArea>
    </Transition>
  </Teleport>
  <div
    v-else-if="hasChildren" :id="popupId" ref="inlineBranchRef" class="sub-menu menu-inline-branch"
    :class="{ 'is-open': opened }" :style="inlineStyle" :aria-hidden="!opened" :inert="!opened"
  >
    <div ref="inlineContentRef" class="menu-inline-content">
      <template v-for="item in menu.children" :key="item.path ?? rootMenu.getUseId(item)">
        <SubMenu v-if="item.meta?.menu !== false" :unique-key="[...uniqueKey, item.path ?? rootMenu.getUseId(item)]" :menu="item" :level="level + 1" />
      </template>
    </div>
  </div>
</template>

<style scoped>
.menu-inline-branch {
  position: relative;
  visibility: hidden;
  flex: none;
  min-height: 0;
  overflow: hidden;
  transition: height var(--mrr-motion-overlay) var(--mrr-ease-out), visibility 0s linear var(--mrr-motion-overlay);
}

.menu-inline-branch.is-open {
  visibility: visible;
  transition-delay: 0s;
}

.menu-inline-content {
  display: flow-root;
  min-width: 0;
}

.menu-inline-branch:not(.is-open) {
  pointer-events: none;
}

:global(:root[data-motion="off"]) .menu-inline-branch {
  transition: none;
}

@media (prefers-reduced-motion: reduce) {
  .menu-inline-branch { transition-delay: 0s; }
}

.saas-popup-menu {
  max-width: 100vw;
  color: var(--mrr-popover-foreground);

  /*
   * 浮层底色：不透明令牌色。毛玻璃（backdrop-filter）已在生产 UI 整体下线，
   * 半透明底在没有模糊衬托时会让下层文字透出，故一并移除（效果仅在
   * 「UI 组件」展示页演示）。
   */
  background: var(--mrr-popover);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-lg);
  box-shadow: var(--mrr-shadow-md);
}

.saas-popup-menu :deep(.menu-item-container) {
  min-height: 34px;
  border-radius: var(--mrr-radius-md);
}
</style>
