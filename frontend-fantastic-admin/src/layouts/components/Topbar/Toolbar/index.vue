<script setup lang="ts">
import LeftSide from './leftSide.vue'
import RightSide from './rightSide.vue'

defineOptions({
  name: 'Toolbar',
})
</script>

<template>
  <div class="toolbar-container">
    <div class="left-side">
      <LeftSide />
    </div>
    <div class="right-side">
      <RightSide />
    </div>
  </div>
</template>

<style scoped>
.toolbar-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--g-toolbar-height);
  padding: 0 14px 0 16px;
  background: transparent;
  border-top: 1px solid var(--mrr-shell-divider);
}

.left-side {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  min-width: 0;
  height: 100%;
  padding-right: 48px;
  overflow: hidden;
  mask-image: linear-gradient(to right, #000 0%, #000 calc(100% - 36px), transparent);
}

.right-side {
  display: flex;
  flex: 0 0 auto;
  gap: 4px;
  align-items: center;
  justify-content: flex-end;
  height: 100%;
  padding-left: 12px;
  border-left: 1px solid var(--mrr-shell-divider);
}

.toolbar-container :deep(button),
.toolbar-container :deep(a) {
  border-radius: var(--mrr-radius-md);
}
</style>
