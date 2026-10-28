<script setup lang="ts">
import type { Menu } from '@/types/global'
import { useFocus } from '@vueuse/core'
import hotkeys from 'hotkeys-js'
import Breadcrumb from '@/layouts/components/Breadcrumb/index.vue'
import BreadcrumbItem from '@/layouts/components/Breadcrumb/item.vue'
import { resolveRoutePath } from '@/utils'

defineOptions({
  name: 'NavSearchModal',
})

const isShow = defineModel<boolean>({
  default: false,
})

const router = useRouter()
const settingsStore = useSettingsStore()
const routeStore = useRouteStore()
const menuStore = useMenuStore()

interface listTypes {
  path: string
  icon?: string
  title?: string | (() => string)
  link?: string
}

const searchInput = ref('')
const searchInputRef = useTemplateRef('searchInputRef')
const { focused: searchInputFocused } = useFocus(searchInputRef)
const sourceList = ref<listTypes[]>([])
const actived = ref(0)

const searchResultRef = useTemplateRef('searchResultRef')
const searchResultItemRef = useTemplateRef<HTMLElement[]>('searchResultItemRef')

function getResultTitle(item: listTypes) {
  return (typeof item.title === 'function' ? item.title() : item.title) ?? '[ 无标题 ]'
}

function getResultBreadcrumbs(item: listTypes) {
  const title = getResultTitle(item)
  return routeStore.getRouteMatchedByPath(item.path).filter((route) => {
    const breadcrumbTitle = typeof route.meta?.title === 'function' ? route.meta.title() : route.meta?.title
    return (breadcrumbTitle ?? '[ 无标题 ]') !== title
  })
}

const resultList = computed(() => {
  const keyword = searchInput.value.trim()
  if (!keyword) {
    return sourceList.value
  }

  return sourceList.value.filter((item) => {
    const titleMatches = item.title
      ? (typeof item.title === 'function' ? item.title() : item.title).includes(keyword)
      : false
    const pathMatches = item.path.includes(keyword)
    const breadcrumbMatches = routeStore.getRouteMatchedByPath(item.path).some((route) => {
      const title = typeof route.meta?.title === 'function' ? route.meta.title() : route.meta?.title
      return title?.includes(keyword) ?? false
    })

    return titleMatches || pathMatches || breadcrumbMatches
  })
})

watch(() => isShow.value, (val) => {
  if (val) {
    searchInput.value = ''
    actived.value = 0
    // 当搜索显示的时候绑定上、下、回车快捷键，隐藏的时候再解绑。另外当 input 处于 focus 状态时，采用 vue 来绑定键盘事件
    hotkeys('up', keyUp)
    hotkeys('down', keyDown)
    hotkeys('enter', keyEnter)
    hotkeys('esc', (e) => {
      if (settingsStore.settings.navSearch.enableHotkeys) {
        e.preventDefault()
        isShow.value = false
      }
    })
  }
  else {
    hotkeys.unbind('up', keyUp)
    hotkeys.unbind('down', keyDown)
    hotkeys.unbind('enter', keyEnter)
    hotkeys.unbind('esc')
  }
})
watch(() => resultList.value, () => {
  actived.value = 0
  handleScroll()
})

onMounted(() => {
  initSourceList()
  hotkeys('command+k, ctrl+k', (e) => {
    if (settingsStore.settings.navSearch.enableHotkeys) {
      e.preventDefault()
      isShow.value = true
    }
  })
})

onUnmounted(() => {
  hotkeys.unbind('command+k, ctrl+k')
})

function initSourceList() {
  sourceList.value = []
  menuStore.allMenus.forEach((item) => {
    getSourceList(item.children)
  })
}

function hasChildren(item: Menu.recordRaw) {
  let flag = true
  if (item.children?.every(i => i.meta?.menu === false)) {
    flag = false
  }
  return flag
}
function getSourceList(arr: Menu.recordRaw[], basePath?: string, icon?: string) {
  arr.forEach((item) => {
    if (item.meta?.menu !== false) {
      if (item.children && hasChildren(item)) {
        getSourceList(item.children, resolveRoutePath(basePath, item.path), item.meta?.icon ?? icon)
      }
      else {
        sourceList.value.push({
          path: resolveRoutePath(basePath, item.path),
          icon: item.meta?.icon ?? icon,
          title: item.meta?.title,
          link: item.meta?.link,
        })
      }
    }
  })
}

