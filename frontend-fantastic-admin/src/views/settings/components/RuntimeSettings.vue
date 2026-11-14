<script setup lang="ts">
import type { SettingSchema, SettingSchemaItem } from '@/api/modules/settings'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref } from 'vue'
import { deleteSetting, getSettingsSchema, saveSystemSettings } from '@/api/modules/settings'
import { DIRECTORY_ROUTING_KEY } from './image-directory-routing'

const schema = ref<SettingSchema>({ groups: [], items: [] })
const booleanValues = reactive<Record<string, boolean>>({})
const numberValues = reactive<Record<string, number>>({})
const textValues = reactive<Record<string, string>>({})
/** 数据库中的显式覆盖值；空串表示"未覆盖，沿用配置文件与环境变量"。 */
const overrides = reactive<Record<string, string>>({})
const savedMap = ref<Record<string, string>>({})
const loading = ref(false)
const saving = ref(false)
const loaded = ref(false)
const needsReload = ref(false)
const active = ref(true)
const feedback = ref('')
let alive = true
let generation = 0
const isCurrent = (attempt: number) => alive && active.value && generation === attempt
const controlsDisabled = computed(() => !active.value || loading.value || saving.value || !loaded.value || needsReload.value)

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

/** 先构造完整快照，成功且仍属当前激活代次时才更新页面。 */
async function readSettings(attempt: number): Promise<boolean> {
  const response = await getSettingsSchema()
  if (!isCurrent(attempt)) { return false }
  const payload = response.data
  if (!payload || !Array.isArray(payload.items) || !Array.isArray(payload.groups)) {
    throw new Error('missing schema')
  }
  const items = payload.items.filter(item => item.key !== DIRECTORY_ROUTING_KEY)
  const booleans: Record<string, boolean> = {}
  const numbers: Record<string, number> = {}
  const texts: Record<string, string> = {}
  const rawOverrides: Record<string, string> = {}
  const saved: Record<string, string> = {}
  for (const item of items) {
    const effective = String(item.effectiveValue ?? '').trim()
    rawOverrides[item.key] = String(item.overrideValue ?? '').trim()
    if (item.type === 'boolean') {
      booleans[item.key] = effective === 'true'
      saved[item.key] = booleans[item.key] ? 'true' : 'false'
    }
    else if (isNumeric(item)) {
      const parsed = Number(effective)
      numbers[item.key] = Number.isFinite(parsed) ? parsed : 0
      saved[item.key] = String(item.type === 'integer' ? Math.round(numbers[item.key]) : numbers[item.key])
    }
    else {
      texts[item.key] = effective
      saved[item.key] = effective
    }
  }
  schema.value = { items, groups: payload.groups.filter(group => items.some(item => item.group === group.key)) }
  Object.assign(booleanValues, booleans)
  Object.assign(numberValues, numbers)
  Object.assign(textValues, texts)
  Object.assign(overrides, rawOverrides)
  savedMap.value = saved
  loaded.value = true
  needsReload.value = false
  feedback.value = ''
  return true
}

const synced = computed(() => loaded.value && !needsReload.value && !loading.value && !saving.value)
const canSave = computed(() => !controlsDisabled.value && isDirty.value)
const statusLabel = computed(() => {
  if (saving.value) { return '正在保存或核对运行参数' }
  if (loading.value) { return '正在读取运行参数' }
  if (needsReload.value) { return '运行参数待重新核对' }
  if (!loaded.value) { return '运行参数尚未读取' }
  return isDirty.value ? '运行参数修改待保存' : '运行参数已同步'
})

async function loadSettings(): Promise<boolean> {
  if (!active.value || loading.value || saving.value) { return false }
  const attempt = generation
  loading.value = true
  try {
    if (isDirty.value) {
      try {
        await ElMessageBox.confirm('重新加载会丢弃本页所有未保存的运行参数修改，是否继续？', '重新加载运行参数', { type: 'warning', confirmButtonText: '确认重新加载', cancelButtonText: '取消' })
      }
      catch { return false }
    }
    if (!isCurrent(attempt)) { return false }
    return await readSettings(attempt)
  }
  catch {
    if (isCurrent(attempt)) {
      needsReload.value = true
      feedback.value = '运行参数读取失败，当前编辑已保留。请重新加载核对后再保存。'
      ElMessage.error(feedback.value)
    }
    return false
  }
  finally { if (alive) { loading.value = false } }
}

