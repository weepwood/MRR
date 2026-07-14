<script setup lang="ts">
import type { Tabbar } from '#/global'
import { useMagicKeys } from '@vueuse/core'
import hotkeys from 'hotkeys-js'
import { toast } from 'vue-sonner'
import { useSlots } from '@/slots'

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

watch(() => route, (val) => {
  if (settingsStore.settings.tabbar.enable) {
    tabbarStore.add(val)
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
  deep: true,
})
function tabbarScrollTip() {
  if (tabContainerRef.value?.$el.clientWidth > (tabsRef.value?.ref?.$el.clientWidth ?? 0) && localStorage.getItem('tabbarScrollTip') === undefined) {
    localStorage.setItem('tabbarScrollTip', '')
    const tips = toast.info('温馨提示', {
      description: '标签栏数量超过展示区域范围，可以将鼠标移到标签栏上，通过鼠标滚轮滑动浏览',
      position: 'top-center',
      duration: Infinity,
      action: {
        label: '知道了',
        onClick: () => toast.dismiss(tips),
      },
    })
  }
}
function contextMenuItems(routeItem: Tabbar.recordRaw) {
  return [
    [
      {
        label: '重新加载',
        icon: 'i-ri:refresh-line',
        disabled: routeItem.tabId !== activedTabId.value,
        handle: () => mainPage.reload(),
      },
      {
        label: '关闭标签页',
        icon: 'i-ri:close-line',
        disabled: tabbarStore.list.length <= 1,
        handle: () => tabbar.closeById(routeItem.tabId),
      },
    ],
    [
      {
        label: '关闭其他标签页',
        icon: 'i-mdi:close',
        disabled: !tabbar.checkCloseOtherSide(routeItem.tabId),
        handle: () => tabbar.closeOtherSide(routeItem.tabId),
      },
      {
        label: '关闭左侧标签页',
        icon: 'i-mdi:arrow-expand-left',
        disabled: !tabbar.checkCloseLeftSide(routeItem.tabId),
        handle: () => tabbar.closeLeftSide(routeItem.tabId),
      },
      {
        label: '关闭右侧标签页',
        icon: 'i-mdi:arrow-expand-right',
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
            }" @click="router.push(element.fullPath)"
          >
            <FaContextMenu :items="contextMenuItems(element)">
              <div class="size-full">
                <div class="tab-dividers" />
                <div class="tab-background" />
                <FaTooltip :delay="1000" side="bottom">
                  <div class="tab-content">
                    <div :key="element.tabId" class="title">
                      <FaIcon v-if="settingsStore.settings.tabbar.enableIcon && element.icon" :name="element.icon" class="icon" />
                      {{ typeof element?.title === 'function' ? element.title() : element.title }}
                    </div>
                    <div v-if="tabbarStore.list.length > 1" class="action-icon" @click.stop="tabbar.closeById(element.tabId)">
                      <FaIcon name="i-ri:close-fill" />
                    </div>
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
  padding: 0 10px;
  background: transparent;

  .tabbar-container {
    position: relative;
    flex: 1;
    height: 100%;
    min-width: 0;

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
            transition: background-color 140ms ease, border-color 140ms ease, box-shadow 140ms ease;
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
              gap: 6px;
              align-items: center;
              height: 100%;
              min-width: 0;
              padding: 0 10px;
              margin-right: 8px;
              overflow: hidden;
              font-weight: 500;
              color: var(--text-secondary);
              text-overflow: ellipsis;
              white-space: nowrap;
              transition: color 140ms ease, margin-right 140ms ease;

              &:has(+ .action-icon) {
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
              width: 20px;
              height: 20px;
              font-size: 11px;
              color: var(--text-tertiary);
              background: transparent;
              border-radius: var(--mrr-radius-sm);
              opacity: 0;
              place-items: center;
              transform: translateY(-50%);
              transition: color 140ms ease, background-color 140ms ease, opacity 140ms ease;
            }

            .action-icon:hover {
              color: var(--text-primary);
              background: var(--mrr-navigation-hover);
            }

            .hotkey-number {
              font-weight: 650;
              opacity: 1;
              background: var(--mrr-secondary);
              border: 1px solid var(--mrr-border);
            }
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
  .tabbar-move,
  .tabbar-enter-active,
  .tabbar-leave-active {
    transition: opacity 160ms ease, transform 160ms ease;
  }

  .tabbar-enter-from,
  .tabbar-leave-to {
    opacity: 0;
    transform: translateY(6px);
  }

  .tabbar-leave-active {
    position: absolute !important;
  }
}

@media (prefers-reduced-motion: reduce) {
  .tabs .tabbar-move,
  .tabs .tabbar-enter-active,
  .tabs .tabbar-leave-active,
  .tab-background,
  .title,
  .action-icon {
    transition: none !important;
  }
}
</style>