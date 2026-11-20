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
  z-index: 1000;
  width: calc(100% - var(--g-main-sidebar-actual-width) - var(--g-sub-sidebar-actual-width));
  padding-right: var(--scrollbar-width, 0);
  transform: translateX(-50%) translateX(calc(var(--g-main-sidebar-actual-width) / 2)) translateX(calc(var(--g-sub-sidebar-actual-width) / 2));
  transition: width 0.3s, max-width 0.3s, transform 0.3s, top 0.3s;

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
