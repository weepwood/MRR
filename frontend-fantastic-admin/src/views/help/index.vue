<script setup lang="ts">
import type { Component } from 'vue'
import type { DocumentationSettings } from '@/api/modules/documentation-settings'
import { Network as Connection, FileText as Document, BookOpen as Reading, Settings as Setting } from 'lucide-vue-next'
import { createDocumentationSession } from '@/api/modules/documentation'
import {
  DEFAULT_DOCUMENTATION_SETTINGS,
  getPublicDocumentationSettings,
} from '@/api/modules/documentation-settings'
import AppLoading from '@/components/AppLoading/index.vue'
import { notify } from '@/utils/notification'

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

/**
 * 判断入口是否由后端直接提供。
 * 这些路径受后端文档访问拦截器保护，必须先建立短期文档会话。
 */
function isBackendServedDocument(target: string): boolean {
  return target.startsWith('/swagger-ui') || target.startsWith('/v3/api-docs')
}

async function openDocumentation(entry: DocumentationEntry) {
  if (!entry.target) { return }

  if (!isBackendServedDocument(entry.target)) {
    window.open(entry.target, '_blank', 'noopener')
    return
  }

  // 弹窗必须在用户手势内同步打开，否则 await 之后会被浏览器拦截。
  const popup = window.open('', '_blank')
  try {
    await createDocumentationSession(entry.target)
    if (popup) {
      popup.opener = null
      popup.location.replace(entry.target)
    }
    else {
      window.open(entry.target, '_blank', 'noopener')
    }
  }
  catch {
    popup?.close()
    notify.error('打开实时 API 文档失败，请确认当前账号具备系统查看权限')
  }
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

    <!-- 入口地址来自系统设置；加载完成前不展示可能被覆盖的默认链接。 -->
    <AppLoading v-if="loading" type="card" :cols="3" />
    <section v-else class="documentation-grid" aria-label="文档入口">
      <el-card
        v-for="entry in entries"
        :key="entry.key"
        shadow="never"
        class="documentation-card"
        :class="[`documentation-card--${entry.tone}`, { 'is-unconfigured': !entry.target }]"
      >
        <div class="card-icon">
          <el-icon :size="22">
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
            <FaIcon v-if="entry.target" name="i-lucide:arrow-up-right" />
          </el-button>
        </div>
      </el-card>
    </section>
  </div>
</template>

<style scoped>
/*
 * 页头（.help-header / .help-kicker / h1 / .help-description）的外观全部由 page-header.css 提供，
 * 权重 (0,3,1) 高于这里的 scoped 规则；原先这里的 2xl 圆角、38px 标题、内边距等声明从未生效，已删除。
 */
.help-center {
  display: flex;
  flex-direction: column;
  gap: var(--mrr-page-gap);
  width: min(1280px, 100%);
  margin: 0 auto;
}

.help-status {
  display: inline-flex;
  flex: none;
  gap: var(--mrr-space-2);
  align-items: center;
  padding: var(--mrr-space-2) var(--mrr-space-3);
  font-size: var(--mrr-font-size-sm);
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--mrr-radius-pill);
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
  gap: var(--mrr-space-4);
  min-height: 220px;
}

.documentation-card {
  position: relative;
  overflow: hidden;
  background: color-mix(in srgb, var(--card-accent, var(--el-color-primary)) 4%, var(--el-bg-color));
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--mrr-radius-card);
  transition: border-color var(--mrr-motion-feedback) var(--mrr-ease-out), box-shadow var(--mrr-motion-feedback) var(--mrr-ease-out);
}

/*
 * 卡片本身不可点击（入口是卡内按钮），因此不再上浮 2px —— 位移暗示「整张可点」（DESIGN.md §2.7）。
 * 保留描边加深 + 升一档阴影作为悬停反馈；阴影改用令牌，暖色 / 暗色主题自动适配。
 */
.documentation-card:hover {
  border-color: color-mix(in srgb, var(--card-accent, var(--el-color-primary)) 40%, var(--el-border-color));
  box-shadow: var(--mrr-shadow-sm);
}

.documentation-card.is-unconfigured {
  opacity: 0.72;
}

.documentation-card.is-unconfigured:hover {
  border-color: var(--el-border-color-light);
  box-shadow: none;
}

.documentation-card :deep(.el-card__body) {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: var(--mrr-space-4);
  align-content: start;
  min-height: 196px;
  padding: var(--mrr-card-inset);
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

  /* 与指标卡图标块同尺寸（44px / radius-lg），全站「图标 + 标题」卡片只有一种比例。 */
  width: 44px;
  height: 44px;
  color: var(--card-accent);
  background: color-mix(in srgb, var(--card-accent) 12%, transparent);
  border-radius: var(--mrr-radius-lg);
}

/*
 * Chrome 86 回退：文档卡的着色底面与悬停描边是该卡「属于哪个系统入口」的
 * 分层信号；图标块的浅色底若失效，图标只剩裸字形浮在卡面上。
 */
@supports not (color: color-mix(in srgb, #000 50%, #fff)) {
  .documentation-card {
    background: var(--mrr-card);
  }

  .documentation-card:hover {
    border-color: var(--card-accent);
  }

  .card-icon {
    background: var(--mrr-tone-neutral-soft);
  }

  .documentation-card--primary .card-icon {
    background: var(--mrr-tone-primary-soft);
  }

  .documentation-card--success .card-icon {
    background: var(--mrr-tone-success-soft);
  }

  .documentation-card--warning .card-icon {
    background: var(--mrr-tone-warning-soft);
  }

  .documentation-card--info .card-icon {
    background: var(--mrr-tone-info-soft);
  }
}

.card-content h2 {
  margin: 2px 0 var(--mrr-space-2);
  font-size: var(--mrr-font-size-lg);
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.card-content p {
  margin: 0 0 var(--mrr-space-3);
  font-size: var(--mrr-font-size-sm);
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

.card-footer {
  display: flex;
  grid-column: 1 / -1;
  gap: var(--mrr-space-4);
  align-items: center;
  justify-content: flex-end;
  padding-top: var(--mrr-space-3);
  margin-top: var(--mrr-space-4);
  border-top: 1px solid var(--el-border-color-lighter);
}

.documentation-button {
  min-width: 116px;
}

@media (max-width: 820px) {
  .documentation-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 520px) {
  .documentation-card :deep(.el-card__body) {
    padding: var(--mrr-card-inset-compact);
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
</style>
