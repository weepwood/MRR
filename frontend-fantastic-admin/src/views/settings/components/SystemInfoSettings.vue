<script setup lang="ts">
import type { EffectiveSystemSettings } from '@/utils/system-settings'

const settings = defineModel<EffectiveSystemSettings>({ required: true })
</script>

<template>
  <section class="setting-section">
    <div class="setting-group">
      <div class="group-heading">
        <strong>系统标识</strong>
        <p>系统名称会用于浏览器标题、登录页和主要品牌区域。</p>
      </div>
      <div class="control-grid">
        <el-form-item label="系统名称">
          <el-input v-model="settings.systemName" maxlength="60" show-word-limit placeholder="例如 MRR 病案文件管理系统" />
        </el-form-item>
        <el-form-item label="系统简称">
          <el-input v-model="settings.systemShortName" maxlength="20" show-word-limit placeholder="例如 MRR" />
        </el-form-item>
        <el-form-item label="系统英文名称">
          <el-input v-model="settings.systemEnglishName" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="所属医院或机构">
          <el-input v-model="settings.organizationName" maxlength="80" show-word-limit placeholder="可选" />
        </el-form-item>
      </div>
      <el-form-item label="系统简介">
        <el-input v-model="settings.systemDescription" type="textarea" :rows="3" maxlength="200" show-word-limit />
      </el-form-item>
    </div>
    <el-alert type="info" :closable="false" show-icon title="登录页主标题、英文标题和“登录 + 系统简称”会自动使用以上系统标识，无需重复维护。" />
  </section>
</template>

<style scoped>
.setting-section { display: grid; gap: var(--mrr-space-5); }
.setting-group { padding: var(--mrr-space-5); background: var(--mrr-card); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-xl); }
.group-heading { margin-bottom: var(--mrr-space-5); }
.group-heading strong { font-size: 15px; }
.group-heading p { margin: 4px 0 0; font-size: 11px; color: var(--mrr-muted-foreground); }
.control-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--mrr-space-4); }

@media (width <= 680px) { .control-grid { grid-template-columns: 1fr; } }
</style>
