<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router'
import { RouterLink } from 'vue-router'

const props = withDefaults(
  defineProps<{
    to?: RouteLocationRaw
    replace?: boolean
    separator?: string
  }>(),
  {
    separator: '/',
  },
)
</script>

<template>
  <div class="breadcrumb-item flex items-center text-foreground">
    <span class="separator mx-2" aria-hidden="true">
      {{ separator }}
    </span>
    <!--
      可跳转的层级渲染为真实链接：键盘 Tab 可达、Enter 跳转、读屏读作「链接」，也支持中键在新标签页打开。
      原先是带 @click 的 span，只能用鼠标操作。
    -->
    <RouterLink
      v-if="props.to"
      :to="props.to"
      :replace="props.replace"
      class="text is-link flex items-center opacity-60 transition-opacity hover-opacity-100"
    >
      <slot />
    </RouterLink>
    <span v-else class="text flex items-center opacity-60" aria-current="page">
      <slot />
    </span>
  </div>
</template>

<style scoped>
.is-link {
  color: inherit;
  text-decoration: none;
  border-radius: var(--mrr-radius-sm);
}

.is-link:focus-visible {
  outline: 2px solid var(--mrr-ring);
  outline-offset: 2px;
  opacity: 1;
}
</style>
