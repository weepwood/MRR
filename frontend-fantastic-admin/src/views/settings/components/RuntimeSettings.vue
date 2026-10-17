<script setup lang="ts">
import type { SettingSchema, SettingSchemaItem } from '@/api/modules/settings'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { deleteSetting, getSettingsSchema, saveSystemSettings } from '@/api/modules/settings'

const schema = ref<SettingSchema>({ groups: [], items: [] })
const booleanValues = reactive<Record<string, boolean>>({})
const numberValues = reactive<Record<string, number>>({})
const textValues = reactive<Record<string, string>>({})
/** 数据库中的显式覆盖值；空串表示"未覆盖，沿用配置文件与环境变量"。 */
const overrides = reactive<Record<string, string>>({})
const savedMap = ref<Record<string, string>>({})
const loading = ref(false)
const saving = ref(false)

function isNumeric(item: SettingSchemaItem) {
  return item.type === 'integer' || item.type === 'decimal'
}

function serializeOne(item: SettingSchemaItem): string {
  if (item.type === 'boolean') {
    return booleanValues[item.key] ? 'true' : 'false'
  }
  if (item.type === 'integer') {
    return String(Math.round(numberValues[item.key] ?? 0))
  }
  if (item.type === 'decimal') {
    return String(numberValues[item.key] ?? 0)
  }
  return (textValues[item.key] ?? '').trim()
}

const changedItems = computed(() => schema.value.items.filter(
  item => serializeOne(item) !== (savedMap.value[item.key] ?? ''),
))
const isDirty = computed(() => changedItems.value.length > 0)

function itemsOf(groupKey: string) {
  return schema.value.items.filter(item => item.group === groupKey)
}

function isOverridden(item: SettingSchemaItem) {
  return (overrides[item.key] ?? '').trim().length > 0
}

function rangeLabel(item: SettingSchemaItem) {
  if (item.min === null && item.max === null) {
    return ''
  }
  const unit = item.unit ? ` ${item.unit}` : ''
  return `取值范围 ${item.min ?? '-'} 至 ${item.max ?? '-'}${unit}`
}

function defaultValueLabel(item: SettingSchemaItem) {
  return item.defaultValue === '' ? '空' : item.defaultValue
}

async function loadSettings() {
  loading.value = true
  try {
    const response = await getSettingsSchema()
    const payload = response.data
    if (!payload) {
      throw new Error('服务端未返回设置元数据')
    }
    schema.value = payload
    for (const item of payload.items) {
      const effective = String(item.effectiveValue ?? '').trim()
      const override = String(item.overrideValue ?? '').trim()
      overrides[item.key] = override
      if (item.type === 'boolean') {
        booleanValues[item.key] = effective === 'true'
      }
      else if (isNumeric(item)) {
        const parsed = Number(effective)
        numberValues[item.key] = Number.isFinite(parsed) ? parsed : 0
      }
      else {
        textValues[item.key] = effective
      }
    }
    savedMap.value = Object.fromEntries(payload.items.map(item => [item.key, serializeOne(item)]))
  }
  catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '运行参数加载失败')
  }
  finally {
    loading.value = false
  }
}

async function saveSettings(): Promise<boolean> {
  if (!changedItems.value.length) {
    return false
  }
  const sensitiveItems = changedItems.value.filter(item => item.sensitive)
  if (sensitiveItems.length > 0) {
    try {
      await ElMessageBox.confirm(
        `本次修改包含 ${sensitiveItems.length} 项安全与留存相关配置：${sensitiveItems.map(item => item.label).join('、')}。`
        + '保存后立即影响所有用户，且不需要重启服务。',
        '确认修改敏感配置',
        { type: 'warning', confirmButtonText: '确认保存', cancelButtonText: '取消' },
      )
    }
    catch {
      return false
    }
  }

  saving.value = true
  try {
    const payload: Record<string, string> = {}
    for (const item of changedItems.value) {
      payload[item.key] = serializeOne(item)
    }
    await saveSystemSettings(payload)
    await loadSettings()
    ElMessage.success('运行参数已保存并立即生效')
    return true
  }
  catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '运行参数保存失败')
    return false
  }
  finally {
    saving.value = false
  }
}

async function clearOverride(item: SettingSchemaItem) {
  try {
    await ElMessageBox.confirm(
      `将删除「${item.label}」在数据库中的覆盖值，回落到配置文件与环境变量提供的默认值（${defaultValueLabel(item)}）。`,
      '恢复配置默认',
      { type: 'warning', confirmButtonText: '确认恢复', cancelButtonText: '取消' },
    )
  }
  catch {
    return
  }
  try {
    await deleteSetting(item.key)
    await loadSettings()
    ElMessage.success(`${item.label} 已恢复为配置默认值`)
  }
  catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '恢复默认失败')
  }
}

function fillWithConfigDefaults() {
  for (const item of schema.value.items) {
    if (item.type === 'boolean') {
      booleanValues[item.key] = item.defaultValue === 'true'
    }
    else if (isNumeric(item)) {
      const parsed = Number(item.defaultValue)
      numberValues[item.key] = Number.isFinite(parsed) ? parsed : 0
    }
    else {
      textValues[item.key] = item.defaultValue
    }
  }
  ElMessage.info('已填入配置文件默认值，保存后才会写入数据库并覆盖配置')
}

