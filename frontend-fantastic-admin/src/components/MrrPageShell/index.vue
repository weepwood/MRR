<script setup lang="ts">
defineOptions({ name: 'MrrPageShell' })

const props = withDefaults(defineProps<{
  width?: PageWidth
  compact?: boolean
}>(), {
  width: 'fluid',
  compact: false,
})

type PageWidth = 'fluid' | 'standard' | 'narrow'
</script>

<template>
  <div
    class="mrr-page-shell"
    :class="[
      `mrr-page-shell--${props.width}`,
      { 'mrr-page-shell--compact': props.compact },
    ]"
  >
    <slot />
  </div>
</template>

<style scoped>
.mrr-page-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--mrr-page-gap);
  width: 100%;
  min-width: 0;
  margin: 0 auto;
}

.mrr-page-shell--fluid {
  max-width: var(--mrr-content-fluid);
}

.mrr-page-shell--standard {
  max-width: var(--mrr-content-standard);
}

.mrr-page-shell--narrow {
  max-width: var(--mrr-content-narrow);
}

.mrr-page-shell--compact {
  gap: var(--mrr-space-4);
}

@media (max-width: 640px) {
  .mrr-page-shell {
    gap: var(--mrr-space-4);
  }
}
</style>

<!--
  区块入场写在非 scoped 块里：壳层的子元素是调用方的插槽内容，带的是页面的 scope id，
  scoped 选择器（.mrr-page-shell > *[data-v-壳层]）匹配不到它们。类名本身已带 mrr- 前缀，不会外溢。
-->
<style>
/*
 * 区块依次入场：页头 → 摘要 → 主体，间隔一个 stagger 档，最多三档。
 * 不需要脚本触发 —— 元素插入文档或 display 从 none 恢复时 CSS 动画会自动重播，
 * 因此 KeepAlive 重新激活、v-show 切回时同样生效；关闭动效由 motion.css 全局清零。
 * 只有入场、没有离场，与布局「主内容不做离场过渡」的约定一致。
 */
.mrr-page-shell > * {
  animation: mrr-page-enter var(--mrr-motion-move) var(--mrr-ease-out) backwards;
}

.mrr-page-shell > :nth-child(2) {
  animation-delay: var(--mrr-motion-stagger);
}

.mrr-page-shell > :nth-child(3) {
  animation-delay: calc(var(--mrr-motion-stagger) * 2);
}

.mrr-page-shell > :nth-child(n + 4) {
  animation-delay: calc(var(--mrr-motion-stagger) * 3);
}

/* 嵌套壳层（监控聚合页）只由外层播放一次，避免同一区块淡入两遍。 */
.mrr-page-shell .mrr-page-shell > * {
  animation: none;
}
</style>
