<script setup lang="ts">
import type { Tabbar } from '#/global'
import { useMagicKeys } from '@vueuse/core'
import hotkeys from 'hotkeys-js'
import { useSlots } from '@/slots'
import { notify } from '@/utils/notification'

defineOptions({
  name: 'Tabbar',
})

const route = useRoute()
const router = useRouter()

const settingsStore = useSettingsStore()
const tabbarStore = useTabbarStore()

const tabbar = useTabbar()
const mainPage = useMainPage()

const keys = useMagicKeys({ reactive: true })

const activedTabId = computed(() => tabbar.getId())

const tabsRef = useTemplateRef('tabsRef')
const tabContainerRef = useTemplateRef('tabContainerRef')
const tabRef = useTemplateRef<HTMLElement[]>('tabRef')

// 只在导航发生时同步标签。原实现 deep 监听整个 route 对象，会递归遍历
// matched 中的路由记录、组件定义与 meta，每次响应式变动都要走一遍。
watch(() => route.fullPath, () => {
  if (settingsStore.settings.tabbar.enable) {
    tabbarStore.add(route)
    nextTick(() => {
      const index = tabbarStore.list.findIndex(item => item.tabId === activedTabId.value)
      if (index !== -1) {
        const tabEl = tabRef.value?.find(item => Number.parseInt(item.dataset.index!) === index)
        const containerEl = tabsRef.value?.ref?.$el
        if (tabEl && containerEl) {
          const tabLeft = tabEl.offsetLeft
          const tabWidth = tabEl.offsetWidth
          const containerWidth = containerEl.clientWidth
          const scrollLeft = tabLeft - (containerWidth - tabWidth) / 2
          tabsRef.value?.scrollTo(scrollLeft)
        }
        tabbarScrollTip()
      }
    })
  }
}, {
  immediate: true,
})
function tabbarScrollTip() {
  // localStorage.getItem 缺失时返回 null 而不是 undefined；原判断恒为 false，提示从未出现过。
  if (tabContainerRef.value?.$el.clientWidth > (tabsRef.value?.ref?.$el.clientWidth ?? 0) && localStorage.getItem('tabbarScrollTip') === null) {
    localStorage.setItem('tabbarScrollTip', '')
    notify.info('温馨提示', {
      description: '标签栏数量超过展示区域范围，可以将鼠标移到标签栏上，通过鼠标滚轮滑动浏览',
      duration: 0,
      showClose: true,
    })
  }
}
function contextMenuItems(routeItem: Tabbar.recordRaw) {
  return [
    [
      {
        label: '重新加载',
        icon: 'i-lucide:refresh-cw',
        disabled: routeItem.tabId !== activedTabId.value,
        handle: () => mainPage.reload(),
      },
      {
        label: '关闭标签页',
        icon: 'i-lucide:x',
        disabled: tabbarStore.list.length <= 1,
        handle: () => tabbar.closeById(routeItem.tabId),
      },
    ],
    [
      {
        label: '关闭其他标签页',
        icon: 'i-lucide:x',
        disabled: !tabbar.checkCloseOtherSide(routeItem.tabId),
        handle: () => tabbar.closeOtherSide(routeItem.tabId),
      },
      {
        label: '关闭左侧标签页',
        icon: 'i-lucide:arrow-left-to-line',
        disabled: !tabbar.checkCloseLeftSide(routeItem.tabId),
        handle: () => tabbar.closeLeftSide(routeItem.tabId),
      },
      {
        label: '关闭右侧标签页',
        icon: 'i-lucide:arrow-right-to-line',
        disabled: !tabbar.checkCloseRightSide(routeItem.tabId),
        handle: () => tabbar.closeRightSide(routeItem.tabId),
      },
    ],
  ]
}

const visibleTabIndex = ref<number[]>([])
function getVisibleTabs() {
  const containerWidth = tabsRef.value?.ref?.$el.clientWidth ?? 0
  const scrollLeft = tabsRef.value?.ref?.el?.viewportElement?.scrollLeft ?? 0
  visibleTabIndex.value = []
  if (tabRef.value) {
    for (let i = 0; i < tabRef.value.length; i++) {
      const tab = tabRef.value[i]
      const tabLeft = tab.offsetLeft
      const tabRight = tabLeft + tab.offsetWidth
      if (tabLeft < scrollLeft + containerWidth && tabRight > scrollLeft) {
        if (i >= 0 && i < tabbarStore.list.length) {
          visibleTabIndex.value.push(i)
        }
      }
    }
  }
}
function getVisibleTabIndex(arrayIndex: number) {
  return visibleTabIndex.value.findIndex(visibleTab => visibleTab === arrayIndex) ?? -1
}
watch(() => keys.alt, (val) => {
  if (val) {
    getVisibleTabs()
  }
})

