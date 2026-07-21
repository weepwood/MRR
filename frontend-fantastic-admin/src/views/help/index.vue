<script setup lang="ts">
import type { Component } from 'vue'
import { Connection, Document, Reading, Setting } from '@element-plus/icons-vue'

defineOptions({ name: 'HelpCenter' })

interface DocumentationEntry {
  title: string
  description: string
  target: string
  icon: Component
  tone: string
}

const entries: DocumentationEntry[] = [
  {
    title: '用户使用手册',
    description: '查看病案管理、影像浏览、统计分析和系统操作说明。',
    target: 'http://192.2.1.135:8002/docs',
    icon: Reading,
    tone: 'primary',
  },
  {
    title: '开发文档',
    description: '查看前后端架构、开发流程、接口约定和数据库设计。',
    target: 'http://192.2.1.135:8002/docs/internal/',
    icon: Document,
    tone: 'success',
  },
  {
    title: '运维指南',
    description: '查看部署、日志、备份恢复、监控和故障处理说明。',
    target: 'http://192.2.1.135:8002/docs/internal/internal/deployment.html',
    icon: Setting,
    tone: 'warning',
  },
  {
    title: '实时 API 文档',
    description: '打开由 Springdoc OpenAPI 实时生成的 Swagger UI。',
    target: 'http://192.2.1.135:18045/swagger-ui/index.html#/',
    icon: Connection,
    tone: 'info',
  },
]

function openDocumentation(entry: DocumentationEntry) {
  window.open(entry.target, '_blank', 'noopener')
}
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
          用户手册、内部文档和 API 文档均可直接打开。
        </p>
      </div>
      <div class="help-status">
        <span class="status-dot" />
        VitePress + Springdoc
      </div>
    </header>

    <section class="documentation-grid" aria-label="文档入口">
      <el-card
        v-for="entry in entries"
        :key="entry.target"
        shadow="never"
        class="documentation-card"
        :class="`documentation-card--${entry.tone}`"
      >
        <div class="card-icon">
          <el-icon :size="26">
            <component :is="entry.icon" />
          </el-icon>
        </div>
        <div class="card-content">
          <h2>{{ entry.title }}</h2>
          <p>{{ entry.description }}</p>
        </div>
        <div class="card-footer">
          <el-button
            type="primary"
            plain
            class="documentation-button"
            @click="openDocumentation(entry)"
          >
            打开文档
            <FaIcon name="i-ri:arrow-right-up-line" />
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
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 16px;
  background: var(--el-bg-color);
}

.help-kicker {
  margin: 0 0 8px;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.help-header h1 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: clamp(26px, 3vw, 38px);
  line-height: 1.2;
}

.help-description {
  max-width: 680px;
  margin: 12px 0 0;
  color: var(--el-text-color-secondary);
  line-height: 1.7;
}

.help-status {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 999px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  font-size: 13px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-success);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--el-color-success) 15%, transparent);
}

.documentation-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.documentation-card {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--el-border-color-light);
  border-radius: 16px;
  background: color-mix(in srgb, var(--card-accent, var(--el-color-primary)) 4%, var(--el-bg-color));
  transition: border-color 160ms ease, transform 160ms ease, box-shadow 160ms ease;
}

.documentation-card:hover {
  border-color: color-mix(in srgb, var(--card-accent, var(--el-color-primary)) 40%, var(--el-border-color));
  box-shadow: 0 12px 30px rgb(0 0 0 / 7%);
  transform: translateY(-2px);
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
  width: 52px;
  height: 52px;
  place-items: center;
  border-radius: 14px;
  color: var(--card-accent);
  background: color-mix(in srgb, var(--card-accent) 12%, transparent);
}

.card-content h2 {
  margin: 2px 0 10px;
  color: var(--el-text-color-primary);
  font-size: 19px;
}

.card-content p {
  margin: 0;
  color: var(--el-text-color-secondary);
  line-height: 1.7;
}

.card-footer {
  display: flex;
  grid-column: 1 / -1;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.documentation-button {
  min-width: 116px;
}

@media (max-width: 820px) {
  .help-header {
    flex-direction: column;
  }

  .documentation-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 520px) {
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
