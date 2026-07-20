<script setup lang="ts">
import { ElMessageBox } from 'element-plus'
import { toast } from 'vue-sonner'
import { createExternalLinkId, normalizeExternalLinkUrl } from '@/utils/external-links'

defineOptions({ name: 'ExternalLinksSettings' })

const settingsStore = useSettingsStore()
const draft = reactive({
  title: '',
  url: '',
})

const externalLinks = computed(() => settingsStore.settings.menu.externalLinks)

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
    toast.warning('请填写外链名称')
    return
  }
  if (title.length > 40) {
    toast.warning('外链名称不能超过 40 个字符')
    return
  }
  if (!url) {
    toast.warning('请输入有效的 HTTP 或 HTTPS 地址')
    return
  }
  if (externalLinks.value.length >= 30) {
    toast.warning('最多可以添加 30 个外部链接')
    return
  }
  if (externalLinks.value.some(item => normalizeExternalLinkUrl(item.url) === url)) {
    toast.warning('该网站已经添加')
    return
  }

  externalLinks.value.push({
    id: createExternalLinkId(),
    title,
    url,
    icon: 'i-ri:external-link-line',
  })
  persistSettings()
  resetDraft()
  toast.success('外部链接已添加到“其他”菜单')
}

async function removeExternalLink(index: number, title: string) {
  try {
    await ElMessageBox.confirm(
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
  toast.success('外部链接已移除')
}

function openExternalLink(url: string) {
  const normalizedUrl = normalizeExternalLinkUrl(url)
  if (!normalizedUrl) {
    toast.warning('该链接地址无效')
    return
  }
  window.open(normalizedUrl, '_blank', 'noopener,noreferrer')
}
</script>

<template>
  <div class="external-links-settings">
    <section class="settings-card add-link-card">
      <header class="card-header">
        <div class="header-icon">
          <FaIcon name="i-ri:add-circle-line" />
        </div>
        <div>
          <h4>添加外部网站</h4>
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
            placeholder="https://example.com"
            @keyup.enter="addExternalLink"
          >
            <template #prefix>
              <FaIcon name="i-ri:global-line" />
            </template>
          </el-input>
        </label>
        <el-button type="primary" class="add-button" @click="addExternalLink">
          <FaIcon name="i-ri:add-line" />
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
          <FaIcon name="i-ri:links-line" />
        </div>
        <div class="header-copy">
          <h4>“其他”菜单</h4>
          <p>这些链接只保存在当前浏览器，可通过界面配置导入和导出。</p>
        </div>
        <el-tag effect="plain" round>
          {{ externalLinks.length }} / 30
        </el-tag>
      </header>

      <el-empty v-if="externalLinks.length === 0" description="尚未添加外部网站" :image-size="72" />

      <div v-else class="external-link-list">
        <article v-for="(link, index) in externalLinks" :key="link.id" class="external-link-item">
          <span class="link-icon">
            <FaIcon :name="link.icon || 'i-ri:external-link-line'" />
          </span>
          <div class="link-copy">
            <strong>{{ link.title }}</strong>
            <small :title="link.url">{{ link.url }}</small>
          </div>
          <div class="link-actions">
            <el-button text type="primary" @click="openExternalLink(link.url)">
              <FaIcon name="i-ri:external-link-line" />
              访问
            </el-button>
            <el-button text type="danger" @click="removeExternalLink(index, link.title)">
              <FaIcon name="i-ri:delete-bin-6-line" />
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
  gap: 16px;
  max-width: 920px;
}

.settings-card {
  padding: 18px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.card-header {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 18px;
}

.card-header--list {
  margin-bottom: 12px;
}

.header-icon {
  display: grid;
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  font-size: 18px;
  color: var(--el-color-primary);
  background: color-mix(in srgb, var(--el-color-primary) 10%, var(--el-bg-color));
  border-radius: 10px;
  place-items: center;
}

.header-copy {
  flex: 1;
  min-width: 0;
}

.card-header h4,
.card-header p {
  margin: 0;
}

.card-header h4 {
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.card-header p {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.link-form {
  display: grid;
  grid-template-columns: minmax(180px, 0.8fr) minmax(280px, 1.4fr) auto;
  gap: 12px;
  align-items: end;
}

.field-group {
  display: grid;
  gap: 7px;
  min-width: 0;
}

.field-group > span {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.add-button {
  gap: 6px;
  min-width: 108px;
}

.form-hint {
  margin: 10px 0 0;
  font-size: 11px;
  color: var(--el-text-color-secondary);
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
  padding: 11px 12px;
  background: var(--el-fill-color-extra-light);
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: 10px;
}

.link-icon {
  display: grid;
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  color: var(--el-color-primary);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 9px;
  place-items: center;
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
  font-size: 13px;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.link-copy small {
  margin-top: 3px;
  overflow: hidden;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
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

@media (max-width: 760px) {
  .link-form {
    grid-template-columns: 1fr;
  }

  .add-button {
    width: 100%;
  }

  .external-link-item {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .link-actions {
    width: 100%;
    padding-left: 46px;
  }
}
</style>