function keyUp() {
  if (resultList.value.length) {
    actived.value -= 1
    if (actived.value < 0) {
      actived.value = resultList.value.length - 1
    }
    handleScroll()
  }
}
function keyDown() {
  if (resultList.value.length) {
    actived.value += 1
    if (actived.value > resultList.value.length - 1) {
      actived.value = 0
    }
    handleScroll()
  }
}
function keyEnter() {
  searchResultItemRef.value?.find(item => Number.parseInt(item.dataset.index!) === actived.value)?.click()
}
function handleScroll() {
  if (searchResultRef.value?.ref?.el?.viewportElement) {
    const contentDom = searchResultRef.value.ref.el.viewportElement
    let scrollTo = 0
    if (resultList.value.length > 0) {
      scrollTo = contentDom.scrollTop
      const activedOffsetTop = searchResultItemRef.value?.find(item => Number.parseInt(item.dataset.index!) === actived.value)?.offsetTop ?? 0
      const activedClientHeight = searchResultItemRef.value?.find(item => Number.parseInt(item.dataset.index!) === actived.value)?.clientHeight ?? 0
      const searchScrollTop = contentDom.scrollTop
      const searchClientHeight = contentDom.clientHeight
      if (activedOffsetTop + activedClientHeight > searchScrollTop + searchClientHeight) {
        scrollTo = activedOffsetTop + activedClientHeight - searchClientHeight
      }
      else if (activedOffsetTop <= searchScrollTop) {
        scrollTo = activedOffsetTop - 16
      }
    }
    contentDom.scrollTo({
      top: scrollTo,
    })
  }
}

function pageJump(path: listTypes['path'], link: listTypes['link']) {
  if (link) {
    window.open(link, '_blank')
  }
  else {
    router.push(path)
  }
  isShow.value = false
}
</script>

<template>
  <FaModal v-model="isShow" border :footer="settingsStore.mode === 'pc'" :closable="false" overlay-class="mrr-vscode-overlay mrr-vscode-search-overlay" class="mrr-vscode-glass mrr-vscode-search w-full lg-max-w-[40rem]" content-class="flex flex-col p-0 min-h-auto" header-class="p-0" footer-class="p-0" @opened="searchInputFocused = true">
    <template #header>
      <div class="h-12 flex flex-shrink-0 items-center">
        <div class="h-full w-20 flex-center flex-shrink-0 pl-4">
          <FaIcon name="i-lucide:search" class="size-5 text-foreground/30" />
        </div>
        <input ref="searchInputRef" v-model="searchInput" placeholder="搜索页面，支持标题、URL模糊查询" class="h-full min-w-0 w-full border-0 rounded-md bg-transparent pl-4 text-base text-foreground focus-outline-none placeholder-foreground/30" @keydown.esc.prevent="isShow = false" @keydown.up.prevent="keyUp" @keydown.down.prevent="keyDown" @keydown.enter.prevent="keyEnter">
        <div v-if="settingsStore.mode === 'mobile'" class="h-full w-14 flex-center border-s">
          <FaIcon name="i-lucide:x" class="size-4" @click="isShow = false" />
        </div>
      </div>
    </template>
    <template #footer>
      <div class="w-full flex justify-between px-4 py-3">
        <div class="flex gap-8">
          <div class="inline-flex items-center gap-1 text-xs">
            <FaKbd>
              <FaIcon name="i-lucide:corner-down-left" class="size-4" />
            </FaKbd>
            <span>访问</span>
          </div>
          <div class="inline-flex items-center gap-1 text-xs">
            <FaKbd>
              <FaIcon name="i-lucide:chevron-up" class="size-4" />
            </FaKbd>
            <FaKbd>
              <FaIcon name="i-lucide:chevron-down" class="size-4" />
            </FaKbd>
            <span>切换</span>
          </div>
        </div>
        <div v-if="settingsStore.settings.navSearch.enableHotkeys" class="inline-flex items-center gap-1 text-xs">
          <FaKbd>
            ESC
          </FaKbd>
          <span>退出</span>
        </div>
      </div>
    </template>
    <FaScrollArea ref="searchResultRef">
      <template v-if="resultList.length > 0">
        <div v-for="(item, index) in resultList" ref="searchResultItemRef" :key="item.path" class="p-4" :data-index="index" @click="pageJump(item.path, item.link)" @mouseover="actived = index">
          <a class="flex cursor-pointer items-center border rounded-lg" :class="{ 'bg-accent': index === actived }">
            <FaIcon v-if="item.icon" :name="item.icon" class="size-5 basis-16 transition" :class="{ 'scale-120 text-primary': index === actived }" />
            <div class="flex flex-1 flex-col gap-1 truncate border-s px-4 py-1.5">
              <div class="truncate text-start text-base font-bold">{{ getResultTitle(item) }}</div>
              <Breadcrumb v-if="getResultBreadcrumbs(item).length" class="truncate">
                <BreadcrumbItem v-for="(bc, bcIndex) in getResultBreadcrumbs(item)" :key="bcIndex" class="text-xs">
                  {{ (typeof bc.meta?.title === 'function' ? bc.meta?.title() : bc.meta?.title) ?? '[ 无标题 ]' }}
                </BreadcrumbItem>
              </Breadcrumb>
            </div>
          </a>
        </div>
      </template>
      <template v-else-if="searchInput === ''">
        <div class="h-full flex-col-center py-6 text-secondary-foreground/50">
          <FaIcon name="i-lucide:smile" class="size-10" />
          <p class="m-2 text-base">
            输入你要搜索的导航
          </p>
        </div>
      </template>
      <template v-else>
        <div class="h-full flex-col-center py-6 text-secondary-foreground/50">
          <FaIcon name="i-lucide:meh" class="size-10" />
          <p class="m-2 text-base">
            没有找到你想要的
          </p>
        </div>
      </template>
    </FaScrollArea>
  </FaModal>
</template>
