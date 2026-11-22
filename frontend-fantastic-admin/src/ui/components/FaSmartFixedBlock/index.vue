<script setup lang="ts">
defineOptions({
  name: 'FaSmartFixedBlock',
})

defineProps<{
  position: 'top' | 'bottom'
}>()
</script>

<template>
  <div class="smart-fixed-block" :class="{ [`${position}`]: position }">
    <slot />
  </div>
</template>

<style scoped>
.smart-fixed-block {
  position: fixed;

  /* Chrome 86 无法匹配构建为 :not(:is(...)) 的逻辑定位降级选择器。 */
  left: 50%;

  /* stylelint-disable-next-line order/properties-order -- 逻辑定位覆盖基础定位。 */
  inset-inline: 50% 0;
  z-index: var(--mrr-z-sticky);
  width: calc(100% - var(--g-main-sidebar-actual-width) - var(--g-sub-sidebar-actual-width));
  padding-right: var(--scrollbar-width, 0);
  transform: translateX(-50%) translateX(calc(var(--g-main-sidebar-actual-width) / 2)) translateX(calc(var(--g-sub-sidebar-actual-width) / 2));

  /* 与侧栏收起/展开（layouts/index.vue，move 档）同节奏，否则顶栏宽度会比侧栏慢半拍。 */
  transition:
    width var(--mrr-motion-move) var(--mrr-ease-out),
    max-width var(--mrr-motion-move) var(--mrr-ease-out),
    transform var(--mrr-motion-move) var(--mrr-ease-out),
    top var(--mrr-motion-move) var(--mrr-ease-out);

  &.top {
    top: var(--g-header-actual-height);
  }

  &.bottom {
    bottom: 0;
  }

  [data-mode="mobile"] & {
    width: 100% !important;
    transform: translateX(-50%) !important;
  }
}
</style>