onMounted(() => {
  hotkeys('alt+left,alt+right,alt+w,alt+1,alt+2,alt+3,alt+4,alt+5,alt+6,alt+7,alt+8,alt+9,alt+0', (e, handle) => {
    if (settingsStore.settings.tabbar.enable && settingsStore.settings.tabbar.enableHotkeys) {
      e.preventDefault()
      switch (handle.key) {
        case 'alt+left':
          if (tabbarStore.list[0].tabId !== activedTabId.value) {
            const index = tabbarStore.list.findIndex(item => item.tabId === activedTabId.value)
            router.push(tabbarStore.list[index - 1].fullPath)
          }
          break
        case 'alt+right':
          if (tabbarStore.list.at(-1)?.tabId !== activedTabId.value) {
            const index = tabbarStore.list.findIndex(item => item.tabId === activedTabId.value)
            router.push(tabbarStore.list[index + 1].fullPath)
          }
          break
        case 'alt+w':
          tabbar.closeById(activedTabId.value)
          break
        case 'alt+1':
        case 'alt+2':
        case 'alt+3':
        case 'alt+4':
        case 'alt+5':
        case 'alt+6':
        case 'alt+7':
        case 'alt+8':
        case 'alt+9':
        {
          const number = Number(handle.key.split('+')[1])
          if (visibleTabIndex.value[number - 1] !== undefined) {
            router.push(tabbarStore.list[visibleTabIndex.value[number - 1]].fullPath)
          }
          break
        }
        case 'alt+0':
          {
            const last = tabbarStore.list.at(-1)
            if (last) {
              router.push(last.fullPath)
            }
          }
          break
      }
    }
  })
})
onUnmounted(() => {
  hotkeys.unbind('alt+left,alt+right,alt+w,alt+1,alt+2,alt+3,alt+4,alt+5,alt+6,alt+7,alt+8,alt+9,alt+0')
})
</script>

<template>
  <div class="tabbar">
    <component :is="useSlots('tabbar-start')" />
    <div class="tabbar-container">
      <FaScrollArea ref="tabsRef" :scrollbar="false" mask horizontal gradient-color="var(--mrr-topbar-bg)" class="tabs">
        <TransitionGroup ref="tabContainerRef" name="tabbar" tag="div" class="tab-container">
          <div
            v-for="(element, index) in tabbarStore.list" :key="element.tabId"
            ref="tabRef" :data-index="index" class="tab" :class="{
              actived: element.tabId === activedTabId,
            }" role="link" tabindex="0" :aria-current="element.tabId === activedTabId ? 'page' : undefined"
            @click="router.push(element.fullPath)"
            @keydown.enter.self.prevent="router.push(element.fullPath)"
          >
            <FaContextMenu :items="contextMenuItems(element)">
              <div class="size-full">
                <div class="tab-dividers" />
                <div class="tab-background" />
                <FaTooltip :delay="1000" side="bottom">
                  <div class="tab-content">
                    <div :key="element.tabId" class="title" :class="{ 'title--has-action': tabbarStore.list.length > 1 }">
                      <FaIcon v-if="settingsStore.settings.tabbar.enableIcon && element.icon" :name="element.icon" class="icon" />
                      {{ typeof element?.title === 'function' ? element.title() : element.title }}
                    </div>
                    <button
                      v-if="tabbarStore.list.length > 1" type="button" class="action-icon"
                      :aria-label="`关闭标签页：${typeof element?.title === 'function' ? element.title() : element.title}`"
                      @click.stop="tabbar.closeById(element.tabId)" @keydown.enter.stop
                    >
                      <FaIcon name="i-lucide:x" />
                    </button>
                    <div v-show="keys.alt && getVisibleTabIndex(index) >= 0 && getVisibleTabIndex(index) < 9" class="hotkey-number">
                      {{ getVisibleTabIndex(index) + 1 }}
                    </div>
                  </div>
                  <template #content>
                    <div class="text-sm">
                      {{ typeof element?.title === 'function' ? element.title() : element.title }}
                    </div>
                    <div class="text-accent-foreground/50">
                      {{ element.fullPath }}
                    </div>
                  </template>
                </FaTooltip>
              </div>
            </FaContextMenu>
          </div>
        </TransitionGroup>
      </FaScrollArea>
    </div>
    <component :is="useSlots('tabbar-end')" />
  </div>
</template>

