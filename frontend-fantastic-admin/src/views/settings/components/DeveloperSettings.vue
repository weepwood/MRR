<script setup lang="ts">
import type { EffectiveSystemSettings } from '@/utils/system-settings'

const settings = defineModel<EffectiveSystemSettings>({ required: true })
</script>

<template>
  <section class="setting-section">
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="开发者模式仅允许旧系统以无 Token 方式只读打开影像档案袋，不会绕过后台登录或放宽跨域策略。"
    />
    <div class="setting-row">
      <div>
        <strong>兼容旧版影像档案袋调用</strong>
        <p>还必须通过启动配置 MRR_DEVELOPER_MODE_ALLOWED=true 明确允许；关闭任一开关都会立即停止兼容访问。</p>
      </div>
      <el-switch v-model="settings.developerModeEnabled" inline-prompt active-text="启用" inactive-text="关闭" />
    </div>
    <div class="developer-grid">
      <article>
        <span><FaIcon name="i-ri:folder-shield-2-line" /></span>
        <div><strong>只读档案袋</strong><p>仅允许病案号、上架号查询以及本地、Nginx、OSS 影像读取，不提供后台管理身份。</p></div>
      </article>
      <article>
        <span><FaIcon name="i-ri:shield-keyhole-line" /></span>
        <div><strong>认证边界</strong><p>无效、过期或撤销 Token 始终返回 401；下载、PDF、打印和图片分类入口在兼容模式下关闭。</p></div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.setting-section { display: grid; gap: var(--mrr-space-5); }
.setting-row { display: flex; gap: var(--mrr-space-4); align-items: center; justify-content: space-between; padding: var(--mrr-space-5); background: var(--mrr-card); border: 1px solid color-mix(in srgb, var(--color-warning) 25%, var(--mrr-border)); border-radius: var(--mrr-radius-xl); }
.setting-row strong { font-size: 13px; }
.setting-row p { margin: 3px 0 0; font-size: 10px; color: var(--mrr-muted-foreground); }
.developer-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--mrr-space-4); }
.developer-grid article { display: flex; gap: var(--mrr-space-3); padding: var(--mrr-space-4); background: var(--mrr-muted); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-lg); }
.developer-grid article > span { color: var(--color-warning); }
.developer-grid p { margin: 4px 0 0; font-size: 10px; color: var(--mrr-muted-foreground); }
@media (max-width: 680px) { .developer-grid { grid-template-columns: 1fr; } }
</style>
