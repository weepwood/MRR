<script setup lang="ts">
import type { EffectiveSystemSettings } from '@/utils/system-settings'

const settings = defineModel<EffectiveSystemSettings>({ required: true })
</script>

<template>
  <section class="setting-section">
    <div class="switch-list">
      <div class="switch-row">
        <div><strong>显示访问水印</strong><p>在档案页面显示用户 ID 与访问时间。</p></div><el-switch v-model="settings.archiveWatermarkEnabled" />
      </div>
      <div class="switch-row">
        <div><strong>允许显示完整身份证号</strong><p>允许在患者管理表中查看完整身份证号；默认关闭。</p></div><el-switch v-model="settings.patientIdCardRevealEnabled" />
      </div>
      <div class="switch-row">
        <div><strong>允许复制身份证号</strong><p>允许点击身份证号后复制完整号码；默认关闭。</p></div><el-switch v-model="settings.patientIdCardCopyEnabled" />
      </div>
    </div>
    <div class="control-grid">
      <el-form-item label="水印透明度">
        <div class="slider-control">
          <el-slider
            v-model="settings.archiveWatermarkOpacity"
            :min="5"
            :max="35"
            aria-label="水印透明度"
          />
          <span class="slider-value">{{ settings.archiveWatermarkOpacity }}%</span>
        </div>
      </el-form-item>
      <el-form-item label="每日允许 IP 切换次数">
        <div class="number-control">
          <el-input-number v-model="settings.archiveIpMaxChanges" :min="0" :max="20" controls-position="right" /><span>次</span>
        </div>
      </el-form-item>
    </div>
  </section>
</template>

<style scoped>
.setting-section { display: grid; gap: var(--mrr-space-5); }

.switch-list,
.control-grid { padding: var(--mrr-space-5); background: var(--mrr-card); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-xl); }
.switch-list { display: grid; gap: 0; padding-top: 0; padding-bottom: 0; }
.switch-row { display: flex; gap: var(--mrr-space-4); align-items: center; justify-content: space-between; min-height: 76px; border-bottom: 1px solid var(--mrr-border); }
.switch-row:last-child { border-bottom: 0; }
.switch-row strong { font-size: 13px; }
.switch-row p { margin: 3px 0 0; font-size: 10px; color: var(--mrr-muted-foreground); }
.control-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--mrr-space-4); }

.slider-control {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) auto;
  gap: var(--mrr-space-3);
  align-items: center;
  width: 100%;
  min-width: 0;
}
.slider-control :deep(.el-slider) { width: 100%; min-width: 120px; }
.slider-value { min-width: 40px; font-size: 12px; color: var(--mrr-muted-foreground); text-align: right; white-space: nowrap; }
.number-control { display: flex; gap: var(--mrr-space-2); align-items: center; }

@media (width <= 680px) {
  .control-grid { grid-template-columns: 1fr; }
  .slider-control { grid-template-columns: minmax(100px, 1fr) auto; }
}
</style>