<style scoped>
.tabbar {
  position: relative;
  display: flex;
  align-items: center;
  height: var(--g-tabbar-height);
  padding: 0 var(--mrr-space-3);
  background: transparent;

  .tabbar-container {
    position: relative;
    flex: 1;
    min-width: 0;
    height: 100%;

    .tabs {
      position: absolute;
      inset-inline: 0;
      height: 100%;
      white-space: nowrap;

      .tab-container {
        display: inline-flex;
        gap: 4px;
        align-items: center;
        height: 100%;
        padding: 5px 0;

        .tab {
          position: relative;
          display: inline-flex;
          width: 146px;
          height: 34px;
          font-size: 12px;
          vertical-align: bottom;
          pointer-events: none;
          cursor: pointer;

          * {
            user-select: none;
          }

          .tab-dividers {
            display: none;
          }

          .tab-background {
            position: absolute;
            inset: 0;
            z-index: 0;
            pointer-events: none;
            background: transparent;
            border: 1px solid transparent;
            border-radius: var(--mrr-radius-md);
            transition: background-color var(--mrr-motion-feedback) var(--mrr-ease-out), border-color var(--mrr-motion-feedback) var(--mrr-ease-out), box-shadow var(--mrr-motion-feedback) var(--mrr-ease-out);
          }

          .tab-content {
            position: relative;
            z-index: 1;
            display: flex;
            align-items: center;
            width: 100%;
            height: 100%;
            pointer-events: all;

            .title {
              display: flex;
              flex: 1;
              gap: var(--mrr-space-2);
              align-items: center;
              min-width: 0;
              height: 100%;
              padding: 0 var(--mrr-space-3);
              margin-right: var(--mrr-space-2);
              overflow: hidden;
              text-overflow: ellipsis;
              font-weight: 500;
              color: var(--text-secondary);
              white-space: nowrap;
              transition: color var(--mrr-motion-feedback) var(--mrr-ease-out);

              /* 存在关闭按钮时为其预留间距。原实现用 `:has(+ .action-icon)` 探测，
                 需要 Chrome 105+；改为由模板显式声明状态类，基线浏览器行为一致。 */
              &.title--has-action {
                margin-right: 26px;
              }

              .icon {
                flex: 0 0 auto;
                width: 14px;
                height: 14px;
                font-size: 14px;
              }
            }

            .action-icon,
            .hotkey-number {
              position: absolute;
              inset-inline-end: 7px;
              top: 50%;
              z-index: 2;
              display: grid;
              place-items: center;
              width: 20px;
              height: 20px;
              padding: 0;
              font-size: 11px;
              color: var(--text-tertiary);
              cursor: pointer;
              background: transparent;
              border: 0;
              border-radius: var(--mrr-radius-sm);
              opacity: 0;
              transform: translateY(-50%);
              transition: color var(--mrr-motion-feedback) var(--mrr-ease-out), background-color var(--mrr-motion-feedback) var(--mrr-ease-out), opacity var(--mrr-motion-feedback) var(--mrr-ease-out);
            }

            .action-icon:hover {
              color: var(--text-primary);
              background: var(--mrr-navigation-hover);
            }

            /* 键盘用户也要能看到并操作关闭按钮（原实现只在鼠标悬停时出现）。 */
            .action-icon:focus-visible {
              color: var(--text-primary);
              outline: 2px solid var(--mrr-ring);
              outline-offset: 1px;
              opacity: 1;
            }

            .hotkey-number {
              font-weight: 650;
              background: var(--mrr-secondary);
              border: 1px solid var(--mrr-border);
              opacity: 1;
            }
          }

          &:focus-visible {
            outline: none;

            .tab-background {
              outline: 2px solid var(--mrr-ring);
              outline-offset: -2px;
            }
          }

          &:focus-within .action-icon {
            opacity: 1;
          }

          /*
           * 关闭标签 = 宽度收拢、右侧标签顺势左移；新开标签 = 从 0 宽度展开。
           * 原实现离场时设 position: absolute，在 flex 容器里会跳到最左端再淡出，
           * 且使用纵向 translateY，与横向排列的标签栏方向不一致。
           */
          &.tabbar-enter-active,
          &.tabbar-leave-active {
            overflow: hidden;
            transition:
              width var(--mrr-motion-move) var(--mrr-ease-out),
              margin-right var(--mrr-motion-move) var(--mrr-ease-out),
              opacity var(--mrr-motion-feedback) var(--mrr-ease-out);
          }

          &.tabbar-enter-from,
          &.tabbar-leave-to {
            width: 0;
            margin-right: -4px;
            opacity: 0;
          }

          &:not(.actived):hover {
            .tab-background {
              background: color-mix(in srgb, var(--mrr-accent) 78%, transparent);
            }

            .title {
              color: var(--text-primary);
            }

            .action-icon {
              opacity: 1;
            }
          }

          &.actived {
            .tab-background {
              background: var(--mrr-card);
              border-color: var(--mrr-border);
              box-shadow: var(--mrr-shadow-xs);
            }

            .title {
              font-weight: 600;
              color: var(--text-primary);
            }

            .action-icon {
              opacity: 1;
            }
          }
        }
      }
    }
  }
}

.tabs {
  /* 拖拽/重排时其余标签平移到新位置。 */
  .tabbar-move {
    transition: transform var(--mrr-motion-move) var(--mrr-ease-out);
  }
}
</style>
