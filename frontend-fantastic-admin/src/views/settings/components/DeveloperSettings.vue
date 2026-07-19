<script setup lang="ts">
import type { EffectiveSystemSettings } from '@/utils/system-settings'

const settings = defineModel<EffectiveSystemSettings>({ required: true })
</script>

<template>
  <section class="setting-section">
    <el-alert type="error" :closable="false" show-icon title="开发者模式会绕过普通认证并放宽跨域策略，只能用于隔离的开发和联调环境。" />
    <div class="setting-row">
      <div><strong>兼容旧版无登录接口调用</strong><p>无有效 JWT 时使用虚拟 dev / ADMIN 会话。生产环境必须关闭。</p></div>
      <el-switch v-model="settings.developerModeEnabled" inline-prompt active-text="启用" inactive-text="关闭" />
    </div>
    <div class="developer-grid">
      <article><span><FaIcon name="i-ri:shield-user-line" /></span><div><strong>认证兼容</strong><p>无有效 Token 的普通 API 使用虚拟管理员身份。</p></div></article>
      <article><span><FaIcon name="i-ri:global-line" /></span><div><strong>跨域调试</strong><p>临时允许任意浏览器 Origin 发起调试请求。</p></div></article>
    </div>
  </section>
</template>

<style scoped>
.setting-section { display: grid; gap: var(--mrr-space-5); }
.setting-row { display: flex; gap: var(--mrr-space-4); align-items: center; justify-content: space-between; padding: var(--mrr-space-5); background: var(--mrr-card); border: 1px solid color-mix(in srgb, var(--color-danger) 25%, var(--mrr-border)); border-radius: var(--mrr-radius-xl); }
.setting-row strong { font-size: 13px; }
.setting-row p { margin: 3px 0 0; font-size: 10px; color: var(--mrr-muted-foreground); }
.developer-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--mrr-space-4); }
.developer-grid article { display: flex; gap: var(--mrr-space-3); padding: var(--mrr-space-4); background: var(--mrr-muted); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-lg); }
.developer-grid article > span { color: var(--color-danger); }
.developer-grid p { margin: 4px 0 0; font-size: 10px; color: var(--mrr-muted-foreground); }
@media (max-width: 680px) { .developer-grid { grid-template-columns: 1fr; } }
</style>
