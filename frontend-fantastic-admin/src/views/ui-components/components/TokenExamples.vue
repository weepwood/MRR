<script setup lang="ts">
const colors = [
  { label: '主要操作', variable: '--mrr-primary' },
  { label: '正文', variable: '--mrr-foreground' },
  { label: '说明文字', variable: '--mrr-muted-foreground' },
  { label: '内容表面', variable: '--mrr-card' },
  { label: '工作区', variable: '--mrr-workspace-bg' },
  { label: '分隔线', variable: '--mrr-border' },
]
const radii = ['sm', 'md', 'lg', 'card']
</script>

<template>
  <div class="example-stack">
    <div class="token-grid">
      <div v-for="color in colors" :key="color.variable" class="token-color">
        <span class="token-swatch" :style="{ backgroundColor: `var(${color.variable})` }" aria-hidden="true" />
        <strong>{{ color.label }}</strong>
        <code>{{ color.variable }}</code>
      </div>
    </div>
    <div class="example-row">
      <div v-for="radius in radii" :key="radius" class="token-radius" :style="{ borderRadius: `var(--mrr-radius-${radius})` }">
        <code>{{ radius }}</code>
      </div>
    </div>
    <p class="example-note">
      圆角、主色和字体沿用全站设置。页头与外层布局本身也使用 MrrPageHeader 和 MrrPageShell。
    </p>
  </div>
</template>

<style scoped>
.token-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(145px, 1fr));
  gap: var(--mrr-space-4);
}

.token-color {
  display: grid;
  gap: var(--mrr-space-2);
  min-width: 0;
}

.token-swatch {
  height: 48px;
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
}

.token-color strong {
  font-size: 13px;
  font-weight: 500;
}

.token-color code {
  font-size: 11px;
  color: var(--mrr-muted-foreground);
  overflow-wrap: anywhere;
}

.token-radius {
  display: grid;
  place-items: center;
  width: 72px;
  height: 56px;
  color: var(--mrr-muted-foreground);
  background: var(--mrr-muted);
  border: 1px solid var(--mrr-border);
}
</style>
