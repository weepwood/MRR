<template>
  <nav class="flex items-center text-sm" aria-label="面包屑导航">
    <slot />
  </nav>
</template>

<style scoped>
:deep(.breadcrumb-item) {
  &:first-child {
    .separator {
      display: none;
    }
  }

  &:last-child {
    .text {
      opacity: 1;
    }
  }
}
</style>
