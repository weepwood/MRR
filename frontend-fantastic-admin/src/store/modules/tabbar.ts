import type { Tabbar } from '#/global'
import type { RouteLocationNormalized } from 'vue-router'

export const useTabbarStore = defineStore(
  // 唯一ID
  'tabbar',
  () => {
    const keepAliveStore = useKeepAliveStore()

    const list = ref<Tabbar.recordRaw[]>([])
    const leaveIndex = ref(-1)

    // 添加标签页
    function add(route: RouteLocationNormalized) {
      const names: string[] = []
      route.matched.forEach((v, i) => {
        if (i > 0) {
          v.components?.default.name && names.push(v.components.default.name)
        }
      })
      const meta = route.matched.at(-1)?.meta
      const tabId = route.path
      if (route.name !== 'reload') {
        // 记录查找到的标签页
        const findTab = list.value.find((item) => {
          return item.tabId === tabId
        })
        // 新增标签页
        if (!findTab) {
          const listItem = {
            tabId,
            fullPath: route.fullPath,
            title: typeof meta?.title === 'function' ? meta.title() : meta?.title,
            icon: meta?.icon ?? route.matched?.findLast(item => item.meta?.icon)?.meta?.icon,
            name: names,
          }
          if (leaveIndex.value >= 0) {
            list.value.splice(leaveIndex.value + 1, 0, listItem)
            leaveIndex.value = -1
          }
          else {
            list.value.push(listItem)
          }
        }
      }
    }
    function computeRemoveNames(predicate: (item: Tabbar.recordRaw, index: number) => boolean) {
      const keepName: string[] = []
      const removeName: string[] = []
      list.value.forEach((v, i) => {
        if (predicate(v, i)) {
          removeName.push(...v.name)
        }
        else {
          keepName.push(...v.name)
        }
      })
      return removeName.filter(v => !keepName.includes(v))
    }

    function remove(tabId: Tabbar.recordRaw['tabId']) {
      const name = computeRemoveNames(item => item.tabId === tabId)
      keepAliveStore.remove(name)
      list.value = list.value.filter(item => item.tabId !== tabId)
    }

    function removeOtherSide(tabId: Tabbar.recordRaw['tabId']) {
      const name = computeRemoveNames(item => item.tabId !== tabId)
      keepAliveStore.remove(name)
      list.value = list.value.filter(item => item.tabId === tabId)
    }

    function removeLeftSide(tabId: Tabbar.recordRaw['tabId']) {
      const index = list.value.findIndex(item => item.tabId === tabId)
      const name = computeRemoveNames((_item, i) => i < index)
      keepAliveStore.remove(name)
      list.value = list.value.filter((_item, i) => i >= index)
    }

    function removeRightSide(tabId: Tabbar.recordRaw['tabId']) {
      const index = list.value.findIndex(item => item.tabId === tabId)
      const name = computeRemoveNames((_item, i) => i > index)
      keepAliveStore.remove(name)
      list.value = list.value.filter((_item, i) => i <= index)
    }
    // 清空所有标签页，登出的时候需要清空
    function clean() {
      list.value = []
    }

    return {
      list,
      leaveIndex,
      add,
      remove,
      removeOtherSide,
      removeLeftSide,
      removeRightSide,
      clean,
    }
  },
)
