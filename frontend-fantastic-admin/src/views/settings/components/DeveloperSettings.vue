<script setup lang="ts">
import type { EffectiveSystemSettings } from '@/utils/system-settings'

defineModel<EffectiveSystemSettings>({ required: true })
</script>

<template>
  <section class="setting-section">
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="旧版开发者模式已停用，不能再通过系统设置绕过认证或放宽跨域策略。"
    />
    <div class="setting-row">
      <div>
        <strong>旧版无登录兼容模式</strong>
        <p>该高风险兼容能力已永久关闭。开发联调请在独立环境中配置明确的账号、权限和允许 Origin。</p>
      </div>
      <el-switch :model-value="false" disabled inline-prompt active-text="启用" inactive-text="已停用" />
    </div>
    <div class="developer-grid">
      <article>
        <span><FaIcon name="i-ri:shield-check-line" /></span>
        <div><strong>认证边界</strong><p>受保护 API 始终要求有效登录凭据，不再创建虚拟管理员会话。</p></div>
      </article>
      <article>
        <span><FaIcon name="i-ri:global-line" /></span>
        <div><strong>跨域边界</strong><p>只允许服务端配置的精确 Origin，运行时设置不能放宽 CORS。</p></div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.setting-section { display: grid; gap: var(--mrr-space-5); }
.setting-row { display: flex; gap: var(--mrr-space-4); align-items: center; justify-content: space-between; padding: var(--mrr-space-5); background: var(--mrr-card); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-xl); }
.setting-row strong { font-size: 13px; }
.setting-row p { margin: 3px 0 0; font-size: 10px; color: var(--mrr-muted-foreground); }
.developer-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--mrr-space-4); }
.developer-grid article { display: flex; gap: var(--mrr-space-3); padding: var(--mrr-space-4); background: var(--mrr-muted); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-lg); }
.developer-grid article > span { color: var(--color-success); }
.developer-grid p { margin: 4px 0 0; font-size: 10px; color: var(--mrr-muted-foreground); }
@media (max-width: 680px) { .developer-grid { grid-template-columns: 1fr; } }
</style>