async function saveSettings(): Promise<boolean> {
  if (!canSave.value) { return false }
  const attempt = generation
  const items = changedItems.value.map(item => ({ ...item }))
  const payload = Object.fromEntries(items.map(item => [item.key, serializeOne(item)]))
  const sensitiveLabels = items.filter(item => item.sensitive).map(item => item.label)
  let applied = false
  saving.value = true
  try {
    if (sensitiveLabels.length > 0) {
      try {
        await ElMessageBox.confirm(
          `本次保存 ${items.length} 项修改，包含 ${sensitiveLabels.length} 项敏感配置：${sensitiveLabels.join('、')}。保存后立即影响所有用户，无需重启服务。`,
          '确认修改敏感配置',
          { type: 'warning', confirmButtonText: '确认保存', cancelButtonText: '取消' },
        )
      }
      catch { return false }
    }
    if (!isCurrent(attempt)) { return false }
    await saveSystemSettings(payload)
    applied = true
    if (!isCurrent(attempt) || !await readSettings(attempt)) { return false }
    ElMessage.success('运行参数已保存并核对最新配置')
    return true
  }
  catch {
    if (isCurrent(attempt)) {
      needsReload.value = true
      feedback.value = applied
        ? '运行参数已保存，但读取最新配置失败。请重新加载核对，不要重复保存。'
        : '未能确认运行参数保存结果，服务器可能已应用变更。请重新加载核对后再操作。'
      ElMessage.error(feedback.value)
    }
    return false
  }
  finally { if (alive) { saving.value = false } }
}

async function clearOverride(item: SettingSchemaItem) {
  if (controlsDisabled.value || !schema.value.items.some(current => current.key === item.key)) { return }
  const attempt = generation
  const key = item.key
  const label = item.label
  const defaultLabel = defaultValueLabel(item)
  let applied = false
  saving.value = true
  try {
    try {
      await ElMessageBox.confirm(
        `将删除「${label}」的数据库覆盖，恢复配置默认值（${defaultLabel}）。重新读取会丢弃本页其他未保存修改。`,
        '恢复配置默认',
        { type: 'warning', confirmButtonText: '确认恢复', cancelButtonText: '取消' },
      )
    }
    catch { return }
    if (!isCurrent(attempt)) { return }
    await deleteSetting(key)
    applied = true
    if (!isCurrent(attempt) || !await readSettings(attempt)) { return }
    ElMessage.success(`${label} 已恢复并核对最新配置`)
  }
  catch {
    if (isCurrent(attempt)) {
      needsReload.value = true
      feedback.value = applied
        ? '覆盖值已清除，但读取最新配置失败。请重新加载核对，不要重复恢复。'
        : '未能确认恢复结果，服务器可能已应用变更。请重新加载核对后再操作。'
      ElMessage.error(feedback.value)
    }
  }
  finally { if (alive) { saving.value = false } }
}

function fillWithConfigDefaults() {
  if (controlsDisabled.value) { return }
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

defineExpose({ saving, isDirty, canSave, statusLabel, synced, saveSettings, loadSettings, fillWithConfigDefaults })

onMounted(loadSettings)
onActivated(() => { active.value = true })
onDeactivated(() => {
  active.value = false
  generation++
  if (loading.value || saving.value) {
    needsReload.value = true
    feedback.value = '页面切换中断了结果核对。已发送的请求仍可能生效，请重新加载服务器参数后再操作。'
  }
})
onBeforeUnmount(() => { alive = false; active.value = false; generation++ })
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

    <el-alert v-if="feedback" :title="feedback" type="warning" :closable="false" show-icon role="status" />
    <header class="panel-actions">
      <div class="actions-copy">
        <strong>{{ statusLabel }}</strong>
        <p>保存后立即影响后续请求，无需重启服务。</p>
      </div>
      <div class="actions-buttons">
        <el-button :disabled="loading || saving || !active" @click="loadSettings">
          <FaIcon name="i-lucide:refresh-cw" class="mrr-icon-interactive" />重新加载
        </el-button>
        <el-button :disabled="controlsDisabled" @click="fillWithConfigDefaults">
          填入配置默认值
        </el-button>
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
              :disabled="controlsDisabled || item.effect === 'RESTART_REQUIRED'"
            />
            <div v-else-if="isNumeric(item)" class="number-control">
              <el-input-number
                v-model="numberValues[item.key]"
                :min="item.min ?? undefined"
                :max="item.max ?? undefined"
                :step="item.type === 'decimal' ? 0.01 : 1"
                :precision="item.type === 'decimal' ? 2 : 0"
                :readonly="controlsDisabled"
                :disabled="item.effect === 'RESTART_REQUIRED'"
                controls-position="right"
              />
              <span v-if="item.unit">{{ item.unit }}</span>
            </div>
            <el-select
              v-else-if="item.type === 'enum'"
              v-model="textValues[item.key]"
              :disabled="controlsDisabled || item.effect === 'RESTART_REQUIRED'"
              class="full-width"
            >
              <el-option v-for="value in item.allowedValues" :key="value" :label="value" :value="value" />
            </el-select>
            <el-input
              v-else
              v-model="textValues[item.key]"
              :maxlength="item.max ?? undefined"
              :disabled="controlsDisabled || item.effect === 'RESTART_REQUIRED'"
            />

            <p class="field-hint">
              {{ item.description }}
            </p>
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
              :disabled="controlsDisabled"
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
