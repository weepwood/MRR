<script setup lang="ts">
const router = useRouter()

onMounted(() => {
  void router.replace({ path: '/settings', query: { section: 'login-support' } })
})
</script>

<template>
  <div class="legacy-settings-redirect">
    正在打开系统设置…
  </div>
</template>

<style scoped>
.legacy-settings-redirect {
  display: grid;
  place-items: center;
  min-height: 220px;
  color: var(--mrr-muted-foreground);
}
</style>
