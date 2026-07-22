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

    <div class="trusted-sources-card">
      <div class="trusted-sources-heading">
        <div>
          <strong>允许访问的客户端 IP / 网段</strong>
          <p>请求必须先经过本机 Nginx。后端只在确认代理来源可信后，才读取 X-Forwarded-For 或 X-Real-IP。</p>
        </div>
        <el-tag type="info" effect="plain">
          每行一个
        </el-tag>
      </div>

      <el-input
        v-model="settings.developerModeAllowedSources"
        type="textarea"
        :rows="7"
        resize="vertical"
        spellcheck="false"
        placeholder="192.168.1.20&#10;192.168.1.0/24&#10;10.20.0.0/16&#10;::1"
      />

      <div class="source-examples">
        <span><code>192.168.1.20</code> 单台电脑</span>
        <span><code>192.168.1.0/24</code> 一个 IPv4 网段</span>
        <span><code>10.20.0.0/16</code> 较大内网网段</span>
        <span><code>::1</code> 本机 IPv6</span>
      </div>

      <el-alert
        v-if="settings.developerModeEnabled && !settings.developerModeAllowedSources.trim()"
        type="error"
        :closable="false"
        show-icon
        title="当前白名单为空，保存前需要至少配置一个 IP 或网段。"
      />
    </div>

    <div class="developer-grid">
      <article>
        <span><FaIcon name="i-ri:folder-shield-2-line" /></span>
        <div><strong>只读档案袋</strong><p>仅允许病案号、上架号查询以及本地、Nginx、OSS 影像读取，不提供后台管理身份。</p></div>
      </article>
      <article>
        <span><FaIcon name="i-ri:shield-keyhole-line" /></span>
        <div><strong>双层来源校验</strong><p>先验证后端连接来自本机 Nginx，再验证真实客户端 IP 是否命中上方单 IP 或 CIDR 白名单。</p></div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.setting-section { display: grid; gap: var(--mrr-space-5); }
.setting-row { display: flex; gap: var(--mrr-space-4); align-items: center; justify-content: space-between; padding: var(--mrr-space-5); background: var(--mrr-card); border: 1px solid color-mix(in srgb, var(--color-warning) 25%, var(--mrr-border)); border-radius: var(--mrr-radius-xl); }
.setting-row strong,
.trusted-sources-card strong { font-size: 13px; }
.setting-row p,
.trusted-sources-card p { margin: 3px 0 0; font-size: 10px; line-height: 1.6; color: var(--mrr-muted-foreground); }
.trusted-sources-card { display: grid; gap: var(--mrr-space-4); padding: var(--mrr-space-5); background: var(--mrr-card); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-xl); }
.trusted-sources-heading { display: flex; gap: var(--mrr-space-4); align-items: flex-start; justify-content: space-between; }
.trusted-sources-card :deep(.el-textarea__inner) { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; line-height: 1.6; }
.source-examples { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 16px; font-size: 11px; color: var(--mrr-muted-foreground); }
.source-examples span { min-width: 0; }
.source-examples code { padding: 2px 5px; margin-right: 4px; font-size: 10px; color: var(--el-color-primary); background: var(--mrr-muted); border-radius: 4px; }
.developer-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--mrr-space-4); }
.developer-grid article { display: flex; gap: var(--mrr-space-3); padding: var(--mrr-space-4); background: var(--mrr-muted); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-lg); }
.developer-grid article > span { color: var(--color-warning); }
.developer-grid p { margin: 4px 0 0; font-size: 10px; color: var(--mrr-muted-foreground); }
@media (max-width: 680px) {
  .developer-grid,
  .source-examples { grid-template-columns: 1fr; }
  .trusted-sources-heading { align-items: stretch; flex-direction: column; }
}
</style>
