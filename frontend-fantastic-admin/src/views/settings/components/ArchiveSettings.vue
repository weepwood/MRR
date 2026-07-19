<script setup lang="ts">
import type { EffectiveSystemSettings } from '@/utils/system-settings'

const settings = defineModel<EffectiveSystemSettings>({ required: true })
</script>

<template>
  <section class="setting-section">
    <div class="setting-group">
      <div class="group-heading"><strong>默认图片来源</strong><p>设置影像档案袋优先从本地图片服务器或 OSS 读取。</p></div>
      <el-segmented v-model="settings.imageSource" :options="[{ label: '本地图片', value: 'local' }, { label: 'OSS 图片', value: 'oss' }]" class="full-width" />
      <el-alert type="info" :closable="false" show-icon :title="settings.imageSource === 'local' ? '默认从本地图片服务器读取。' : '优先从 OSS 读取；签名失败时自动回退本地图片。'" />
    </div>
    <div class="setting-group">
      <div class="group-heading"><strong>默认浏览方式</strong><p>进入影像档案袋时自动应用。</p></div>
      <div class="control-grid">
        <el-form-item label="影像列表"><el-segmented v-model="settings.archiveDefaultView" :options="[{ label: '缩略图', value: 'thumb' }, { label: '列表', value: 'list' }]" class="full-width" /></el-form-item>
        <el-form-item label="预览模式"><el-segmented v-model="settings.archivePreviewMode" :options="[{ label: '单页', value: 'single' }, { label: '连续滚动', value: 'scroll' }]" class="full-width" /></el-form-item>
        <el-form-item label="缩略图宽度">
          <div class="slider-control">
            <el-slider
              v-model="settings.archiveThumbnailSize"
              :min="160"
              :max="320"
              :step="20"
              aria-label="缩略图宽度"
            />
            <span class="slider-value">{{ settings.archiveThumbnailSize }} px</span>
          </div>
        </el-form-item>
        <el-form-item label="首批渲染数量"><div class="number-control"><el-input-number v-model="settings.archivePreloadCount" :min="10" :max="100" :step="10" controls-position="right" /><span>张</span></div></el-form-item>
      </div>
    </div>
    <div class="switch-list">
      <div class="switch-row"><div><strong>自动适应预览区域</strong><p>完整显示图片；关闭后按原始尺寸浏览。</p></div><el-switch v-model="settings.archiveAutoFit" /></div>
      <div class="switch-row"><div><strong>记住选择状态</strong><p>再次进入同一档案时恢复已选影像页。</p></div><el-switch v-model="settings.archiveRememberSelection" /></div>
    </div>
  </section>
</template>

<style scoped>
.setting-section { display: grid; gap: var(--mrr-space-5); }
.setting-group, .switch-list { padding: var(--mrr-space-5); background: var(--mrr-card); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-xl); }
.group-heading { margin-bottom: var(--mrr-space-5); }
.group-heading strong { font-size: 15px; }
.group-heading p { margin: 4px 0 0; font-size: 11px; color: var(--mrr-muted-foreground); }
.control-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--mrr-space-4); }
.full-width { width: 100%; }
.slider-control {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) auto;
  gap: var(--mrr-space-3);
  align-items: center;
  width: 100%;
  min-width: 0;
}
.slider-control :deep(.el-slider) { width: 100%; min-width: 120px; }
.slider-value { min-width: 52px; font-size: 12px; color: var(--mrr-muted-foreground); text-align: right; white-space: nowrap; }
.number-control { display: flex; gap: var(--mrr-space-2); align-items: center; }
.switch-list { display: grid; gap: 0; padding-top: 0; padding-bottom: 0; }
.switch-row { display: flex; gap: var(--mrr-space-4); align-items: center; justify-content: space-between; min-height: 76px; border-bottom: 1px solid var(--mrr-border); }
.switch-row:last-child { border-bottom: 0; }
.switch-row strong { font-size: 13px; }
.switch-row p { margin: 3px 0 0; font-size: 10px; color: var(--mrr-muted-foreground); }
@media (max-width: 680px) {
  .control-grid { grid-template-columns: 1fr; }
  .slider-control { grid-template-columns: minmax(100px, 1fr) auto; }
}
</style>
