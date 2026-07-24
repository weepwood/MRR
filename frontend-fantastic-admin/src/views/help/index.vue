<script setup lang="ts">
import type { Component } from 'vue'
import type { DocumentationSettings } from '@/api/modules/documentation-settings'
import { Connection, Document, Reading, Setting } from '@element-plus/icons-vue'
import {
  DEFAULT_DOCUMENTATION_SETTINGS,
  getPublicDocumentationSettings,
} from '@/api/modules/documentation-settings'

defineOptions({ name: 'HelpCenter' })

interface DocumentationEntry {
  key: keyof DocumentationSettings | 'api'
  title: string
  description: string
  target: string
  icon: Component
  tone: string
}

const loading = ref(true)
const documentationSettings = ref<DocumentationSettings>({ ...DEFAULT_DOCUMENTATION_SETTINGS })

const entries = computed<DocumentationEntry[]>(() => [
  {
    key: 'documentationUserGuideUrl',
    title: '用户使用手册',
    description: '查看病案管理、影像浏览、统计分析和系统操作说明。',
    target: documentationSettings.value.documentationUserGuideUrl,
    icon: Reading,
    tone: 'primary',
  },
  {
    key: 'documentationDeveloperUrl',
    title: '开发文档',
    description: '查看前后端架构、开发流程、接口约定和数据库设计。',
    target: documentationSettings.value.documentationDeveloperUrl,
    icon: Document,
    tone: 'success',
  },
  {
    key: 'documentationOperationsUrl',
    title: '运维指南',
    description: '查看部署、日志、备份恢复、监控和故障处理说明。',
    target: documentationSettings.value.documentationOperationsUrl,
    icon: Setting,
    tone: 'warning',
  },
  {
    key: 'api',
    title: '实时 API 文档',
    description: '打开由 Springdoc OpenAPI 实时生成的 Swagger UI。',
    target: '/swagger-ui/index.html#/',
    icon: Connection,
    tone: 'info',
  },
])

function openDocumentation(entry: DocumentationEntry) {
  if (!entry.target) { return }
  window.open(entry.target, '_blank', 'noopener')
}

onMounted(async () => {
  try {
    documentationSettings.value = await getPublicDocumentationSettings()
  }
  finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="help-center">
    <header class="help-header">
      <div>
        <p class="help-kicker">
          MRR DOCUMENTATION
        </p>
        <h1>帮助与文档</h1>
        <p class="help-description">
          用户手册、开发文档和运维指南由系统设置提供访问地址，实时 API 文档由当前后端服务提供。
        </p>
      </div>
      <div class="help-status">
        <span class="status-dot" />
        可配置文档入口
      </div>
    </header>

    <section v-loading="loading" class="documentation-grid" aria-label="文档入口">
      <el-card
        v-for="entry in entries"
        :key="entry.key"
        shadow="never"
        class="documentation-card"
        :class="[`documentation-card--${entry.tone}`, { 'is-unconfigured': !entry.target }]"
      >
        <div class="card-icon">
          <el-icon :size="26">
            <component :is="entry.icon" />
          </el-icon>
        </div>
        <div class="card-content">
          <h2>{{ entry.title }}</h2>
          <p>{{ entry.description }}</p>
          <el-tag v-if="!entry.target" type="info" effect="plain" round size="small">
            尚未配置访问链接
          </el-tag>
        </div>
        <div class="card-footer">
          <el-button
            type="primary"
            plain
            class="documentation-button"
            :disabled="!entry.target"
            @click="openDocumentation(entry)"
          >
            {{ entry.target ? '打开文档' : '未配置' }}
            <FaIcon v-if="entry.target" name="i-ri:arrow-right-up-line" />
          </el-button>
        </div>
      </el-card>
    </section>
  </div>
</template>

<style scoped>
.help-center {
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: min(1280px, 100%);
  margin: 0 auto;
}

.help-header {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 28px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 16px;
}

.help-kicker {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 700;
  color: var(--el-color-primary);
  letter-spacing: 0.16em;
}

.help-header h1 {
  margin: 0;
  font-size: clamp(26px, 3vw, 38px);
  line-height: 1.2;
  color: var(--el-text-color-primary);
}

.help-description {
  max-width: 680px;
  margin: 12px 0 0;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

.help-status {
  display: inline-flex;
  flex: none;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 999px;
}

.status-dot {
  width: 8px;
  height: 8px;
  background: var(--el-color-success);
  border-radius: 50%;
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--el-color-success) 15%, transparent);
}

.documentation-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  min-height: 220px;
}

.documentation-card {
  position: relative;
  overflow: hidden;
  background: color-mix(in srgb, var(--card-accent, var(--el-color-primary)) 4%, var(--el-bg-color));
  border: 1px solid var(--el-border-color-light);
  border-radius: 16px;
  transition: border-color 160ms ease, transform 160ms ease, box-shadow 160ms ease;
}

.documentation-card:hover {
  border-color: color-mix(in srgb, var(--card-accent, var(--el-color-primary)) 40%, var(--el-border-color));
  box-shadow: 0 12px 30px rgb(0 0 0 / 7%);
  transform: translateY(-2px);
}

.documentation-card.is-unconfigured {
  opacity: 0.72;
}

.documentation-card.is-unconfigured:hover {
  border-color: var(--el-border-color-light);
  box-shadow: none;
  transform: none;
}

.documentation-card :deep(.el-card__body) {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  gap: 18px;
  align-content: start;
  min-height: 196px;
  padding: 18px;
}

.documentation-card--primary {
  --card-accent: var(--el-color-primary);
}

.documentation-card--success {
  --card-accent: var(--el-color-success);
}

.documentation-card--warning {
  --card-accent: var(--el-color-warning);
}

.documentation-card--info {
  --card-accent: var(--el-color-info);
}

.card-icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  color: var(--card-accent);
  background: color-mix(in srgb, var(--card-accent) 12%, transparent);
  border-radius: 14px;
}

.card-content h2 {
  margin: 2px 0 10px;
  font-size: 19px;
  color: var(--el-text-color-primary);
}

.card-content p {
  margin: 0 0 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

.card-footer {
  display: flex;
  grid-column: 1 / -1;
  gap: 16px;
  align-items: center;
  justify-content: flex-end;
  padding-top: 14px;
  margin-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.documentation-button {
  min-width: 116px;
}

@media (width <= 820px) {
  .help-header {
    flex-direction: column;
  }

  .documentation-grid {
    grid-template-columns: 1fr;
  }
}

@media (width <= 520px) {
  .help-header,
  .documentation-card :deep(.el-card__body) {
    padding: 18px;
  }

  .documentation-card :deep(.el-card__body) {
    grid-template-columns: 1fr;
  }

  .card-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .documentation-button {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .documentation-card {
    transition: none;
  }
}
</style>
