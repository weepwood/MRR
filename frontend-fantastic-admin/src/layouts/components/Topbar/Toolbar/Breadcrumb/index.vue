<script setup lang="ts">
import { compile } from 'path-to-regexp'
import Breadcrumb from '../../../Breadcrumb/index.vue'
import BreadcrumbItem from '../../../Breadcrumb/item.vue'

const route = useRoute()

const settingsStore = useSettingsStore()

// 面包屑备份
let breadcrumbListBackup: any = []
const breadcrumbList = computed(() => {
  if (route.name === 'reload') {
    return breadcrumbListBackup
  }
  const list = []
  if (settingsStore.settings.home.enable) {
    list.push({
      path: settingsStore.settings.home.fullPath,
      title: settingsStore.settings.home.title,
    })
  }
  route.matched.forEach((item) => {
    if (item.meta?.breadcrumb !== false) {
      list.push({
        path: item.path,
        title: item.meta?.title,
      })
    }
  })
  breadcrumbListBackup = list
  return list
})

// 以「路径 + 标题」作为稳定 key，同一层级在导航前后保持同一节点；极少数重复项追加序号去重。
const breadcrumbKeys = computed(() => {
  const seen = new Map<string, number>()
  return breadcrumbList.value.map((item: { path: string, title: unknown }) => {
    const base = `${item.path}_${String(item.title)}`
    const count = seen.get(base) ?? 0
    seen.set(base, count + 1)
    return count ? `${base}_${count}` : base
  })
})

function pathCompile(path: string) {
  const toPath = compile(path)
  return toPath(route.params)
}
</script>

<template>
  <Breadcrumb v-if="settingsStore.mode === 'pc' && settingsStore.settings.app.routeBaseOn !== 'filesystem'" class="breadcrumb whitespace-nowrap px-2" navigation-label="面包屑导航">
    <TransitionGroup name="breadcrumb">
      <BreadcrumbItem v-for="(item, index) in breadcrumbList" :key="breadcrumbKeys[index]" :current="Number(index) === breadcrumbList.length - 1" :to="Number(index) < breadcrumbList.length - 1 && item.path !== '' ? pathCompile(item.path) : ''">
        {{ item.title }}
      </BreadcrumbItem>
    </TransitionGroup>
  </Breadcrumb>
</template>

<style scoped>
/*
 * 面包屑动画：只让「新出现的层级」轻量淡入。
 * key 不再带 index，跳转到同一模块下的兄弟页时，公共祖先节点保持不动，
 * 不会整条面包屑重播；原先的 30px 位移 + skewX(-50deg) 偏装饰、时长 300ms 也超出令牌。
 */
.breadcrumb-enter-active {
  transition: transform var(--mrr-motion-move) var(--mrr-ease-out), opacity var(--mrr-motion-move) var(--mrr-ease-out);
}

.breadcrumb-enter-from {
  opacity: 0;
  transform: var(--mrr-motion-decor, translateX(4px));
}
</style>
