<script setup lang="ts">
import { useElementSize } from '@vueuse/core'
import AppEmpty from '@/components/AppEmpty/index.vue'
import { actionDialog } from '@/ui/components/ActionDialog'
import { createExternalLinkId, normalizeExternalLinkUrl } from '@/utils/external-links'
import { notify } from '@/utils/notification'

defineOptions({ name: 'ExternalLinksSettings' })

const settingsStore = useSettingsStore()
const layoutRef = ref<HTMLElement>()
const { width: layoutWidth } = useElementSize(layoutRef)
const compactLayout = computed(() => layoutWidth.value > 0 && layoutWidth.value < 720)
const narrowLayout = computed(() => layoutWidth.value > 0 && layoutWidth.value < 440)
const draft = reactive({
  title: '',
  url: '',
})
const externalLinkUrlPlaceholder = 'https://example.com'

const externalLinks = computed(() => settingsStore.settings.menu.externalLinks)

/**
 * 生效范围提示。外链属于全站界面配置，写入服务端需要 system:manage 权限；
 * 无权限时只能保存在本机，必须在界面上如实说明，避免承诺未生效的同步。
 */
const linkScopeDescription = computed(() => {
  if (settingsStore.uiSettingsSyncState === 'local-only') {
    return '这些链接保存在本机浏览器。当前账号缺少系统管理权限，修改不会同步到全站。'
  }
  if (settingsStore.uiSettingsSyncState === 'failed') { return '链接已保存在本机浏览器，但全站同步失败。' }
  if (settingsStore.uiSettingsSyncState === 'synced') { return '这些链接已同步到全站的“其他”菜单。' }
  return '链接先保存在本机；全站同步取决于权限和服务器返回结果。'
})

defineExpose({ linkScopeDescription })

function persistSettings() {
  settingsStore.saveAppSettings()
}

function resetDraft() {
  draft.title = ''
  draft.url = ''
}

function addExternalLink() {
  const title = draft.title.trim()
  const url = normalizeExternalLinkUrl(draft.url)

  if (!title) {
    notify.warning('请填写外链名称')
    return
  }
  if (title.length > 40) {
    notify.warning('外链名称不能超过 40 个字符')
    return
  }
  if (!url) {
    notify.warning('请输入有效的 HTTP 或 HTTPS 地址')
    return
  }
  if (externalLinks.value.length >= 30) {
    notify.warning('最多可以添加 30 个外部链接')
    return
  }
  if (externalLinks.value.some(item => normalizeExternalLinkUrl(item.url) === url)) {
    notify.warning('该网站已经添加')
    return
  }

  externalLinks.value.push({
    id: createExternalLinkId(),
    title,
    url,
    icon: 'i-lucide:external-link',
  })
  persistSettings()
  resetDraft()
  notify.success('外部链接已添加到“其他”菜单')
}

async function removeExternalLink(index: number, title: string) {
  try {
    await actionDialog.confirm(
      `确定从“其他”菜单中移除“${title}”吗？`,
      '移除外部链接',
      {
        type: 'warning',
        confirmButtonText: '移除',
        cancelButtonText: '取消',
      },
    )
  }
  catch {
    return
  }

  externalLinks.value.splice(index, 1)
  persistSettings()
  notify.success('外部链接已移除')
}

function openExternalLink(url: string) {
  const normalizedUrl = normalizeExternalLinkUrl(url)
  if (!normalizedUrl) {
    notify.warning('该链接地址无效')
    return
  }
  window.open(normalizedUrl, '_blank', 'noopener,noreferrer')
}
</script>

