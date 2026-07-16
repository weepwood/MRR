<script setup lang="ts">
import type { Component } from 'vue'
import { Connection, Document, Reading, Setting } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { createDocumentationSession } from '@/api/modules/documentation'

defineOptions({ name: 'HelpCenter' })

interface DocumentationEntry {
  title: string
  description: string
  target: string
  icon: Component
  permission: string
  tone: string
}

const route = useRoute()
const openingTarget = ref('')

const redirectedForAuthentication = computed(() => route.query.docsAuth === 'required')

const entries: DocumentationEntry[] = [
  {
    title: '用户使用手册',
    description: '查看病案管理、影像浏览、统计分析和系统操作说明。',
    target: '/docs/',
    icon: Reading,
    permission: '登录用户',
    tone: 'primary',
  },
  {
    title: '开发文档',
    description: '查看前后端架构、开发流程、接口约定和数据库设计。',
    target: '/docs/internal/',
    icon: Document,
    permission: '需要 system:read',
    tone: 'success',
  },
  {
    title: '运维指南',
    description: '查看部署、日志、备份恢复、监控和故障处理说明。',
    target: '/docs/internal/internal/deployment.html',
    icon: Setting,
    permission: '需要 system:read',
    tone: 'warning',
  },
  {
    title: '实时 API 文档',
    description: '打开由 Springdoc OpenAPI 实时生成的 Swagger UI。',
    target: '/api-docs/',
    icon: Connection,
    permission: '需要 system:read',
    tone: 'info',
  },
]

function getErrorMessage(error: unknown) {
  if (error && typeof error === 'object' && 'message' in error) {
    const message = (error as { message?: unknown }).message
    if (typeof message === 'string' && message.trim()) {
      return message
    }
  }
  return '无法创建文档访问会话'
}

async function openDocumentation(entry: DocumentationEntry) {
  if (openingTarget.value) {
    return
  }

  openingTarget.value = entry.target
  try {
    const result = await createDocumentationSession(entry.target)
    const target = result.data?.target || entry.target
    window.location.assign(target)
  }
  catch (error: unknown) {
    ElMessage.error(getErrorMessage(error))
  }
  finally {
    openingTarget.value = ''
  }
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
          用户手册与内部文档采用独立构建。打开文档前会创建一个 30 分钟有效的 HttpOnly 访问会话。
        </p>
      </div>
      <div class="help-status">
        <span class="status-dot" />
        VitePress + Springdoc
      </div>
    </header>

    <el-alert
      v-if="redirectedForAuthentication"
      class="auth-alert"
      title="文档访问会话已失效"
      description="请从下方重新打开文档，系统会根据当前账号权限创建新的访问会话。"
      type="warning"
      show-icon
      :closable="false"
    />

    <section class="documentation-grid" aria-label="文档入口">
      <article
        v-for="entry in entries"
        :key="entry.target"
        class="documentation-card"
        :class="`documentation-card--${entry.tone}`"
      >
        <div class="card-icon">
          <el-icon :size="25">
            <component :is="entry.icon" />
          </el-icon>
        </div>
        <div class="card-content">
          <h2>{{ entry.title }}</h2>
          <p>{{ entry.description }}</p>
        </div>
        <div class="card-footer">
          <span>{{ entry.permission }}</span>
          <el-button
            type="primary"
            plain
            :loading="openingTarget === entry.target"
            :disabled="Boolean(openingTarget) && openingTarget !== entry.target"
            @click="openDocumentation(entry)"
          >
            打开文档
          </el-button>
        </div>
      </article>
    </section>

    <el-card class="access-note" shadow="never">
      <template #header>
        <strong>访问控制说明</strong>
      </template>
      <div class="access-levels">
        <div>
          <span class="level-index">01</span>
          <div>
            <h3>用户文档</h3>
            <p>任何已登录账号均可访问，搜索索引仅包含用户手册。</p>
          </div>
        </div>
        <div>
          <span class="level-index">02</span>
          <div>
            <h3>内部文档</h3>
            <p>开发、架构和运维内容要求 system:read 权限。</p>
          </div>
        </div>
        <div>
          <span class="level-index">03</span>
          <div>
            <h3>实时 API</h3>
            <p>Swagger UI 与 OpenAPI JSON 使用相同的内部文档权限。</p>
          </div>
        </div>
      </div>
    </el-card>
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

.auth-alert {
  border-radius: 12px;
}

.documentation-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.documentation-card {
  position: relative;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 16px;
  min-height: 220px;
  padding: 22px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-light);
  border-radius: 16px;
  background: var(--el-bg-color);
  transition: border-color 160ms ease, transform 160ms ease, box-shadow 160ms ease;
}

.documentation-card::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: var(--card-accent, var(--el-color-primary));
  content: '';
}

.documentation-card:hover {
  border-color: color-mix(in srgb, var(--card-accent, var(--el-color-primary)) 40%, var(--el-border-color));
  box-shadow: 0 12px 30px rgb(0 0 0 / 7%);
  transform: translateY(-2px);
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
  justify-content: space-between;
  gap: 16px;
  margin-top: auto;
  padding-top: 18px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.card-footer span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.access-note {
  border-radius: 16px;
}

.access-levels {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}

.access-levels > div {
  display: flex;
  gap: 12px;
}

.level-index {
  color: var(--el-color-primary);
  font-size: 13px;
  font-weight: 700;
}

.access-levels h3 {
  margin: 0 0 6px;
  color: var(--el-text-color-primary);
  font-size: 15px;
}

.access-levels p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 820px) {
  .help-header {
    flex-direction: column;
  }

  .documentation-grid,
  .access-levels {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 520px) {
  .help-header,
  .documentation-card {
    padding: 18px;
  }

  .documentation-card {
    grid-template-columns: 1fr;
  }

  .card-footer {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (prefers-reduced-motion: reduce) {
  .documentation-card {
    transition: none;
  }
}
</style>
