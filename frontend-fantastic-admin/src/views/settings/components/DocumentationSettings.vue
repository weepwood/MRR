<script setup lang="ts">
import type { EffectiveSystemSettings } from '@/utils/system-settings'
import { isAllowedDocumentationUrl } from '@/utils/system-settings'

const settings = defineModel<EffectiveSystemSettings>({ required: true })

const documentationItems = computed(() => [
  {
    key: 'documentationUserGuideUrl' as const,
    label: '用户使用手册',
    description: '面向普通业务用户的病案查询、影像浏览和系统操作说明。',
    placeholder: '例如 /docs/ 或 https://docs.example.com/user/',
  },
  {
    key: 'documentationDeveloperUrl' as const,
    label: '开发文档',
    description: '面向开发人员的架构、接口、数据库和开发流程说明。',
    placeholder: '例如 /docs/internal/ 或 https://docs.example.com/development/',
  },
  {
    key: 'documentationOperationsUrl' as const,
    label: '运维指南',
    description: '面向部署和运维人员的安装、备份、监控与故障处理说明。',
    placeholder: '例如 /docs/internal/deployment.html',
  },
])

function canOpen(value: string): boolean {
  return Boolean(value.trim()) && isAllowedDocumentationUrl(value)
}

function openPreview(value: string) {
  if (!canOpen(value)) { return }
  window.open(value.trim(), '_blank', 'noopener')
}
</script>

<template>
  <section class="setting-section">
    <div class="setting-group">
      <div class="group-heading">
        <strong>帮助中心文档入口</strong>
        <p>文档不再强制打包进单体 JAR。可以填写独立文档服务器、Nginx 路径或其他 HTTP(S) 地址。</p>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="支持 http://、https:// 和以单个 / 开头的站内路径；留空后帮助中心会显示“未配置”。"
      />

      <div class="documentation-list">
        <div v-for="item in documentationItems" :key="item.key" class="documentation-item">
          <div class="item-heading">
            <div>
              <strong>{{ item.label }}</strong>
              <p>{{ item.description }}</p>
            </div>
            <el-button
              plain
              :disabled="!canOpen(settings[item.key])"
              @click="openPreview(settings[item.key])"
            >
              <FaIcon name="i-ri:external-link-line" />
              测试访问
            </el-button>
          </div>
          <el-form-item
            :prop="item.key"
            :error="settings[item.key] && !isAllowedDocumentationUrl(settings[item.key]) ? '仅允许 HTTP(S) 地址或以 / 开头的站内路径' : ''"
          >
            <el-input
              v-model="settings[item.key]"
              clearable
              maxlength="2048"
              :placeholder="item.placeholder"
            />
          </el-form-item>
        </div>
      </div>
    </div>

    <div class="setting-group deployment-note">
      <div class="group-heading">
        <strong>部署关系</strong>
        <p>单体 JAR 只提供管理端和后端服务；文档可由 Windows 离线包中的 Nginx、独立 VitePress 服务或其他文档平台提供。</p>
      </div>
      <div class="route-example">
        <span>站内路径示例</span>
        <code>/docs/</code>
        <small>浏览器会按当前系统域名和端口打开。</small>
      </div>
      <div class="route-example">
        <span>独立服务器示例</span>
        <code>http://192.168.1.20:8080/user-guide/</code>
        <small>适合将帮助文档部署在独立 Nginx 或文档服务器。</small>
      </div>
    </div>
  </section>
</template>

<style scoped>
.setting-section {
  display: grid;
  gap: var(--mrr-space-5);
}

.setting-group {
  padding: var(--mrr-space-5);
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-xl);
}

.group-heading {
  margin-bottom: var(--mrr-space-5);
}

.group-heading strong {
  font-size: 15px;
}

.group-heading p {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--mrr-muted-foreground);
}

.documentation-list {
  display: grid;
  gap: var(--mrr-space-4);
  margin-top: var(--mrr-space-5);
}

.documentation-item {
  padding: var(--mrr-space-4);
  background: var(--mrr-muted);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-lg);
}

.item-heading {
  display: flex;
  gap: var(--mrr-space-4);
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--mrr-space-3);
}

.item-heading strong {
  font-size: 13px;
}

.item-heading p {
  margin: 4px 0 0;
  font-size: 10px;
  color: var(--mrr-muted-foreground);
}

.documentation-item :deep(.el-form-item) {
  margin-bottom: 0;
}

.deployment-note {
  display: grid;
  gap: var(--mrr-space-3);
}

.deployment-note .group-heading {
  margin-bottom: var(--mrr-space-2);
}

.route-example {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 4px var(--mrr-space-3);
  align-items: center;
  padding: var(--mrr-space-3);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-lg);
}

.route-example span {
  font-size: 11px;
  color: var(--mrr-muted-foreground);
}

.route-example code {
  overflow-wrap: anywhere;
}

.route-example small {
  grid-column: 2;
  color: var(--mrr-muted-foreground);
}

@media (width <= 680px) {
  .item-heading {
    flex-direction: column;
  }

  .route-example {
    grid-template-columns: 1fr;
  }

  .route-example small {
    grid-column: 1;
  }
}
</style>
