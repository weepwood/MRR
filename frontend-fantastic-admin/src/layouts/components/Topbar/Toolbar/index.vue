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
  padding: 0 var(--mrr-space-4);
  background: transparent;
  border-top: 1px solid var(--mrr-shell-divider);
}

.left-side {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  min-width: 0;
  height: 100%;
  padding-right: var(--mrr-space-3);
  overflow: hidden;
}

.right-side {
  display: flex;
  flex: 0 0 auto;
  gap: var(--mrr-space-1);
  align-items: center;
  justify-content: flex-end;
  height: 100%;
  padding-left: var(--mrr-space-3);
  border-left: 1px solid var(--mrr-shell-divider);
}

.toolbar-container :deep(button),
.toolbar-container :deep(a) {
  border-radius: var(--mrr-radius-md);
}
</style>
