<script setup lang="ts">
import type { EffectiveSystemSettings } from '@/utils/system-settings'

const settings = defineModel<EffectiveSystemSettings>({ required: true })
const publicSupportReady = computed(() => Boolean(settings.value.systemAdminPhone || settings.value.systemAdminEmail))
</script>

<template>
  <section class="setting-section">
    <div class="setting-group">
      <div class="group-heading">
        <strong>登录页展示</strong>
        <p>安全字段、登录按钮和认证错误保持固定，只允许修改展示性内容。</p>
      </div>
      <div class="control-grid">
        <el-form-item label="环境标签">
          <el-input v-model="settings.loginEnvironmentLabel" maxlength="60" show-word-limit placeholder="例如 医院内网系统" />
        </el-form-item>
        <el-form-item label="页脚说明">
          <el-input v-model="settings.loginFooterText" maxlength="120" show-word-limit />
        </el-form-item>
      </div>
      <el-form-item label="登录区说明">
        <el-input v-model="settings.loginFormDescription" maxlength="160" show-word-limit />
      </el-form-item>
      <el-form-item label="管理员提示">
        <el-input v-model="settings.loginHelpText" type="textarea" :rows="2" maxlength="200" show-word-limit />
      </el-form-item>
      <div class="switch-row">
        <div><strong>显示功能介绍</strong><p>控制登录页左侧三项功能介绍是否展示。</p></div>
        <el-switch v-model="settings.loginFeatureEnabled" />
      </div>
      <div v-if="settings.loginFeatureEnabled" class="advanced-copy">
        <div class="advanced-heading">
          <strong>功能介绍文案</strong><span>高级展示内容</span>
        </div>
        <div class="feature-editor">
          <el-form-item label="功能 1 标题">
            <el-input v-model="settings.loginFeature1Title" maxlength="60" show-word-limit />
          </el-form-item>
          <el-form-item label="功能 1 说明">
            <el-input v-model="settings.loginFeature1Description" maxlength="120" show-word-limit />
          </el-form-item>
        </div>
        <div class="feature-editor">
          <el-form-item label="功能 2 标题">
            <el-input v-model="settings.loginFeature2Title" maxlength="60" show-word-limit />
          </el-form-item>
          <el-form-item label="功能 2 说明">
            <el-input v-model="settings.loginFeature2Description" maxlength="120" show-word-limit />
          </el-form-item>
        </div>
        <div class="feature-editor">
          <el-form-item label="功能 3 标题">
            <el-input v-model="settings.loginFeature3Title" maxlength="60" show-word-limit />
          </el-form-item>
          <el-form-item label="功能 3 说明">
            <el-input v-model="settings.loginFeature3Description" maxlength="120" show-word-limit />
          </el-form-item>
        </div>
      </div>
    </div>

    <div class="setting-group">
      <div class="group-heading">
        <strong>系统管理员与技术支持</strong>
        <p>优先填写信息科或运维组的值班联系方式，不建议公开个人手机号。</p>
      </div>
      <div class="switch-list">
        <div class="switch-row">
          <div><strong>启用管理员联系信息</strong><p>允许系统页面使用统一的管理员支持信息。</p></div>
          <el-switch v-model="settings.systemAdminContactEnabled" />
        </div>
        <div class="switch-row">
          <div><strong>在登录页公开显示</strong><p>启用后，未登录用户可悬停、聚焦或点击查看联系方式。</p></div>
          <el-switch v-model="settings.systemAdminPublicVisible" :disabled="!settings.systemAdminContactEnabled" />
        </div>
      </div>
      <div class="control-grid" :class="{ disabled: !settings.systemAdminContactEnabled }">
        <el-form-item label="联系人或支持团队">
          <el-input v-model="settings.systemAdminDisplayName" :disabled="!settings.systemAdminContactEnabled" maxlength="60" />
        </el-form-item>
        <el-form-item label="所属部门">
          <el-input v-model="settings.systemAdminDepartment" :disabled="!settings.systemAdminContactEnabled" maxlength="60" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="settings.systemAdminPhone" :disabled="!settings.systemAdminContactEnabled" maxlength="40" placeholder="推荐填写科室或值班电话" />
        </el-form-item>
        <el-form-item label="分机号">
          <el-input v-model="settings.systemAdminExtension" :disabled="!settings.systemAdminContactEnabled" maxlength="20" />
        </el-form-item>
        <el-form-item label="联系邮箱">
          <el-input v-model="settings.systemAdminEmail" :disabled="!settings.systemAdminContactEnabled" maxlength="100" />
        </el-form-item>
        <el-form-item label="服务时间">
          <el-input v-model="settings.systemAdminServiceHours" :disabled="!settings.systemAdminContactEnabled" maxlength="100" placeholder="例如 工作日 08:00–17:30" />
        </el-form-item>
      </div>
      <el-form-item label="补充说明">
        <el-input v-model="settings.systemAdminDescription" :disabled="!settings.systemAdminContactEnabled" type="textarea" :rows="2" maxlength="200" show-word-limit />
      </el-form-item>
      <el-alert v-if="settings.systemAdminPublicVisible && !publicSupportReady" type="warning" :closable="false" show-icon title="公开显示已开启，请至少填写联系电话或联系邮箱。" />
    </div>

    <div class="login-preview">
      <span>{{ settings.loginEnvironmentLabel }}</span>
      <strong>{{ settings.systemName }}</strong>
      <small>{{ settings.systemEnglishName }}</small>
      <p>{{ settings.systemDescription }}</p>
      <div><b>登录 {{ settings.systemShortName }}</b><em>{{ settings.loginFormDescription }}</em></div>
    </div>
  </section>