<template>
  <div ref="layoutRef" class="external-links-settings" :class="{ 'external-links-settings--compact': compactLayout, 'external-links-settings--narrow': narrowLayout }">
    <section class="settings-card add-link-card">
      <header class="card-header">
        <div class="header-icon">
          <FaIcon name="i-lucide:circle-plus" />
        </div>
        <div>
          <h4 data-settings-search-target="add-external-link" tabindex="-1">
            添加外部网站
          </h4>
          <p>添加后会出现在单栏左侧导航的“其他”一级菜单中，并在新标签页打开。</p>
        </div>
      </header>

      <div class="link-form">
        <label class="field-group">
          <span>显示名称</span>
          <el-input
            v-model="draft.title"
            maxlength="40"
            show-word-limit
            clearable
            placeholder="例如：医院官网"
            @keyup.enter="addExternalLink"
          />
        </label>
        <label class="field-group field-group--url">
          <span>网站地址</span>
          <el-input
            v-model="draft.url"
            clearable
            :placeholder="externalLinkUrlPlaceholder"
            @keyup.enter="addExternalLink"
          >
            <template #prefix>
              <FaIcon name="i-lucide:globe" />
            </template>
          </el-input>
        </label>
        <el-button type="primary" class="add-button" @click="addExternalLink">
          <FaIcon name="i-lucide:plus" />
          添加外链
        </el-button>
      </div>
      <p class="form-hint">
        未填写协议时会自动使用 HTTPS。出于安全考虑，仅允许 HTTP 和 HTTPS 地址。
      </p>
    </section>

    <section class="settings-card link-list-card">
      <header class="card-header card-header--list">
        <div class="header-icon">
          <FaIcon name="i-lucide:link-2" />
        </div>
        <div class="header-copy">
          <h4>“其他”菜单</h4>
          <p>{{ linkScopeDescription }}</p>
        </div>
        <el-tag effect="plain" round>
          {{ externalLinks.length }} / 30
        </el-tag>
      </header>

      <AppEmpty v-if="externalLinks.length === 0" description="尚未添加外部网站" icon="i-lucide:link" />

      <div v-else class="external-link-list">
        <article v-for="(link, index) in externalLinks" :key="link.id" class="external-link-item">
          <span class="link-icon">
            <FaIcon :name="link.icon || 'i-lucide:external-link'" />
          </span>
          <div class="link-copy">
            <strong>{{ link.title }}</strong>
            <small :title="link.url">{{ link.url }}</small>
          </div>
          <div class="link-actions">
            <el-button text type="primary" @click="openExternalLink(link.url)">
              <FaIcon name="i-lucide:external-link" />
              访问
            </el-button>
            <el-button text type="danger" @click="removeExternalLink(index, link.title)">
              <FaIcon name="i-lucide:trash-2" />
              移除
            </el-button>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.external-links-settings {
  display: grid;
  gap: var(--mrr-space-4);
  min-width: 0;
}

.settings-card {
  min-width: 0;
  padding: var(--settings-group-inset, var(--mrr-card-inset));
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-card);
}

.card-header {
  display: flex;
  gap: var(--mrr-space-3);
  align-items: center;
  margin-bottom: var(--mrr-space-4);
}

.card-header--list {
  margin-bottom: 12px;
}

.header-icon {
  display: grid;
  flex: 0 0 38px;
  place-items: center;
  width: 38px;
  height: 38px;
  font-size: 18px;
  color: var(--mrr-tone-primary-ink);
  background: var(--mrr-tone-primary-soft);
  border-radius: var(--mrr-radius-lg);
}

.header-copy {
  flex: 1;
  min-width: 0;
}

.card-header > div:not(.header-icon) { min-width: 0; }

.card-header h4,
.card-header p {
  margin: 0;
}

.card-header h4 {
  font-size: var(--mrr-font-size-md);
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.card-header p {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--mrr-muted-foreground);
}

.link-form {
  display: grid;
  grid-template-columns: minmax(0, 280px) minmax(0, 1fr) max-content;
  gap: var(--mrr-space-3);
  align-items: end;
  max-width: 960px;
}

.field-group {
  display: grid;
  gap: var(--mrr-space-2);
  min-width: 0;
}

.field-group > span {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.add-button {
  gap: var(--mrr-space-2);
  justify-self: start;
  min-width: 108px;
  margin: 0;
}

.form-hint {
  margin: var(--mrr-space-2) 0 0;
  font-size: var(--mrr-font-size-xs);
  line-height: 1.6;
  color: var(--mrr-muted-foreground);
}

.external-link-list {
  display: grid;
  gap: 8px;
}

.external-link-item {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
  padding: var(--mrr-space-3);
  background: var(--mrr-muted);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-lg);
}

.link-icon {
  display: grid;
  flex: 0 0 34px;
  place-items: center;
  width: 34px;
  height: 34px;
  color: var(--el-color-primary);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--mrr-radius-md);
}

.link-copy {
  flex: 1;
  min-width: 0;
}

.link-copy strong,
.link-copy small {
  display: block;
}

.link-copy strong {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.link-copy small {
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--mrr-font-size-xs);
  line-height: 1.6;
  color: var(--mrr-muted-foreground);
  white-space: nowrap;
}

.link-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 2px;
  align-items: center;
}

.link-actions :deep(.el-button) {
  gap: 5px;
  margin-left: 0;
}

/* The workspace can shrink independently of the viewport (app sidebar). */
.external-links-settings--compact .link-form {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.external-links-settings--compact .add-button {
  grid-column: 1 / -1;
  justify-self: end;
}

.external-links-settings--narrow .link-form {
  grid-template-columns: minmax(0, 1fr);
}

.external-links-settings--narrow .add-button {
  width: 100%;
}

.external-links-settings--narrow .external-link-item {
  flex-wrap: wrap;
  align-items: flex-start;
}

.external-links-settings--narrow .link-actions {
  justify-content: flex-end;
  width: 100%;
}
</style>