defineExpose({ saving, isDirty, saveSettings, loadSettings, fillWithConfigDefaults })

onMounted(loadSettings)
</script>

<template>
  <section v-loading="loading" class="setting-section">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="数据库留空的项会沿用部署配置（application.properties 与环境变量），只有在后台填写后才会覆盖配置。"
      description="因此升级后行为与升级前一致；如需撤销某次后台修改，点击该项下方的「恢复配置默认」即可。"
    />

    <header class="panel-actions">
      <div class="actions-copy">
        <strong>{{ isDirty ? `${changedItems.length} 项修改待保存` : '运行参数已同步' }}</strong>
        <p>保存后立即影响后续请求，无需重启服务。</p>
      </div>
      <div class="actions-buttons">
        <el-button :disabled="loading || saving" @click="loadSettings">
          <FaIcon name="i-ri:refresh-line" class="mrr-icon-interactive" />重新加载
        </el-button>
        <el-button :disabled="loading || saving" @click="fillWithConfigDefaults">填入配置默认值</el-button>
      </div>
    </header>

    <div v-for="group in schema.groups" :key="group.key" class="setting-group">
      <div class="group-heading">
        <strong>{{ group.label }}</strong>
        <p>全部取值由服务端再次校验，超出安全值域的配置会被拒绝。</p>
      </div>
      <el-form label-position="top" @submit.prevent>
        <div class="control-grid">
          <el-form-item v-for="item in itemsOf(group.key)" :key="item.key">
            <template #label>
              <span class="field-label">
                {{ item.label }}
                <el-tag v-if="isOverridden(item)" size="small" type="warning" effect="plain">数据库覆盖</el-tag>
                <el-tag v-else size="small" type="info" effect="plain">沿用配置</el-tag>
                <el-tag v-if="item.effect === 'RESTART_REQUIRED'" size="small" type="danger" effect="plain">需重启</el-tag>
              </span>
            </template>

            <el-switch
              v-if="item.type === 'boolean'"
              v-model="booleanValues[item.key]"
              :disabled="item.effect === 'RESTART_REQUIRED'"
            />
            <div v-else-if="isNumeric(item)" class="number-control">
              <el-input-number
                v-model="numberValues[item.key]"
                :min="item.min ?? undefined"
                :max="item.max ?? undefined"
                :step="item.type === 'decimal' ? 0.01 : 1"
                :precision="item.type === 'decimal' ? 2 : 0"
                :disabled="item.effect === 'RESTART_REQUIRED'"
                controls-position="right"
              />
              <span v-if="item.unit">{{ item.unit }}</span>
            </div>
            <el-select
              v-else-if="item.type === 'enum'"
              v-model="textValues[item.key]"
              :disabled="item.effect === 'RESTART_REQUIRED'"
              class="full-width"
            >
              <el-option v-for="value in item.allowedValues" :key="value" :label="value" :value="value" />
            </el-select>
            <el-input
              v-else
              v-model="textValues[item.key]"
              :maxlength="item.max ?? undefined"
              :disabled="item.effect === 'RESTART_REQUIRED'"
            />

            <p class="field-hint">{{ item.description }}</p>
            <p class="field-meta">
              <span>配置默认值 {{ defaultValueLabel(item) }}<template v-if="item.unit"> {{ item.unit }}</template></span>
              <span v-if="rangeLabel(item)">{{ rangeLabel(item) }}</span>
            </p>
            <el-button
              v-if="isOverridden(item)"
              link
              type="primary"
              size="small"
              class="reset-link"
              @click="clearOverride(item)"
            >
              恢复配置默认
            </el-button>
          </el-form-item>
        </div>
      </el-form>
    </div>
  </section>
</template>

<style scoped>
.setting-section { display: grid; gap: var(--mrr-space-5); }

.panel-actions {
  display: flex;
  gap: var(--mrr-space-4);
  align-items: center;
  justify-content: space-between;
  padding: var(--mrr-space-4) var(--mrr-space-5);
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-xl);
}

.actions-copy strong { font-size: 13px; }
.actions-copy p { margin: 3px 0 0; font-size: 10px; color: var(--mrr-muted-foreground); }
.actions-buttons { display: flex; flex-shrink: 0; gap: var(--mrr-space-2); }

.setting-group { padding: var(--mrr-space-5); background: var(--mrr-card); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-xl); }
.group-heading { margin-bottom: var(--mrr-space-4); }
.group-heading strong { font-size: 15px; }
.group-heading p { margin: 4px 0 0; font-size: 11px; color: var(--mrr-muted-foreground); }

.control-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--mrr-space-4); }
.field-label { display: inline-flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.field-hint { margin: 6px 0 0; font-size: 10px; line-height: 1.6; color: var(--mrr-muted-foreground); }
.field-meta { display: flex; flex-wrap: wrap; gap: var(--mrr-space-3); margin: 4px 0 0; font-size: 10px; color: var(--mrr-muted-foreground); }
.number-control { display: flex; gap: var(--mrr-space-2); align-items: center; }
.full-width { width: 100%; }
.reset-link { padding: 0; margin-top: 4px; }

@media (max-width: 680px) {
  .control-grid { grid-template-columns: 1fr; }
  .panel-actions { flex-direction: column; align-items: stretch; }
  .actions-buttons { justify-content: flex-end; }
}
</style>