</template>

<style scoped>
.setting-section { display: grid; gap: var(--mrr-space-5); }

.setting-group,
.login-preview { padding: var(--mrr-space-5); background: var(--mrr-card); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-xl); }
.group-heading { margin-bottom: var(--mrr-space-5); }
.group-heading strong { font-size: 15px; }
.group-heading p { margin: 4px 0 0; font-size: 11px; color: var(--mrr-muted-foreground); }

.control-grid,
.feature-editor { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--mrr-space-4); }
.switch-list { display: grid; margin-bottom: var(--mrr-space-5); border-top: 1px solid var(--mrr-border); border-bottom: 1px solid var(--mrr-border); }
.switch-row { display: flex; gap: var(--mrr-space-4); align-items: center; justify-content: space-between; min-height: 76px; border-bottom: 1px solid var(--mrr-border); }
.switch-row:last-child { border-bottom: 0; }
.switch-row strong { font-size: 13px; }
.switch-row p { margin: 3px 0 0; font-size: 10px; color: var(--mrr-muted-foreground); }
.advanced-copy { padding: var(--mrr-space-4); margin-top: var(--mrr-space-4); background: var(--mrr-muted); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-lg); }
.advanced-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--mrr-space-4); }
.advanced-heading span { font-size: 10px; color: var(--mrr-muted-foreground); }
.feature-editor + .feature-editor { padding-top: var(--mrr-space-3); border-top: 1px solid var(--mrr-border); }
.disabled { opacity: 0.62; }
.login-preview { display: grid; gap: var(--mrr-space-2); background: radial-gradient(circle at 0 0, color-mix(in srgb, var(--mrr-primary) 10%, transparent), transparent 38%), var(--mrr-muted); }

.login-preview > span,
.login-preview > small { font-size: 10px; color: var(--mrr-primary); text-transform: uppercase; letter-spacing: 0.08em; }
.login-preview > strong { font-size: 25px; }
.login-preview > p { margin: 0; color: var(--mrr-muted-foreground); }
.login-preview > div { display: grid; gap: 3px; padding: var(--mrr-space-3); margin-top: var(--mrr-space-3); background: var(--mrr-card); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-lg); }
.login-preview em { font-size: 11px; font-style: normal; color: var(--mrr-muted-foreground); }

@media (width <= 680px) {
  .control-grid,
  .feature-editor { grid-template-columns: 1fr; }
}
</style>
