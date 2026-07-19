<route lang="yaml">
meta:
  title: 登录页文案
  auth:
    - system:read
  menu: false
  breadcrumb: false
  cache: false
</route>

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
  min-height: 220px;
  color: var(--mrr-muted-foreground);
  place-items: center;
}
</style>
